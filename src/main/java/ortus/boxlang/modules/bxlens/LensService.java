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

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ApplicationCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.BifCallCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.BoxLangInfoCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ExceptionCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.FunctionCallCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.HttpCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.MessageCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.QueryCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ScopesCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.SoapCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.TimelineCollector;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.BaseService;
import ortus.boxlang.runtime.types.IStruct;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Module-scoped service for bx-lens. Owns GlobalStats, manages collectors, and exposes JVM utilities.
 * Registered globally via BoxRuntime.putGlobalService() from ModuleConfig.bx.
 */
public class LensService extends BaseService {

	public static final Key		NAME		= Key.of( "bxLensService" );
	public final GlobalStats	stats		= new GlobalStats();

	// Collector registry
	private final Map<String, BaseCollector> collectors = Collections.synchronizedMap( new LinkedHashMap<>() );

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
		deactivateCollectors();
	}

	// -------------------------------------------------------------------------
	// Request lifecycle
	// -------------------------------------------------------------------------

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

	// -------------------------------------------------------------------------
	// Collector management
	// -------------------------------------------------------------------------

	/**
	 * Activate collectors based on module settings. Called from ModuleConfig.bx onLoad().
	 *
	 * @param settings module settings struct
	 */
	public void activateCollectors( IStruct settings ) {
		deactivateCollectors();

		// Always register ApplicationCollector
		registerCollector( new ApplicationCollector(), settings );

		// Register optional collectors based on settings.collectors struct
		IStruct collectorSettings = getCollectorSettings( settings );

		if ( isCollectorEnabled( collectorSettings, "queries" ) ) registerCollector( new QueryCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "http" ) ) registerCollector( new HttpCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "soap" ) ) registerCollector( new SoapCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "messages" ) ) registerCollector( new MessageCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "timeline" ) ) registerCollector( new TimelineCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "exceptions" ) ) registerCollector( new ExceptionCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "bifCalls" ) ) registerCollector( new BifCallCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "functionCalls" ) ) registerCollector( new FunctionCallCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "scopes" ) ) registerCollector( new ScopesCollector(), settings );
		if ( isCollectorEnabled( collectorSettings, "boxlangInfo" ) ) registerCollector( new BoxLangInfoCollector(), settings );
	}

	/**
	 * Deactivate and unregister all collectors.
	 */
	public void deactivateCollectors() {
		for ( BaseCollector c : collectors.values() ) {
			try {
				runtime.getInterceptorService().unregister( c );
			} catch ( Exception ignored ) {}
		}
		collectors.clear();
	}

	private void registerCollector( BaseCollector collector, IStruct settings ) {
		try {
			collector.configure( settings );
			runtime.getInterceptorService().register( collector );
			collectors.put( collector.getName(), collector );
		} catch ( Exception e ) {
			// Log but don't fail
		}
	}

	private IStruct getCollectorSettings( IStruct settings ) {
		try {
			Object raw = settings.getOrDefault( Key.of( "collectors" ), null );
			if ( raw instanceof IStruct ) return ( IStruct ) raw;
		} catch ( Exception ignored ) {}
		return ortus.boxlang.runtime.types.Struct.of();
	}

	private boolean isCollectorEnabled( IStruct collectorSettings, String name ) {
		try {
			Object val = collectorSettings.getOrDefault( Key.of( name ), Boolean.TRUE );
			if ( val instanceof Boolean ) return ( Boolean ) val;
			if ( val instanceof IStruct ) {
				// scopes is a struct with an "enabled" sub-key
				Object enabled = ( ( IStruct ) val ).getOrDefault( Key.of( "enabled" ), Boolean.TRUE );
				return Boolean.TRUE.equals( enabled );
			}
			return Boolean.TRUE.equals( val );
		} catch ( Exception e ) {
			return true;
		}
	}

	/**
	 * Get all registered collectors.
	 */
	public Collection<BaseCollector> getAllCollectors() {
		return Collections.unmodifiableCollection( collectors.values() );
	}

	/**
	 * Check if a collector with the given name is registered.
	 */
	public boolean hasCollector( String name ) {
		return collectors.containsKey( name );
	}

	/**
	 * Get a collector by name.
	 */
	public Optional<BaseCollector> getCollector( String name ) {
		return Optional.ofNullable( collectors.get( name ) );
	}

	/**
	 * Get the count of registered collectors.
	 */
	public int getCollectorCount() {
		return collectors.size();
	}

	// -------------------------------------------------------------------------
	// JVM utilities
	// -------------------------------------------------------------------------

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
	 * Returns the thread dump for a specific thread ID.
	 *
	 * @param threadId JVM thread ID
	 *
	 * @return thread info string or not-found message
	 */
	public String getThreadDump( long threadId ) {
		ThreadMXBean	bean	= ManagementFactory.getThreadMXBean();
		ThreadInfo		info	= bean.getThreadInfo( threadId, Integer.MAX_VALUE );
		return info != null ? info.toString() : "Thread " + threadId + " not found";
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
