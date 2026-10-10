/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bounded, in-memory ring buffer of finished requests. When it is full the oldest request is recycled.
 * Nothing is persisted: a restart or reinit starts with an empty history.
 */
public final class RequestStore {

	/**
	 * One stored request. Everything but the basic summary is built the first time somebody asks for it, then kept:
	 * <ul>
	 * <li>{@link #json()}: the payload of the bar (what happened in the request),</li>
	 * <li>{@link #consoleJson()}: the payload of the console, which also carries the issues,</li>
	 * <li>{@link #summary(boolean)}: the list row, with the issue count and severity when asked for.</li>
	 * </ul>
	 * Issue analysis belongs to the console, so it only runs when the console asks. The entry holds the finished request until every payload that
	 * can still be asked for was built.
	 */
	public static final class Entry {

		private final String						id;
		private volatile Map<String, Object>		summary;
		private final Supplier<Map<String, Object>>	summarySource;
		private volatile Supplier<String>			source;
		private volatile String						json;
		private final Supplier<String>				consoleSource;
		private volatile String						consoleJson;
		private final Supplier<Map<String, Object>>	analyzedSource;
		private volatile Map<String, Object>		analyzed;
		private volatile String						serverId	= "";

		/**
		 * @param json the payload already built
		 */
		public Entry( String id, Map<String, Object> summary, String json ) {
			this( id, summary, null, null, null );
			this.json = json;
		}

		/**
		 * @param source builds the payload on first use
		 */
		public Entry( String id, Map<String, Object> summary, Supplier<String> source ) {
			this( id, summary, source, null, null );
		}

		public Entry( String id, Map<String, Object> summary, Supplier<String> source, Supplier<String> consoleSource,
		    Supplier<Map<String, Object>> analyzedSource ) {
			this.id				= id;
			this.summary		= summary;
			this.summarySource	= null;
			this.source			= source;
			this.consoleSource	= consoleSource;
			this.analyzedSource	= analyzedSource;
		}

		/**
		 * @param summarySource builds the basic list row on first use, so a request nobody lists costs no row
		 */
		public Entry( String id, Supplier<Map<String, Object>> summarySource, Supplier<String> source, Supplier<String> consoleSource,
		    Supplier<Map<String, Object>> analyzedSource ) {
			this.id				= id;
			this.summarySource	= summarySource;
			this.source			= source;
			this.consoleSource	= consoleSource;
			this.analyzedSource	= analyzedSource;
		}

		public String id() {
			return id;
		}

		/**
		 * The server that handled the request, so the store can tell whether one server or several produced its content.
		 */
		public Entry server( String serverId ) {
			this.serverId = serverId == null ? "" : serverId;
			return this;
		}

		/**
		 * The basic list row, without issues.
		 */
		public Map<String, Object> summary() {
			Map<String, Object> s = summary;
			if ( s == null ) {
				synchronized ( this ) {
					s = summary;
					if ( s == null ) {
						s		= summarySource.get();
						summary	= s;
					}
				}
			}
			return s;
		}

		/**
		 * The list row, with the issue count and severity when <code>withIssues</code> is true (this runs the issue analysis once).
		 */
		public Map<String, Object> summary( boolean withIssues ) {
			if ( !withIssues || analyzedSource == null ) {
				return summary();
			}
			Map<String, Object> a = analyzed;
			if ( a == null ) {
				synchronized ( this ) {
					a = analyzed;
					if ( a == null ) {
						a			= analyzedSource.get();
						analyzed	= a;
					}
				}
			}
			return a;
		}

		/**
		 * Has the bar payload been built?
		 */
		public boolean built() {
			return json != null;
		}

		/**
		 * The payload of the bar as JSON, built once.
		 */
		public String json() {
			String j = json;
			if ( j == null ) {
				synchronized ( this ) {
					j = json;
					if ( j == null ) {
						j		= source.get();
						json	= j;
					}
				}
			}
			return j;
		}

		/**
		 * The payload of the console as JSON, built once. It carries the issues.
		 */
		public String consoleJson() {
			if ( consoleSource == null ) {
				return json();
			}
			String j = consoleJson;
			if ( j == null ) {
				synchronized ( this ) {
					j = consoleJson;
					if ( j == null ) {
						j			= consoleSource.get();
						consoleJson	= j;
					}
				}
			}
			return j;
		}
	}

	private volatile int				capacity;
	private final ArrayDeque<Entry>		ring		= new ArrayDeque<>();
	private final Map<String, Entry>	byId		= new HashMap<>();
	/** Every server id ever added, so the console can show a Server column only when records of more than one server are here. Capped. */
	private final java.util.Set<String>	servers		= new java.util.LinkedHashSet<>();

	/** The most server ids remembered. A store that sees more has records of many servers either way. */
	public static final int				MAX_SERVERS	= 64;

	public RequestStore( int capacity ) {
		this.capacity = Math.max( 1, capacity );
	}

	/**
	 * Store a request, recycling the oldest when at capacity.
	 */
	public synchronized void add( Entry entry ) {
		if ( !entry.serverId.isEmpty() && servers.size() < MAX_SERVERS ) {
			servers.add( entry.serverId );
		}
		ring.addLast( entry );
		byId.put( entry.id(), entry );
		while ( ring.size() > capacity ) {
			Entry old = ring.removeFirst();
			byId.remove( old.id() );
		}
	}

	/**
	 * Summaries, newest first.
	 */
	public List<Map<String, Object>> summaries() {
		return summaries( false );
	}

	/**
	 * Summaries, newest first.
	 *
	 * @param withIssues include the issue count and severity (runs the issue analysis for entries that did not have it yet)
	 */
	public List<Map<String, Object>> summaries( boolean withIssues ) {
		List<Entry> copy;
		synchronized ( this ) {
			copy = new ArrayList<>( ring );
		}
		List<Map<String, Object>> out = new ArrayList<>( copy.size() );
		for ( int i = copy.size() - 1; i >= 0; i-- ) {
			out.add( copy.get( i ).summary( withIssues ) );
		}
		return out;
	}

	/**
	 * Find a stored request.
	 *
	 * @return the entry or null
	 */
	public synchronized Entry get( String id ) {
		return byId.get( id );
	}

	/**
	 * How many distinct server ids were added since the store was created or cleared.
	 */
	public synchronized int serverCount() {
		return servers.size();
	}

	public synchronized int size() {
		return ring.size();
	}

	/**
	 * Change how many requests are kept. The oldest are recycled at once when the new limit is lower.
	 */
	public synchronized void setCapacity( int next ) {
		this.capacity = Math.max( 1, next );
		while ( ring.size() > capacity ) {
			Entry old = ring.removeFirst();
			byId.remove( old.id() );
		}
	}

	public int capacity() {
		return capacity;
	}

	public synchronized void clear() {
		ring.clear();
		byId.clear();
		servers.clear();
	}

}
