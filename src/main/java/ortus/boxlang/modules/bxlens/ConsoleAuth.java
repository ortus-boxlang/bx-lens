/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password login for the console. Sessions live in memory only, so a restart signs everyone out. Failed logins are throttled per address.
 */
public final class ConsoleAuth {

	/**
	 * A signed in browser.
	 */
	public static final class Session {

		public final String										id;
		public final String										csrf;
		public final String										remoteAddr;
		/** admin can change things, viewer can only look. */
		public final String										role;
		public final long										createdAt;
		public volatile long									lastSeen;
		/** The newest live stream of this browser wins, older ones stop. */
		public final java.util.concurrent.atomic.AtomicInteger	streamGen	= new java.util.concurrent.atomic.AtomicInteger();

		Session( String id, String csrf, String remoteAddr, String role, long now ) {
			this.role		= role;
			this.id			= id;
			this.csrf		= csrf;
			this.remoteAddr	= remoteAddr;
			this.createdAt	= now;
			this.lastSeen	= now;
		}
	}

	/**
	 * Outcome of a login attempt.
	 *
	 * @param session       the new session on success, else null
	 * @param lockedSeconds seconds until the address may try again, 0 when not locked
	 * @param attemptsLeft  attempts left before a lockout, only meaningful on failure
	 */
	public record LoginResult( Session session, long lockedSeconds, int attemptsLeft ) {

		public boolean ok() {
			return session != null;
		}

		public boolean viewer() {
			return session != null && "viewer".equals( session.role );
		}
	}

	/**
	 * Failed attempts of one client. A wrong password counts against both roles, because it could have been a guess at either. A success
	 * clears only the counter of the role it signed in as, so the viewer password can never be used to reset the admin counter.
	 */
	private static final class Attempts {

		int				adminFailures;
		int				viewerFailures;
		long			adminSince;
		long			viewerSince;
		volatile long	lockedUntil;
		volatile long	touched;
	}

	private static final long								WINDOW_MS		= 10 * 60_000L;
	private static final long								ABSOLUTE_MAX_MS	= 12 * 3_600_000L;
	private static final int								MAX_ATTEMPTS	= 5000;
	private static final int								MAX_SESSIONS	= 200;
	private static final SecureRandom						RANDOM			= new SecureRandom();

	private final Map<String, Session>						sessions		= new ConcurrentHashMap<>();
	private final Map<String, Attempts>						attempts		= new ConcurrentHashMap<>();
	private final byte[]									passwordHash;
	private final byte[]									viewerHash;
	private final long										idleMs;
	private final int										maxAttempts;
	private final long										lockoutMs;
	/** Told the id of every session that ends: logout, expiry, being pushed out. Lensy forgets its conversation then. */
	private volatile java.util.function.Consumer<String>	onEnd			= id -> {
																			};

	public ConsoleAuth( LensConfig config ) {
		String pw = config.consolePassword();
		this.passwordHash = pw.isEmpty() ? null : sha256( pw );
		String vpw = config.viewerPassword();
		this.viewerHash		= vpw.isEmpty() || pw.isEmpty() ? null : sha256( vpw );
		this.idleMs			= config.sessionMinutes * 60_000L;
		this.maxAttempts	= config.maxLoginAttempts;
		this.lockoutMs		= config.lockoutMinutes * 60_000L;
	}

	/**
	 * Is a password configured? Without one nobody can sign in.
	 */
	public boolean isConfigured() {
		return passwordHash != null;
	}

	/**
	 * Try to sign in.
	 */
	public LoginResult login( String password, String remoteAddr ) {
		long	now	= System.currentTimeMillis();
		String	key	= clientKey( remoteAddr );
		// Old sessions and attempts are dropped on every login, and both tables have a hard cap
		prune( now );
		Attempts a = attempts.computeIfAbsent( key, k -> new Attempts() );
		synchronized ( a ) {
			a.touched = now;
			if ( a.lockedUntil > now ) {
				return new LoginResult( null, ( a.lockedUntil - now + 999 ) / 1000, 0 );
			}
			if ( a.adminFailures > 0 && now - a.adminSince > WINDOW_MS ) {
				a.adminFailures = 0;
			}
			if ( a.viewerFailures > 0 && now - a.viewerSince > WINDOW_MS ) {
				a.viewerFailures = 0;
			}
			byte[]	given		= password == null ? null : sha256( password );
			boolean	isAdmin		= passwordHash != null && given != null && MessageDigest.isEqual( passwordHash, given );
			boolean	isViewer	= !isAdmin && viewerHash != null && given != null && MessageDigest.isEqual( viewerHash, given );
			if ( isAdmin || isViewer ) {
				if ( isAdmin ) {
					a.adminFailures = 0;
				} else {
					a.viewerFailures = 0;
				}
				Session s = new Session( token(), token(), key, isAdmin ? "admin" : "viewer", now );
				if ( sessions.size() >= MAX_SESSIONS ) {
					dropOldestSession();
				}
				sessions.put( s.id, s );
				return new LoginResult( s, 0, maxAttempts );
			}
			if ( a.adminFailures == 0 ) {
				a.adminSince = now;
			}
			a.adminFailures++;
			int worst = a.adminFailures;
			if ( viewerHash != null ) {
				if ( a.viewerFailures == 0 ) {
					a.viewerSince = now;
				}
				a.viewerFailures++;
				worst = Math.max( worst, a.viewerFailures );
			}
			if ( worst >= maxAttempts ) {
				a.lockedUntil		= now + lockoutMs;
				a.adminFailures		= 0;
				a.viewerFailures	= 0;
				return new LoginResult( null, lockoutMs / 1000, 0 );
			}
			return new LoginResult( null, 0, maxAttempts - worst );
		}
	}

	/**
	 * The key failures are counted under: the address, and for IPv6 the /64 network, because one client holds a whole /64 and could otherwise
	 * try a new address for every guess.
	 */
	static String clientKey( String remoteAddr ) {
		if ( remoteAddr == null || remoteAddr.isBlank() ) {
			return "?";
		}
		String a = remoteAddr.trim();
		if ( a.indexOf( ':' ) < 0 ) {
			return a;
		}
		try {
			int pct = a.indexOf( '%' );
			if ( pct > 0 ) {
				a = a.substring( 0, pct );
			}
			if ( a.startsWith( "[" ) && a.contains( "]" ) ) {
				a = a.substring( 1, a.indexOf( ']' ) );
			}
			for ( int i = 0; i < a.length(); i++ ) {
				if ( Character.digit( a.charAt( i ), 16 ) < 0 && a.charAt( i ) != ':' && a.charAt( i ) != '.' ) {
					return a;
				}
			}
			byte[] b = java.net.InetAddress.getByName( a ).getAddress();
			if ( b.length != 16 ) {
				return java.net.InetAddress.getByAddress( b ).getHostAddress();
			}
			StringBuilder sb = new StringBuilder( 24 );
			for ( int i = 0; i < 8; i++ ) {
				sb.append( Character.forDigit( b[ i ] >> 4 & 15, 16 ) ).append( Character.forDigit( b[ i ] & 15, 16 ) );
			}
			return sb.append( "/64" ).toString();
		} catch ( Exception e ) {
			return a;
		}
	}

	/**
	 * Find a live session by its cookie value and refresh its idle timer.
	 *
	 * @return the session or null when unknown or expired
	 */
	public Session find( String sessionId ) {
		if ( sessionId == null || sessionId.isEmpty() ) {
			return null;
		}
		Session	s	= sessions.get( sessionId );
		long	now	= System.currentTimeMillis();
		if ( s == null ) {
			return null;
		}
		if ( now - s.lastSeen > idleMs || now - s.createdAt > ABSOLUTE_MAX_MS ) {
			sessions.remove( sessionId );
			ended( sessionId );
			return null;
		}
		s.lastSeen = now;
		return s;
	}

	/**
	 * Is the session still valid? Unlike {@link #find} this does not refresh the idle timer, so a live stream cannot keep a session alive for
	 * ever on its own.
	 */
	public Session peek( String sessionId ) {
		if ( sessionId == null || sessionId.isEmpty() ) {
			return null;
		}
		Session	s	= sessions.get( sessionId );
		long	now	= System.currentTimeMillis();
		if ( s == null ) {
			return null;
		}
		if ( now - s.lastSeen > idleMs || now - s.createdAt > ABSOLUTE_MAX_MS ) {
			sessions.remove( sessionId );
			ended( sessionId );
			return null;
		}
		return s;
	}

	/**
	 * Be told when a session ends, for any reason.
	 */
	public void onEnd( java.util.function.Consumer<String> listener ) {
		this.onEnd = listener == null ? id -> {
		} : listener;
	}

	private void ended( String id ) {
		try {
			onEnd.accept( id );
		} catch ( Throwable t ) {
			// A listener never breaks a login
		}
	}

	public int attemptCount() {
		return attempts.size();
	}

	private void dropOldestSession() {
		sessions.values().stream().min( java.util.Comparator.comparingLong( x -> x.lastSeen ) ).ifPresent( x -> {
			sessions.remove( x.id );
			ended( x.id );
		} );
	}

	public void logout( String sessionId ) {
		if ( sessionId != null ) {
			sessions.remove( sessionId );
			ended( sessionId );
		}
	}

	/**
	 * Constant time check of a CSRF token.
	 */
	public boolean csrfOk( Session s, String token ) {
		return s != null && token != null && MessageDigest.isEqual( s.csrf.getBytes( StandardCharsets.UTF_8 ), token.getBytes( StandardCharsets.UTF_8 ) );
	}

	public int sessionCount() {
		return sessions.size();
	}

	public long idleSeconds() {
		return idleMs / 1000;
	}

	private void prune( long now ) {
		sessions.values().removeIf( s -> {
			boolean old = now - s.lastSeen > idleMs || now - s.createdAt > ABSOLUTE_MAX_MS;
			if ( old ) {
				ended( s.id );
			}
			return old;
		} );
		attempts.entrySet().removeIf( e -> e.getValue().lockedUntil < now && now - e.getValue().touched > WINDOW_MS );
		while ( attempts.size() > MAX_ATTEMPTS ) {
			attempts.entrySet().stream().min( java.util.Comparator.comparingLong( e -> e.getValue().touched ) )
			    .ifPresent( e -> attempts.remove( e.getKey() ) );
		}
	}

	private static String token() {
		byte[] b = new byte[ 32 ];
		RANDOM.nextBytes( b );
		return Base64.getUrlEncoder().withoutPadding().encodeToString( b );
	}

	private static byte[] sha256( String s ) {
		try {
			return MessageDigest.getInstance( "SHA-256" ).digest( s.getBytes( StandardCharsets.UTF_8 ) );
		} catch ( Exception e ) {
			throw new IllegalStateException( e );
		}
	}

}
