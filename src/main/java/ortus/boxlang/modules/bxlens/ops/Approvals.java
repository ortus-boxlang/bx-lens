/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The human in the loop. An ACT tool does not run until a person has clicked Approve for exactly that action. The request is held on the
 * server with an id, the tool and its arguments as the server will run them, and expires after five minutes. Only the browser session that
 * asked can decide it (the router also demands the CSRF token of that session), and it can be decided once.
 * <p>
 * bx-ai has its own approval policies (<code>models/hitl</code>). They suspend a run and resume it later from a checkpoint, which does not
 * fit a chat that streams over one connection and holds its tool call open, so this is a small purpose built step instead: the tool call
 * waits on the decision, then runs or is refused.
 */
public final class Approvals {

	public static final long			TTL_MS		= 5 * 60_000L;
	public static final int				MAX_HELD	= 100;

	private static final SecureRandom	RANDOM		= new SecureRandom();

	public enum State {
		PENDING,
		APPROVED,
		DENIED,
		EXPIRED,
		CANCELLED
	}

	/** What happened to a decision. */
	public enum Decision {
		OK,
		UNKNOWN,
		WRONG_SESSION,
		EXPIRED,
		ALREADY_DECIDED
	}

	/**
	 * One action waiting for a person.
	 */
	public static final class Pending {

		public final String					id;
		public final String					sessionId;
		public final String					tool;
		public final Map<String, Object>	args;
		public final String					summary;
		public final long					createdAt;
		public final long					expiresAt;
		private volatile State				state	= State.PENDING;
		private volatile long				decidedAt;
		private final CountDownLatch		latch	= new CountDownLatch( 1 );

		Pending( String id, String sessionId, String tool, Map<String, Object> args, String summary, long now ) {
			this.id			= id;
			this.sessionId	= sessionId;
			this.tool		= tool;
			this.args		= Collections.unmodifiableMap( new LinkedHashMap<>( args ) );
			this.summary	= summary;
			this.createdAt	= now;
			this.expiresAt	= now + TTL_MS;
		}

		public State state() {
			return this.state;
		}

		/** The request as the page shows it. */
		public Map<String, Object> toMap() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "id", this.id );
			m.put( "tool", this.tool );
			m.put( "args", this.args );
			m.put( "summary", this.summary );
			m.put( "expiresAt", this.expiresAt );
			return m;
		}
	}

	private final Map<String, Pending>	held	= new ConcurrentHashMap<>();
	private final LongSupplier			clock;

	public Approvals() {
		this( System::currentTimeMillis );
	}

	/**
	 * @param clock the time in epoch milliseconds, replaced in tests
	 */
	public Approvals( LongSupplier clock ) {
		this.clock = clock;
	}

	/**
	 * Hold an action until somebody decides.
	 *
	 * @throws IllegalStateException when too many are held
	 */
	public Pending open( String sessionId, String tool, Map<String, Object> args, String summary ) {
		long now = this.clock.getAsLong();
		prune( now );
		if ( this.held.size() >= MAX_HELD ) {
			throw new IllegalStateException( "Too many actions are waiting for approval" );
		}
		byte[] b = new byte[ 18 ];
		RANDOM.nextBytes( b );
		Pending p = new Pending( Base64.getUrlEncoder().withoutPadding().encodeToString( b ), sessionId, tool, args, summary, now );
		this.held.put( p.id, p );
		return p;
	}

	/**
	 * Wait for the decision. Returns when it is decided, when it expires or when the caller stops waiting.
	 *
	 * @param cancelled true when the chat was cancelled, then the action is dropped
	 */
	public State await( Pending p, BooleanSupplier cancelled ) {
		try {
			while ( p.state == State.PENDING ) {
				if ( this.clock.getAsLong() >= p.expiresAt ) {
					settle( p, State.EXPIRED );
					break;
				}
				if ( cancelled.getAsBoolean() || Thread.currentThread().isInterrupted() ) {
					settle( p, State.CANCELLED );
					break;
				}
				p.latch.await( 100, TimeUnit.MILLISECONDS );
			}
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			settle( p, State.CANCELLED );
		}
		return p.state;
	}

	/**
	 * Approve or deny. Only the session that asked may decide, and only once, and not after the five minutes.
	 */
	public Decision decide( String id, String sessionId, boolean approve ) {
		Pending p = id == null ? null : this.held.get( id );
		if ( p == null ) {
			return Decision.UNKNOWN;
		}
		if ( !p.sessionId.equals( sessionId ) ) {
			return Decision.WRONG_SESSION;
		}
		synchronized ( p ) {
			if ( p.state == State.PENDING && this.clock.getAsLong() >= p.expiresAt ) {
				settle( p, State.EXPIRED );
			}
			if ( p.state == State.EXPIRED ) {
				return Decision.EXPIRED;
			}
			if ( p.state != State.PENDING ) {
				return Decision.ALREADY_DECIDED;
			}
			settle( p, approve ? State.APPROVED : State.DENIED );
			return Decision.OK;
		}
	}

	/**
	 * Drop everything a session still has waiting (logout, expiry, reset). The tool calls that wait stop and refuse.
	 */
	public void cancelSession( String sessionId ) {
		for ( Pending p : this.held.values() ) {
			if ( p.sessionId.equals( sessionId ) ) {
				settle( p, State.CANCELLED );
			}
		}
	}

	/** The pending actions of a session. */
	public int pendingCount( String sessionId ) {
		int n = 0;
		for ( Pending p : this.held.values() ) {
			if ( p.state == State.PENDING && ( sessionId == null || p.sessionId.equals( sessionId ) ) ) {
				n++;
			}
		}
		return n;
	}

	public Pending find( String id ) {
		return id == null ? null : this.held.get( id );
	}

	private void settle( Pending p, State s ) {
		synchronized ( p ) {
			if ( p.state == State.PENDING ) {
				p.state		= s;
				p.decidedAt	= this.clock.getAsLong();
				p.latch.countDown();
			}
		}
	}

	private void prune( long now ) {
		this.held.values().removeIf( p -> p.state != State.PENDING && now - p.decidedAt > TTL_MS || now - p.expiresAt > TTL_MS );
		while ( this.held.size() >= MAX_HELD ) {
			Pending oldest = this.held.values().stream().filter( p -> p.state != State.PENDING ).min( Comparator.comparingLong( p -> p.decidedAt ) )
			    .orElse( null );
			if ( oldest == null ) {
				return;
			}
			this.held.remove( oldest.id );
		}
	}

}
