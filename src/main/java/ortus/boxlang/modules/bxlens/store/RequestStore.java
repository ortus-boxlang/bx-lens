/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bounded, in-memory ring buffer of finished requests. When it is full the oldest request is recycled.
 * Nothing is persisted: a restart or reinit starts with an empty history.
 */
public final class RequestStore {

	/**
	 * One stored request: its list summary and its full payload. The payload is JSON that is built the first time somebody asks for it
	 * ({@link #json()}) and then kept, so a request nobody opens costs no serialization. The entry holds the finished request until the
	 * payload is built, then lets it go.
	 */
	public static final class Entry {

		private final String				id;
		private final Map<String, Object>	summary;
		private volatile Supplier<String>	source;
		private volatile String				json;

		/**
		 * @param json the payload already built
		 */
		public Entry( String id, Map<String, Object> summary, String json ) {
			this.id			= id;
			this.summary	= summary;
			this.json		= json;
		}

		/**
		 * @param source builds the payload on first use
		 */
		public Entry( String id, Map<String, Object> summary, Supplier<String> source ) {
			this.id			= id;
			this.summary	= summary;
			this.source		= source;
		}

		public String id() {
			return id;
		}

		public Map<String, Object> summary() {
			return summary;
		}

		/**
		 * Has the payload been built?
		 */
		public boolean built() {
			return json != null;
		}

		/**
		 * The payload as JSON, built once.
		 */
		public String json() {
			String j = json;
			if ( j == null ) {
				synchronized ( this ) {
					j = json;
					if ( j == null ) {
						j		= source.get();
						json	= j;
						source	= null;
					}
				}
			}
			return j;
		}
	}

	private volatile int				capacity;
	private final ArrayDeque<Entry>		ring	= new ArrayDeque<>();
	private final Map<String, Entry>	byId	= new HashMap<>();

	public RequestStore( int capacity ) {
		this.capacity = Math.max( 1, capacity );
	}

	/**
	 * Store a request, recycling the oldest when at capacity.
	 */
	public synchronized void add( Entry entry ) {
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
	public synchronized List<Map<String, Object>> summaries() {
		List<Map<String, Object>>	out	= new ArrayList<>( ring.size() );
		Iterator<Entry>				it	= ring.descendingIterator();
		while ( it.hasNext() ) {
			out.add( it.next().summary() );
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
	}

}
