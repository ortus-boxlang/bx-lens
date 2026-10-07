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
		m.put( "cores", Runtime.getRuntime().availableProcessors() );
		m.put( "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime() );
		m.put( "javaVersion", System.getProperty( "java.version", "" ) );
		m.put( "javaVendor", System.getProperty( "java.vendor", "" ) );
		m.put( "os", System.getProperty( "os.name", "" ) + " " + System.getProperty( "os.arch", "" ) );
		try {
			IStruct v = BoxRuntime.getInstance().getVersionInfo();
			m.put( "boxlangVersion", String.valueOf( v.getOrDefault( Key.of( "version" ), "" ) ) );
		} catch ( Throwable t ) {
			m.put( "boxlangVersion", "" );
		}
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
