/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one request cost its thread: CPU time and bytes allocated. Both come from the JDK thread beans, so they are cheap to read at the start
 * and the end of a request. Work done on other threads the request started is not included.
 */
public final class Cost {

	/**
	 * Readings taken when the request started.
	 */
	public record Start( long threadId, long cpuNs, long allocBytes ) {
	}

	private static final ThreadMXBean BEAN = ManagementFactory.getThreadMXBean();

	private Cost() {
	}

	/**
	 * Read the current thread now.
	 */
	public static Start begin() {
		long id = Thread.currentThread().threadId();
		return new Start( id, cpu( id ), alloc( id ) );
	}

	/**
	 * What the thread used since {@link #begin()}.
	 *
	 * @return a map with cpuMs and allocBytes, or null when the JVM cannot measure
	 */
	public static Map<String, Object> since( Start s ) {
		if ( s == null ) {
			return null;
		}
		long	cpu		= cpu( s.threadId() );
		long	alloc	= alloc( s.threadId() );
		if ( cpu < 0 && alloc < 0 ) {
			return null;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "cpuMs", cpu < 0 || s.cpuNs() < 0 ? null : Math.round( ( cpu - s.cpuNs() ) / 10_000.0 ) / 100.0 );
		m.put( "allocBytes", alloc < 0 || s.allocBytes() < 0 ? null : Math.max( 0, alloc - s.allocBytes() ) );
		return m;
	}

	private static long cpu( long threadId ) {
		try {
			return BEAN.isThreadCpuTimeSupported() && BEAN.isThreadCpuTimeEnabled() ? BEAN.getThreadCpuTime( threadId ) : -1;
		} catch ( Throwable t ) {
			return -1;
		}
	}

	private static long alloc( long threadId ) {
		try {
			if ( BEAN instanceof com.sun.management.ThreadMXBean x && x.isThreadAllocatedMemorySupported() && x.isThreadAllocatedMemoryEnabled() ) {
				return x.getThreadAllocatedBytes( threadId );
			}
		} catch ( Throwable t ) {
			// Not measurable
		}
		return -1;
	}

}
