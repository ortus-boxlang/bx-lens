/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.util.Plain;

/**
 * The findings of the health sweep. Every method here is a pure function of plain data (the maps the console already builds), so a test can
 * feed it crafted numbers: a deadlock, a full thread pool, a garbage collector that never rests. Each finding has a severity, the evidence
 * that led to it and a concrete next step.
 */
public final class Diagnose {

	public static final String	CRITICAL	= "critical";
	public static final String	WARNING		= "warning";
	public static final String	INFO		= "info";

	/**
	 * One thing the sweep found.
	 *
	 * @param severity critical, warning or info
	 * @param area     threads, executors, memory, datasources, errors or requests
	 * @param title    what is wrong in a few words
	 * @param evidence the numbers that show it
	 * @param next     the next step to take
	 */
	public record Finding( String severity, String area, String title, String evidence, String next ) {

		public Map<String, Object> toMap() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "severity", this.severity );
			m.put( "area", this.area );
			m.put( "title", this.title );
			m.put( "evidence", this.evidence );
			m.put( "next", this.next );
			return m;
		}
	}

	private Diagnose() {
	}

	/**
	 * Findings about threads. The input is the result of <code>ConsoleData.threads()</code>: a list <code>threads</code>, the map
	 * <code>states</code> and the ids in <code>deadlocked</code>.
	 */
	public static List<Finding> threads( Map<String, Object> data ) {
		List<Finding>				out			= new ArrayList<>();
		List<Map<String, Object>>	threads		= maps( data.get( "threads" ) );
		List<Object>				dead		= Plain.list( data.get( "deadlocked" ) );
		int							live		= threads.size();
		int							blocked		= 0;
		List<String>				blockedList	= new ArrayList<>();
		List<String>				deadNames	= new ArrayList<>();
		for ( Map<String, Object> t : threads ) {
			String state = Plain.str( t.get( "state" ) );
			if ( "BLOCKED".equals( state ) ) {
				blocked++;
				blockedList
				    .add( Plain.str( t.get( "name" ) ) + " waits for " + Plain.str( t.get( "lock" ) ) + ( Plain.str( t.get( "lockOwner" ) ).isEmpty() ? ""
				        : " held by " + Plain.str( t.get( "lockOwner" ) ) ) );
			}
			if ( dead.contains( t.get( "id" ) ) ) {
				deadNames.add( Plain.str( t.get( "name" ) ) + " waits for " + Plain.str( t.get( "lock" ) ) + " held by " + Plain.str( t.get( "lockOwner" ) ) );
			}
		}
		if ( !dead.isEmpty() ) {
			out.add( new Finding( CRITICAL, "threads", dead.size() + " threads are deadlocked", String.join( "; ", cut( deadNames, 6 ) ),
			    "A deadlock never clears by itself. Use blockedThreads and threadStack on these threads to see the two code paths that take the locks in opposite order, "
			        + "then change one of them to take the locks in the same order. The threads stay stuck until the application is restarted." ) );
		}
		if ( blocked > 0 && dead.isEmpty() ) {
			boolean heavy = live > 0 && blocked * 100 / live >= 20 || blocked >= 20;
			out.add( new Finding( heavy ? CRITICAL : blocked >= 3 ? WARNING : INFO, "threads", blocked + " of " + live + " threads are BLOCKED on a monitor",
			    String.join( "; ", cut( blockedList, 5 ) ),
			    "Many threads waiting for one lock is contention. Look at the stack of the thread that holds it (threadStack), shorten what runs inside the synchronized "
			        + "block or replace it with a concurrent structure." ) );
		}
		int			waiting	= 0;
		Map<?, ?>	states	= data.get( "states" ) instanceof Map<?, ?> m ? m : Map.of();
		Object		w		= states.get( "WAITING" );
		waiting = w instanceof Number n ? n.intValue() : 0;
		if ( live >= 500 ) {
			out.add( new Finding( live >= 2000 ? CRITICAL : WARNING, "threads", live + " live threads", "BLOCKED " + blocked + ", WAITING " + waiting,
			    "A thread count this high usually means an unbounded pool or one thread per task. Use threadPools to see which pool name grows and cap it." ) );
		}
		return out;
	}

	/**
	 * Findings about executors. The input is the list <code>executors</code> of <code>ConsoleData.executors()</code>. The runtime's own health
	 * report is part of the evidence; the recommendations are numbers worked out from the pool, not general advice.
	 */
	public static List<Finding> executors( List<Map<String, Object>> executors ) {
		List<Finding> out = new ArrayList<>();
		for ( Map<String, Object> e : executors ) {
			String				name		= Plain.str( e.get( "name" ) );
			double				pool		= d( e.get( "poolUtilization" ) );
			double				threads		= d( e.get( "threadsUtilization" ) );
			double				queue		= d( e.get( "queueUtilization" ) );
			long				max			= l( e.get( "maximumPoolSize" ) );
			long				core		= l( e.get( "corePoolSize" ) );
			long				qsize		= l( e.get( "queueSize" ) );
			long				qcap		= l( e.get( "queueCapacity" ) );
			boolean				full		= Boolean.TRUE.equals( e.get( "queueIsFull" ) );
			String				health		= Plain.str( e.get( "healthStatus" ) );
			String				evidence	= "pool " + round( pool ) + "%, threads " + round( threads ) + "%, queue " + qsize
			    + ( qcap > 0 ? " of " + qcap : "" ) + " ("
			    + round( queue ) + "%), health " + health;
			Map<String, Object>	report		= Plain.map( e.get( "healthReport" ) );
			List<Object>		reportNotes	= Plain.list( report.get( "recommendations" ) );
			if ( full ) {
				long grow = Math.max( max + 1, Math.round( Math.ceil( max * 1.5 ) ) );
				out.add( new Finding( CRITICAL, "executors", name + ": the queue is full, new tasks are refused", evidence,
				    "Raise the queue capacity from " + qcap + " to about " + qcap * 2
				        + " only if the load is a short burst. If it is steady, raise the threads of the pool from "
				        + max + " to about " + grow + ", or slow the producers down. Tasks that are refused are lost." ) );
			} else if ( queue >= 70 && qcap > 0 ) {
				out.add( new Finding( queue >= 95 ? CRITICAL : WARNING, "executors", name + ": the queue is filling up", evidence,
				    "Work arrives faster than the " + max + " threads take it. Raise the maximum pool size from " + max + " to about "
				        + Math.max( max + 1, Math.round( max * 1.5 ) )
				        + ", or find out why tasks run long." ) );
			} else if ( ( pool >= 95 || threads >= 95 ) && max > 0 ) {
				out.add( new Finding( CRITICAL, "executors", name + ": every thread is busy", evidence,
				    "All " + max + " threads work at once. If tasks wait in a queue, raise the maximum pool size from " + max + " to about "
				        + Math.max( max + 1, Math.round( Math.ceil( max * 1.5 ) ) ) + "; if they are slow, look at what they wait for with threadStack." ) );
			} else if ( ( pool >= 75 || threads >= 75 ) && max > 0 ) {
				out.add( new Finding( WARNING, "executors", name + ": the pool is getting busy", evidence,
				    "At about " + round( Math.max( pool, threads ) ) + "% use there is little room for a burst. Plan for "
				        + Math.max( max + 1, Math.round( Math.ceil( max * 1.25 ) ) )
				        + " threads (now " + max + ", core " + core + ")." ) );
			} else if ( "critical".equals( health ) || "degraded".equals( health ) ) {
				out.add( new Finding( "critical".equals( health ) ? CRITICAL : WARNING, "executors", name + ": the runtime reports it " + health,
				    Plain.str( report.get( "summary" ) ) + ". " + evidence,
				    reportNotes.isEmpty() ? "Open the Executors page and read the health report."
				        : String.join( " ", reportNotes.stream().map( Plain::str ).limit( 3 ).toList() ) ) );
			}
		}
		return out;
	}

	/**
	 * Findings about memory and garbage collection.
	 *
	 * @param heapAfterGcPct how full the heap is after the last collection, in percent of the maximum, or -1 when unknown
	 * @param gcSharePct     the share of the uptime spent in garbage collection, in percent
	 * @param heapUsedPct    how full the heap is now, in percent of the maximum
	 * @param maxHeapMb      the maximum heap in megabytes
	 */
	public static List<Finding> memory( double heapAfterGcPct, double gcSharePct, double heapUsedPct, long maxHeapMb ) {
		List<Finding> out = new ArrayList<>();
		if ( heapAfterGcPct >= 0 ) {
			String evidence = "heap after the last collection " + round( heapAfterGcPct ) + "% of " + maxHeapMb + " MB, now " + round( heapUsedPct ) + "%";
			if ( heapAfterGcPct >= 85 ) {
				out.add( new Finding( CRITICAL, "memory", "The heap is almost full of live data", evidence,
				    "The collector cannot free it, so the live set is close to the maximum. Either raise -Xmx (now " + maxHeapMb + " MB, try about "
				        + Math.round( maxHeapMb * 1.5 )
				        + " MB) or look for a leak: caches or sessions that only grow. A heap dump (System page, admin) shows what holds the memory." ) );
			} else if ( heapAfterGcPct >= 70 ) {
				out.add( new Finding( WARNING, "memory", "The heap holds a lot of live data", evidence,
				    "Less than a third of the heap is free after a collection. Watch whether this number grows between collections; if it does, there is a leak." ) );
			}
		}
		if ( gcSharePct >= 10 ) {
			out.add( new Finding( CRITICAL, "memory", "The JVM spends " + round( gcSharePct ) + "% of its time in garbage collection",
			    "gc time share " + round( gcSharePct ) + "%",
			    "Requests slow down while the collector runs. Raise the heap, reduce allocation (large result sets, big strings) or tune the collector." ) );
		} else if ( gcSharePct >= 5 ) {
			out.add( new Finding( WARNING, "memory", "Garbage collection takes " + round( gcSharePct ) + "% of the time",
			    "gc time share " + round( gcSharePct ) + "%",
			    "Not yet a problem, but pauses add to every request. Check allocation per request on the Requests page (Plus: cost)." ) );
		}
		return out;
	}

	/**
	 * Findings about datasource pools. The input is the list <code>datasources</code> of <code>DatasourceData.list()</code>.
	 */
	public static List<Finding> datasources( List<Map<String, Object>> datasources ) {
		List<Finding> out = new ArrayList<>();
		for ( Map<String, Object> d : datasources ) {
			String				name	= Plain.str( d.get( "name" ) );
			String				state	= Plain.str( d.get( "state" ) );
			Map<String, Object>	pool	= Plain.map( d.get( "pool" ) );
			if ( pool.isEmpty() ) {
				continue;
			}
			long		max			= l( pool.get( "max" ) );
			long		active		= l( pool.get( "active" ) );
			long		pending		= l( pool.get( "pending" ) );
			String		evidence	= active + " of " + max + " connections in use, " + pending + " threads waiting";
			Map<?, ?>	metrics		= d.get( "metrics" ) instanceof Map<?, ?> m ? m : Map.of();
			long		timeouts	= metrics.get( "timeouts" ) instanceof Number n ? n.longValue() : 0;
			if ( timeouts > 0 ) {
				out.add( new Finding( CRITICAL, "datasources", name + ": connection requests timed out " + timeouts + " times",
				    evidence + ", " + timeouts + " timeouts",
				    "Threads gave up waiting for a connection. Raise the maximum pool size from " + max + " to about "
				        + Math.max( max + 1, Math.round( max * 1.5 ) )
				        + " if the database can take it, and check queries that hold connections long (queryStats, sort total)." ) );
			} else if ( "saturated".equals( state ) || "waiting".equals( state ) ) {
				out.add( new Finding( "saturated".equals( state ) ? CRITICAL : WARNING, "datasources", name + ": the pool is " + state, evidence,
				    "Every connection is busy. Look at the slowest statements with queryStats; if they are fine, raise the maximum pool size from " + max
				        + "." ) );
			}
		}
		return out;
	}

	/**
	 * Findings about the requests the server handled lately.
	 *
	 * @param requests     how many requests the numbers are about
	 * @param errorRatePct the share of server errors, in percent
	 * @param p95Ms        the 95th percentile time
	 * @param slowMs       the slow request limit of the settings
	 * @param inflight     the requests running now, each with <code>elapsedMs</code>, <code>uri</code> and <code>thread</code>
	 */
	public static List<Finding> requests( int requests, double errorRatePct, double p95Ms, int slowMs, List<Map<String, Object>> inflight ) {
		List<Finding> out = new ArrayList<>();
		if ( requests >= 5 && errorRatePct >= 5 ) {
			out.add( new Finding( CRITICAL, "requests", "Error rate is " + round( errorRatePct ) + "%",
			    round( errorRatePct ) + "% of " + requests + " requests returned a 5xx status",
			    "Use errors to see the groups, newest first, and requestDetail on a request of the biggest group." ) );
		} else if ( requests >= 5 && errorRatePct >= 1 ) {
			out.add( new Finding( WARNING, "requests", "Error rate is " + round( errorRatePct ) + "%",
			    round( errorRatePct ) + "% of " + requests + " requests returned a 5xx status",
			    "Use errors to see what fails." ) );
		}
		if ( slowMs > 0 && requests >= 5 && p95Ms >= slowMs ) {
			out.add( new Finding( p95Ms >= slowMs * 4 ? CRITICAL : WARNING, "requests", "Slow requests: p95 is " + round( p95Ms ) + " ms",
			    "p95 " + round( p95Ms ) + " ms against a limit of "
			        + slowMs + " ms over " + requests + " requests",
			    "Use requests with problemsOnly to find the slow ones and queryStats (sort total) to see whether the database is the cause." ) );
		}
		List<String> stuck = new ArrayList<>();
		for ( Map<String, Object> r : inflight ) {
			if ( l( r.get( "elapsedMs" ) ) >= Math.max( 30_000, slowMs * 10L ) ) {
				stuck.add( Plain.str( r.get( "uri" ) ) + " for " + l( r.get( "elapsedMs" ) ) / 1000 + " s on " + Plain.str( r.get( "thread" ) ) );
			}
		}
		if ( !stuck.isEmpty() ) {
			out.add( new Finding( WARNING, "requests", stuck.size() + " requests have run for more than 30 seconds", String.join( "; ", cut( stuck, 4 ) ),
			    "Use inFlight to see them, and threadStack on the named thread to see what each one waits for." ) );
		}
		return out;
	}

	/**
	 * Put the findings in order, worst first, and add the verdict.
	 */
	public static Map<String, Object> report( List<Finding> findings, List<String> checked, List<String> skipped ) {
		List<Finding> sorted = new ArrayList<>( findings );
		sorted.sort( Comparator.comparingInt( f -> rank( f.severity() ) ) );
		Map<String, Object>	m		= new LinkedHashMap<>();
		long				crit	= sorted.stream().filter( f -> CRITICAL.equals( f.severity() ) ).count();
		long				warn	= sorted.stream().filter( f -> WARNING.equals( f.severity() ) ).count();
		m.put( "verdict", crit > 0 ? "critical" : warn > 0 ? "warning" : "healthy" );
		m.put( "critical", crit );
		m.put( "warnings", warn );
		m.put( "checked", checked );
		if ( !skipped.isEmpty() ) {
			m.put( "notChecked", skipped );
		}
		List<Map<String, Object>> list = new ArrayList<>();
		for ( Finding f : sorted ) {
			list.add( f.toMap() );
		}
		m.put( "findings", list );
		if ( list.isEmpty() ) {
			m.put( "summary", "No problem found in " + String.join( ", ", checked ) + "." );
		}
		return m;
	}

	private static int rank( String severity ) {
		return switch ( severity.toLowerCase( Locale.ROOT ) ) {
			case CRITICAL -> 0;
			case WARNING -> 1;
			default -> 2;
		};
	}

	@SuppressWarnings( "unchecked" )
	private static List<Map<String, Object>> maps( Object o ) {
		List<Map<String, Object>> out = new ArrayList<>();
		if ( o instanceof List<?> l ) {
			for ( Object x : l ) {
				if ( x instanceof Map<?, ?> m ) {
					out.add( ( Map<String, Object> ) m );
				}
			}
		}
		return out;
	}

	private static List<String> cut( List<String> in, int n ) {
		return in.size() > n ? new ArrayList<>( in.subList( 0, n ) ) : in;
	}

	private static double d( Object o ) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

	private static long l( Object o ) {
		return o instanceof Number n ? n.longValue() : 0;
	}

	private static double round( double v ) {
		return Math.round( v * 10.0 ) / 10.0;
	}

}
