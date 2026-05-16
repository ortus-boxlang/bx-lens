/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application-lifetime aggregate statistics. Owned by LensService.
 * All fields are public atomics so interceptors can increment without going through a method call.
 */
public class GlobalStats {

	private final long			startedAt			= System.currentTimeMillis();

	public final AtomicLong		totalRequests		= new AtomicLong( 0 );
	public final AtomicLong		totalQueries		= new AtomicLong( 0 );
	public final AtomicLong		totalExceptions		= new AtomicLong( 0 );
	public final AtomicLong		totalHttpCalls		= new AtomicLong( 0 );
	public final AtomicLong		totalSoapCalls		= new AtomicLong( 0 );
	public final AtomicLong		totalMessages		= new AtomicLong( 0 );
	// Tracks live session count via onSessionStart / onSessionEnd
	public final AtomicLong		activeSessions		= new AtomicLong( 0 );

	// Request timing accumulators
	private final AtomicLong	totalRequestTime	= new AtomicLong( 0 );
	private final AtomicLong	slowestRequest		= new AtomicLong( 0 );
	private final AtomicLong	fastestRequest		= new AtomicLong( Long.MAX_VALUE );

	/**
	 * Record the completion of one request with its measured wall-clock duration.
	 *
	 * @param durationMs duration in milliseconds
	 */
	public void recordRequest( long durationMs ) {
		totalRequests.incrementAndGet();
		totalRequestTime.addAndGet( durationMs );
		slowestRequest.updateAndGet( prev -> Math.max( prev, durationMs ) );
		fastestRequest.updateAndGet( prev -> Math.min( prev, durationMs ) );
	}

	/**
	 * Returns a point-in-time snapshot of all stats as a plain Map
	 * suitable for JSON serialization.
	 *
	 * @return Map of stat keys to Long values
	 */
	public Map<String, Object> snapshot() {
		long				reqs	= totalRequests.get();
		long				fastest	= fastestRequest.get();
		Map<String, Object>	m		= new HashMap<>();
		m.put( "totalRequests", reqs );
		m.put( "totalQueries", totalQueries.get() );
		m.put( "totalExceptions", totalExceptions.get() );
		m.put( "totalHttpCalls", totalHttpCalls.get() );
		m.put( "totalSoapCalls", totalSoapCalls.get() );
		m.put( "activeSessions", activeSessions.get() );
		m.put( "uptimeMs", System.currentTimeMillis() - startedAt );
		m.put( "avgRequestTimeMs", reqs > 0 ? totalRequestTime.get() / reqs : 0L );
		m.put( "slowestRequestMs", slowestRequest.get() );
		m.put( "fastestRequestMs", fastest == Long.MAX_VALUE ? 0L : fastest );
		return m;
	}

	/**
	 * Reset all counters. Does NOT reset activeSessions since that reflects live state.
	 */
	public void reset() {
		totalRequests.set( 0 );
		totalQueries.set( 0 );
		totalExceptions.set( 0 );
		totalHttpCalls.set( 0 );
		totalSoapCalls.set( 0 );
		totalMessages.set( 0 );
		totalRequestTime.set( 0 );
		slowestRequest.set( 0 );
		fastestRequest.set( Long.MAX_VALUE );
	}

}
