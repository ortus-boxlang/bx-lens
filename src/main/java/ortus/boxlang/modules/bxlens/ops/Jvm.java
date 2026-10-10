/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.OverviewData;
import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Secrets;

/**
 * JVM introspection: threads, locks, pools, memory and the health sweep. It reads the JDK management beans through the same
 * {@code ConsoleData} the console uses, so it sees what the Threads and System pages see. The admin role has been checked before a method
 * here runs, except for {@link #diagnose}, which leaves out what a viewer may not see.
 */
final class Jvm {

	private final LensService	service;
	private final boolean		admin;

	Jvm( LensService service, boolean admin ) {
		this.service	= service;
		this.admin		= admin;
	}

	Object system() {
		Map<String, Object> s = new LinkedHashMap<>( this.service.getData().system() );
		s.remove( "at" );
		return s;
	}

	Object threadsSummary() {
		Map<String, Object>			data	= this.service.getData().threads();
		List<Map<String, Object>>	threads	= Look.maps( data.get( "threads" ) );
		Map<String, Object>			m		= new LinkedHashMap<>();
		m.put( "liveThreads", threads.size() );
		m.put( "byState", data.get( "states" ) );
		int	daemon	= 0;
		int	blocked	= 0;
		int	waiting	= 0;
		for ( Map<String, Object> t : threads ) {
			daemon += Boolean.TRUE.equals( t.get( "daemon" ) ) ? 1 : 0;
			String st = Plain.str( t.get( "state" ) );
			blocked	+= "BLOCKED".equals( st ) ? 1 : 0;
			waiting	+= "WAITING".equals( st ) || "TIMED_WAITING".equals( st ) ? 1 : 0;
		}
		var tb = ManagementFactory.getThreadMXBean();
		m.put( "daemon", daemon );
		m.put( "peak", tb.getPeakThreadCount() );
		m.put( "startedSinceBoot", tb.getTotalStartedThreadCount() );
		m.put( "blockedOnMonitor", blocked );
		m.put( "waitingOrSleeping", waiting );
		m.put( "deadlockedThreads", Plain.list( data.get( "deadlocked" ) ).size() );
		m.put( "largestPools", pools( threads, 5 ) );
		m.put( "findings", Diagnose.threads( data ).stream().map( Diagnose.Finding::toMap ).toList() );
		return m;
	}

	Object blockedThreads() {
		Map<String, Object>			data	= this.service.getData().threads();
		List<Object>				dead	= Plain.list( data.get( "deadlocked" ) );
		List<Map<String, Object>>	blocked	= new ArrayList<>();
		List<Map<String, Object>>	cycle	= new ArrayList<>();
		for ( Map<String, Object> t : Look.maps( data.get( "threads" ) ) ) {
			boolean	isDead	= dead.contains( t.get( "id" ) );
			String	state	= Plain.str( t.get( "state" ) );
			boolean	waits	= !Plain.str( t.get( "lock" ) ).isEmpty();
			if ( !isDead && ! ( "BLOCKED".equals( state )
			    || waits && ( "WAITING".equals( state ) || "TIMED_WAITING".equals( state ) ) && !Plain.str( t.get( "lockOwner" ) ).isEmpty() ) ) {
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "thread", t.get( "name" ) );
			m.put( "pool", t.get( "pool" ) );
			m.put( "state", state );
			m.put( "waitsForLock", t.get( "lock" ) );
			m.put( "lockHeldBy", t.get( "lockOwner" ) );
			m.put( "blockedMs", t.get( "blockedMs" ) );
			m.put( "locksItHolds", Look.first( t.get( "locked" ), 5 ) );
			m.put( "topFrames", frames( t, 6 ) );
			( isDead ? cycle : blocked ).add( m );
		}
		blocked.sort( Comparator.comparingLong( ( Map<String, Object> m ) -> Plain.num( m.get( "blockedMs" ), 0 ) ).reversed() );
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "deadlock", !cycle.isEmpty() );
		out.put( "deadlockedThreads", cycle );
		out.put( "blockedOrWaitingOnALock", blocked.size() > 30 ? blocked.subList( 0, 30 ) : blocked );
		out.put( "findings", Diagnose.threads( data ).stream().map( Diagnose.Finding::toMap ).toList() );
		return out;
	}

	Object topCpuThreads( int limit ) {
		Map<String, Object>			data	= this.service.getData().threads();
		List<Map<String, Object>>	threads	= new ArrayList<>( Look.maps( data.get( "threads" ) ) );
		threads.removeIf( t -> Boolean.TRUE.equals( t.get( "self" ) ) );
		threads.sort( Comparator.comparingLong( ( Map<String, Object> t ) -> Plain.num( t.get( "cpuMs" ), 0 ) ).reversed() );
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map<String, Object> t : threads ) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "thread", t.get( "name" ) );
			m.put( "pool", t.get( "pool" ) );
			m.put( "state", t.get( "state" ) );
			m.put( "cpuMsSinceStart", t.get( "cpuMs" ) );
			m.put( "topFrames", frames( t, 4 ) );
			out.add( m );
			if ( out.size() >= limit ) {
				break;
			}
		}
		return Map.of( "threads", out, "note",
		    "CPU time is the total since the thread started, not a rate. A thread that is high now and was low a minute ago is the one burning CPU: call this twice." );
	}

	Object threadPools() {
		Map<String, Object>			data	= this.service.getData().threads();
		List<Map<String, Object>>	pools	= pools( Look.maps( data.get( "threads" ) ), 40 );
		return Map.of( "pools", pools, "liveThreads", Look.maps( data.get( "threads" ) ).size(), "note",
		    "A pool is the thread name without its trailing number." );
	}

	Object threadStack( String name ) {
		Map<String, Object>			data	= this.service.getData().threads();
		List<Map<String, Object>>	threads	= Look.maps( data.get( "threads" ) );
		Map<String, Object>			found	= null;
		for ( Map<String, Object> t : threads ) {
			if ( Plain.str( t.get( "name" ) ).equalsIgnoreCase( name ) ) {
				found = t;
				break;
			}
		}
		if ( found == null ) {
			String low = name.toLowerCase( Locale.ROOT );
			for ( Map<String, Object> t : threads ) {
				if ( Plain.str( t.get( "name" ) ).toLowerCase( Locale.ROOT ).contains( low ) ) {
					found = t;
					break;
				}
			}
		}
		if ( found == null ) {
			throw new IllegalArgumentException( "No thread has that name. Use threadPools or blockedThreads to see thread names." );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "thread", found.get( "name" ) );
		m.put( "state", found.get( "state" ) );
		m.put( "waitsForLock", found.get( "lock" ) );
		m.put( "lockHeldBy", found.get( "lockOwner" ) );
		m.put( "locksItHolds", Look.first( found.get( "locked" ), 8 ) );
		m.put( "cpuMsSinceStart", found.get( "cpuMs" ) );
		m.put( "stack", frames( found, 40 ) );
		return m;
	}

	Map<String, Object> gcPressure() {
		Map<String, Object>	m		= new LinkedHashMap<>();
		var					heap	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		long				max		= heap.getMax() > 0 ? heap.getMax() : heap.getCommitted();
		long				afterGc	= 0;
		boolean				known	= false;
		for ( MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans() ) {
			if ( p.getType() == MemoryType.HEAP ) {
				MemoryUsage cu = p.getCollectionUsage();
				if ( cu != null ) {
					afterGc	+= Math.max( 0, cu.getUsed() );
					known	|= cu.getUsed() > 0;
				}
			}
		}
		long						uptime		= Math.max( 1, ManagementFactory.getRuntimeMXBean().getUptime() );
		long						gcMs		= 0;
		long						gcCount		= 0;
		List<Map<String, Object>>	collectors	= new ArrayList<>();
		for ( GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans() ) {
			gcMs	+= Math.max( 0, g.getCollectionTime() );
			gcCount	+= Math.max( 0, g.getCollectionCount() );
			Map<String, Object> c = new LinkedHashMap<>();
			c.put( "name", g.getName() );
			c.put( "collections", g.getCollectionCount() );
			c.put( "timeMs", g.getCollectionTime() );
			collectors.add( c );
		}
		double	afterPct	= known && max > 0 ? afterGc * 100.0 / max : -1;
		double	sharePct	= gcMs * 100.0 / uptime;
		double	usedPct		= max > 0 ? heap.getUsed() * 100.0 / max : 0;
		m.put( "heapUsedMb", heap.getUsed() / 1_048_576L );
		m.put( "heapMaxMb", max / 1_048_576L );
		m.put( "heapUsedPercent", Math.round( usedPct * 10.0 ) / 10.0 );
		m.put( "heapAfterLastGcPercent", afterPct < 0 ? null : Math.round( afterPct * 10.0 ) / 10.0 );
		m.put( "gcCollections", gcCount );
		m.put( "gcTimeMs", gcMs );
		m.put( "gcTimeSharePercent", Math.round( sharePct * 100.0 ) / 100.0 );
		m.put( "collectors", collectors );
		m.put( "findings", Diagnose.memory( afterPct, sharePct, usedPct, max / 1_048_576L ).stream().map( Diagnose.Finding::toMap ).toList() );
		return m;
	}

	/**
	 * The health sweep: threads, executors, heap and garbage collection, datasources, error rate, slow requests. A viewer gets the parts the
	 * console shows a viewer (not threads and not memory) and is told what was left out.
	 */
	Object diagnose( boolean admin ) {
		this.service.sync();
		List<Diagnose.Finding>	findings	= new ArrayList<>();
		List<String>			checked		= new ArrayList<>();
		List<String>			skipped		= new ArrayList<>();
		if ( admin ) {
			findings.addAll( Diagnose.threads( this.service.getData().threads() ) );
			checked.add( "threads" );
			Map<String, Object>	gc			= gcPressure();
			Object				afterPct	= gc.get( "heapAfterLastGcPercent" );
			findings.addAll( Diagnose.memory( afterPct instanceof Number n ? n.doubleValue() : -1, ( ( Number ) gc.get( "gcTimeSharePercent" ) ).doubleValue(),
			    ( ( Number ) gc.get( "heapUsedPercent" ) ).doubleValue(), ( ( Number ) gc.get( "heapMaxMb" ) ).longValue() ) );
			checked.add( "heap and garbage collection" );
		} else {
			skipped.add( "threads, heap and garbage collection (admin role)" );
		}
		findings.addAll( Diagnose.executors( Look.maps( this.service.getData().executors().get( "executors" ) ) ) );
		checked.add( "executors" );
		findings.addAll( Diagnose.datasources( Look.maps( this.service.getDatasources().list().get( "datasources" ) ) ) );
		checked.add( "datasources" );
		Map<String, Object> ov = OverviewData.build( this.service );
		findings.addAll( Diagnose.requests( ( int ) Plain.num( ov.get( "requests" ), 0 ), ov.get( "errorRate" ) instanceof Number n ? n.doubleValue() : 0,
		    ov.get( "p95Ms" ) instanceof Number n ? n.doubleValue() : 0, this.service.getConfig().slowRequestMs, this.service.inflight() ) );
		checked.add( "error rate, slow requests and running requests" );
		return Diagnose.report( findings, checked, skipped );
	}

	// ---------------------------------------------------------------------------------------------

	private static List<Map<String, Object>> pools( List<Map<String, Object>> threads, int limit ) {
		Map<String, Map<String, Integer>> byPool = new TreeMap<>();
		for ( Map<String, Object> t : threads ) {
			String pool = Plain.str( t.get( "pool" ) );
			byPool.computeIfAbsent( pool, k -> new TreeMap<>() ).merge( Plain.str( t.get( "state" ) ), 1, Integer::sum );
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map.Entry<String, Map<String, Integer>> e : byPool.entrySet() ) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "pool", e.getKey() );
			m.put( "threads", e.getValue().values().stream().mapToInt( Integer::intValue ).sum() );
			m.put( "byState", e.getValue() );
			out.add( m );
		}
		out.sort( Comparator.comparingInt( ( Map<String, Object> m ) -> ( Integer ) m.get( "threads" ) ).reversed() );
		return out.size() > limit ? new ArrayList<>( out.subList( 0, limit ) ) : out;
	}

	private static List<String> frames( Map<String, Object> thread, int n ) {
		List<String> out = new ArrayList<>();
		for ( Object f : Plain.list( thread.get( "frames" ) ) ) {
			if ( out.size() >= n ) {
				break;
			}
			out.add( Secrets.text( Plain.str( Plain.map( f ).get( "text" ) ) ) );
		}
		return out;
	}

}
