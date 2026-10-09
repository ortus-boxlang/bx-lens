/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Memory, garbage collection, threads and runtime versions, with the change over the life of the request.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class JvmCollector extends BaseCollector {

	private static final double MB = 1024.0 * 1024.0;

	@Override
	public String id() {
		return "jvm";
	}

	/** Facts that never change while the JVM runs, read once. */
	private static volatile Map<String, Object> constants;

	@Override
	public boolean snapshotOnly() {
		return true;
	}

	private static Map<String, Object> constants() {
		Map<String, Object> c = constants;
		if ( c == null ) {
			c = new LinkedHashMap<>();
			c.put( "cores", Runtime.getRuntime().availableProcessors() );
			c.put( "javaVersion", System.getProperty( "java.version", "" ) );
			c.put( "javaVendor", System.getProperty( "java.vendor", "" ) );
			c.put( "os", System.getProperty( "os.name", "" ) + " " + System.getProperty( "os.arch", "" ) );
			try {
				IStruct v = BoxRuntime.getInstance().getVersionInfo();
				c.put( "boxlangVersion", String.valueOf( v.getOrDefault( Key.of( "version" ), "" ) ) );
			} catch ( Throwable t ) {
				c.put( "boxlangVersion", "" );
			}
			constants = c;
		}
		return c;
	}

	@Override
	public void onRequestStart( LensRequest req ) {
		req.data.put( "jvm.heap0", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() );
		req.data.put( "jvm.gc0", gc() );
	}

	@Override
	public void onRequestFinish( LensRequest req ) {
		MemoryUsage			heap	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		MemoryUsage			nonHeap	= ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
		long				heap0	= req.data.get( "jvm.heap0" ) instanceof Long l ? l : heap.getUsed();
		long[]				gc0		= req.data.get( "jvm.gc0" ) instanceof long[] a ? a : new long[] { 0, 0 };
		long[]				gc1		= gc();
		Map<String, Object>	m		= new LinkedHashMap<>();
		m.put( "heapUsedMb", round( heap.getUsed() / MB ) );
		m.put( "heapMaxMb", heap.getMax() < 0 ? 0 : round( heap.getMax() / MB ) );
		m.put( "heapDeltaMb", round( ( heap.getUsed() - heap0 ) / MB ) );
		m.put( "nonHeapUsedMb", round( nonHeap.getUsed() / MB ) );
		m.put( "gcCount", gc1[ 0 ] - gc0[ 0 ] );
		m.put( "gcTimeMs", gc1[ 1 ] - gc0[ 1 ] );
		m.put( "threads", ManagementFactory.getThreadMXBean().getThreadCount() );
		m.put( "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime() );
		m.putAll( constants() );
		m.put( "requests", service().getStats().snapshot() );
		req.data.put( "jvm", m );
	}

	private static long[] gc() {
		long	count	= 0;
		long	time	= 0;
		for ( GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans() ) {
			count	+= Math.max( 0, b.getCollectionCount() );
			time	+= Math.max( 0, b.getCollectionTime() );
		}
		return new long[] { count, time };
	}

	private static double round( double d ) {
		return Math.round( d * 10.0 ) / 10.0;
	}

}
