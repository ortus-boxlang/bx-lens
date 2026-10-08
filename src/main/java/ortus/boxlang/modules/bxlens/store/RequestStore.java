/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.bxlens.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Bounded, in-memory ring buffer of finished requests. When it is full the oldest request is recycled.
 * Nothing is persisted: a restart or reinit starts with an empty history.
 */
public final class RequestStore {

	/**
	 * One stored request: its list summary and the full payload already serialized to JSON.
	 */
	public record Entry( String id, Map<String, Object> summary, String json ) {
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
