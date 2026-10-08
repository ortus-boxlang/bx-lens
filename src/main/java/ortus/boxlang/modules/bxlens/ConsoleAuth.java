/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
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

		public final String		id;
		public final String		csrf;
		public final String		remoteAddr;
		public final long		createdAt;
		public volatile long	lastSeen;

		Session( String id, String csrf, String remoteAddr, long now ) {
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
	}

	private static final class Attempts {

		int				failures;
		long			firstAt;
		volatile long	lockedUntil;
	}

	private static final long			WINDOW_MS		= 10 * 60_000L;
	private static final long			ABSOLUTE_MAX_MS	= 12 * 3_600_000L;
	private static final SecureRandom	RANDOM			= new SecureRandom();

	private final Map<String, Session>	sessions		= new ConcurrentHashMap<>();
	private final Map<String, Attempts>	attempts		= new ConcurrentHashMap<>();
	private final byte[]				passwordHash;
	private final long					idleMs;
	private final int					maxAttempts;
	private final long					lockoutMs;

	public ConsoleAuth( LensConfig config ) {
		String pw = config.consolePassword();
		this.passwordHash	= pw.isEmpty() ? null : sha256( pw );
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
		long		now	= System.currentTimeMillis();
		String		key	= remoteAddr == null ? "?" : remoteAddr;
		Attempts	a	= attempts.computeIfAbsent( key, k -> new Attempts() );
		synchronized ( a ) {
			if ( a.lockedUntil > now ) {
				return new LoginResult( null, ( a.lockedUntil - now + 999 ) / 1000, 0 );
			}
			if ( a.failures > 0 && now - a.firstAt > WINDOW_MS ) {
				a.failures = 0;
			}
			boolean ok = passwordHash != null && password != null && MessageDigest.isEqual( passwordHash, sha256( password ) );
			if ( ok ) {
				attempts.remove( key );
				Session s = new Session( token(), token(), key, now );
				sessions.put( s.id, s );
				prune( now );
				return new LoginResult( s, 0, maxAttempts );
			}
			if ( a.failures == 0 ) {
				a.firstAt = now;
			}
			a.failures++;
			if ( a.failures >= maxAttempts ) {
				a.lockedUntil	= now + lockoutMs;
				a.failures		= 0;
				return new LoginResult( null, lockoutMs / 1000, 0 );
			}
			return new LoginResult( null, 0, maxAttempts - a.failures );
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
			return null;
		}
		s.lastSeen = now;
		return s;
	}

	public void logout( String sessionId ) {
		if ( sessionId != null ) {
			sessions.remove( sessionId );
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
		Iterator<Session> it = sessions.values().iterator();
		while ( it.hasNext() ) {
			Session s = it.next();
			if ( now - s.lastSeen > idleMs || now - s.createdAt > ABSOLUTE_MAX_MS ) {
				it.remove();
			}
		}
		if ( attempts.size() > 5000 ) {
			attempts.entrySet().removeIf( e -> e.getValue().lockedUntil < now && now - e.getValue().firstAt > WINDOW_MS );
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
