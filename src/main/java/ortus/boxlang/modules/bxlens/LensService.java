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
package ortus.boxlang.modules.bxlens;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.BaseService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Module-scoped service for bx-lens. Owns GlobalStats and exposes JVM utilities.
 * Registered in ModuleConfig.bx onLoad() and stored in moduleRecord.settings.lensService.
 */
public class LensService extends BaseService {

	public static final Key		NAME	= Key.of( "bxLensService" );
	public final GlobalStats	stats	= new GlobalStats();

	public LensService( BoxRuntime runtime ) {
		super( runtime, NAME );
	}

	@Override
	public void onConfigurationLoad() {
	}

	@Override
	public void onStartup() {
	}

	@Override
	public void onShutdown( Boolean force ) {
	}

	/**
	 * Generate a new UUID request identifier.
	 *
	 * @return UUID string
	 */
	public String startRequest() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Record that a request completed with the given wall-clock duration.
	 *
	 * @param durationMs milliseconds the request took
	 */
	public void endRequest( long durationMs ) {
		stats.recordRequest( durationMs );
	}

	/**
	 * @return the shared GlobalStats instance
	 */
	public GlobalStats getStats() {
		return stats;
	}

	/**
	 * Returns a formatted thread dump for all live JVM threads.
	 *
	 * @return multi-line string
	 */
	public String getThreadDump() {
		ThreadMXBean	bean	= ManagementFactory.getThreadMXBean();
		ThreadInfo[]	threads	= bean.dumpAllThreads( true, true );
		StringBuilder	sb		= new StringBuilder();
		for ( ThreadInfo t : threads ) {
			sb.append( t.toString() );
		}
		return sb.toString();
	}

	/**
	 * Returns current JVM heap and non-heap memory usage.
	 *
	 * @return Map with keys heapUsed, heapCommitted, heapMax, nonHeapUsed (all bytes as Long)
	 */
	public Map<String, Long> getMemoryInfo() {
		MemoryMXBean		m		= ManagementFactory.getMemoryMXBean();
		MemoryUsage			h		= m.getHeapMemoryUsage();
		MemoryUsage			nh		= m.getNonHeapMemoryUsage();
		Map<String, Long>	result	= new HashMap<>();
		result.put( "heapUsed", h.getUsed() );
		result.put( "heapCommitted", h.getCommitted() );
		result.put( "heapMax", h.getMax() );
		result.put( "nonHeapUsed", nh.getUsed() );
		return result;
	}

	/**
	 * Dumps the JVM heap to a .hprof file. Requires a HotSpot JVM.
	 *
	 * @param outputPath absolute path for the .hprof file
	 * 
	 * @return outputPath on success
	 * 
	 * @throws Exception if the JVM does not support HotSpotDiagnosticMXBean or write fails
	 */
	public String dumpHeap( String outputPath ) throws Exception {
		com.sun.management.HotSpotDiagnosticMXBean bean = ManagementFactory
		    .getPlatformMXBean( com.sun.management.HotSpotDiagnosticMXBean.class );
		if ( bean == null ) {
			throw new UnsupportedOperationException( "HotSpotDiagnosticMXBean not available on this JVM" );
		}
		bean.dumpHeap( outputPath, true );
		return outputPath;
	}

}
