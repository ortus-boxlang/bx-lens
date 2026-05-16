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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.util.GlobalStats;
import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.services.BaseService;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Module-scoped service for bx-lens. Owns GlobalStats, manages collectors, and exposes JVM utilities.
 * Registered globally via BoxRuntime.putGlobalService() from ModuleConfig.bx.
 */
public class LensService extends BaseService {

	/**
	 * GlobalStats instance shared across all collectors and requests. Collectors record metrics here, and it can be exposed via tags or APIs.
	 * This is to record stats about the global BoxLang environment and overall request metrics, not per-request details (which should go in the request
	 * context).
	 */
	private final GlobalStats			stats		= new GlobalStats();

	/**
	 * Map of active collectors, keyed by collector name. Built during startup and then immutable.
	 */
	private Map<String, BaseCollector>	collectors	= new LinkedHashMap<>();

	/**
	 * The main logger (volatile for thread-safe lazy initialization)
	 */
	private volatile BoxLangLogger		logger;

	/**
	 * --------------------------------------------------------------------------
	 * Constructors
	 * --------------------------------------------------------------------------
	 */

	/**
	 * public no-arg constructor for the ServiceProvider
	 */
	public LensService() {
		this( BoxRuntime.getInstance() );
	}

	/**
	 * Constructor
	 *
	 * @param runtime The BoxRuntime
	 */
	public LensService( BoxRuntime runtime ) {
		super( runtime, KeyDictionary.bxLensService );
		getLogger().trace( "+ bxLens Service built" );
	}

	/**
	 * --------------------------------------------------------------------------
	 * Runtime Service Event Methods
	 * --------------------------------------------------------------------------
	 */

	@Override
	public void onConfigurationLoad() {
		// Not used by the service, since those are only for core services
	}

	@Override
	public void onShutdown( Boolean force ) {
		getLogger().debug( "+ bxLens Service shutdown requested" );
		shutdownCollectors();
	}

	@Override
	public void onStartup() {
		getLogger().debug( "+ bxLens Service started" );
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
	}

	/**
	 * Deactivate and unregister all collectors.
	 */
	public void shutdownCollectors() {
		for ( BaseCollector c : collectors.values() ) {
			try {
				c.shutdown();
				runtime.getInterceptorService().unregister( c );
			} catch ( Exception ignored ) {
			}
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

	/**
	 * Get all registered collectors.
	 */
	public Collection<BaseCollector> getAllCollectors() {
		return Collections.unmodifiableCollection( this.collectors.values() );
	}

	/**
	 * Check if a collector with the given name is registered.
	 *
	 * @param name collector name (e.g. "queries", "http", "scopes")
	 */
	public boolean hasCollector( String name ) {
		return this.collectors.containsKey( name );
	}

	/**
	 * Get a collector by name.
	 *
	 * @param name collector name (e.g. "queries", "http", "scopes")
	 *
	 * @return the collector, or null if not found
	 */
	public BaseCollector getCollector( String name ) {
		return this.collectors.get( name );
	}

	/**
	 * Get the count of registered collectors.
	 */
	public int getCollectorCount() {
		return this.collectors.size();
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
	 * FYI: We return always native types in order to avoid any performance issues.
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
	 * @return outputPath on success
	 *
	 * @throws Exception if the JVM does not support HotSpotDiagnosticMXBean or write fails
	 */
	public String dumpHeap() throws Exception {
		return dumpHeap( null );
	}

	/**
	 * Dumps the JVM heap to a .hprof file. Requires a HotSpot JVM.
	 *
	 * @param outputPath absolute path for the .hprof file, or null to use a temporary file
	 *
	 * @return outputPath on success
	 *
	 * @throws Exception if the JVM does not support HotSpotDiagnosticMXBean or write fails
	 */
	public String dumpHeap( String outputPath ) throws Exception {
		// If no output path provided, create a temp file
		if ( outputPath == null || outputPath.isEmpty() ) {
			Path tempFile = Files.createTempFile( "heapdump", ".hprof" );
			outputPath = tempFile.toAbsolutePath().toString();
		}

		// Use HotSpotDiagnosticMXBean to dump the heap
		com.sun.management.HotSpotDiagnosticMXBean bean = ManagementFactory
		    .getPlatformMXBean( com.sun.management.HotSpotDiagnosticMXBean.class );
		if ( bean == null ) {
			throw new UnsupportedOperationException( "HotSpotDiagnosticMXBean not available on this JVM" );
		}
		bean.dumpHeap( outputPath, true );
		return outputPath;
	}

	/**
	 * --------------------------------------------------------------------------
	 * Helper methods
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Get the shared GlobalStats instance for recording and reporting metrics.
	 *
	 * @return GlobalStats instance
	 */
	public GlobalStats getGlobalStats() {
		return this.stats;
	}

	/**
	 * Get the bxLens logger that logs to the "bxLens" category.
	 */
	public BoxLangLogger getLogger() {
		if ( this.logger == null ) {
			synchronized ( LensService.class ) {
				if ( this.logger == null ) {
					this.logger = runtime.getLoggingService().getLogger( "bxLens" );
				}
			}
		}
		return this.logger;
	}

}
