/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The numbers of the Overview page: aggregates over the requests in memory, counts, errors, percentiles and the slowest routes. The console
 * and Lensy read the same figures.
 */
public final class OverviewData {

	private OverviewData() {
	}

	/**
	 * Aggregates over the requests in memory: counts, errors, percentiles and the slowest routes.
	 */
	public static Map<String, Object> build( LensService service ) {
		List<Map<String, Object>>	all		= service.getStore().summaries();
		long						now		= System.currentTimeMillis();
		long						cutoff	= now - 5 * 60_000L;
		List<Map<String, Object>>	win		= new ArrayList<>();
		for ( Map<String, Object> r : all ) {
			if ( num( r.get( "at" ) ) >= cutoff ) {
				win.add( r );
			}
		}
		List<Map<String, Object>>	use	= win.isEmpty() ? all : win;
		Map<String, Object>			m	= new LinkedHashMap<>();
		m.put( "windowMinutes", win.isEmpty() ? 0 : 5 );
		m.put( "requests", use.size() );
		int										errors	= 0;
		double									queries	= 0;
		double[]								times	= new double[ use.size() ];
		Map<String, List<Map<String, Object>>>	routes	= new TreeMap<>();
		int										i		= 0;
		for ( Map<String, Object> r : use ) {
			if ( num( r.get( "status" ) ) >= 500 ) {
				errors++;
			}
			queries			+= num( r.get( "queries" ) );
			times[ i++ ]	= num( r.get( "ms" ) );
			String	url	= String.valueOf( r.get( "url" ) );
			int		q	= url.indexOf( '?' );
			routes.computeIfAbsent( q > 0 ? url.substring( 0, q ) : url, k -> new ArrayList<>() ).add( r );
		}
		java.util.Arrays.sort( times );
		m.put( "errors", errors );
		m.put( "errorRate", use.isEmpty() ? 0 : Math.round( errors * 1000.0 / use.size() ) / 10.0 );
		m.put( "medianMs", percentile( times, 0.5 ) );
		m.put( "p95Ms", percentile( times, 0.95 ) );
		m.put( "queriesPerRequest", use.isEmpty() ? 0 : Math.round( queries * 10.0 / use.size() ) / 10.0 );
		m.put( "perSecond", win.isEmpty() ? 0 : Math.round( win.size() * 10.0 / 300 ) / 10.0 );
		List<Map<String, Object>> slow = new ArrayList<>();
		routes.forEach( ( route, rs ) -> {
			double[]			t	= rs.stream().mapToDouble( r -> num( r.get( "ms" ) ) ).sorted().toArray();
			long				err	= rs.stream().filter( r -> num( r.get( "status" ) ) >= 500 ).count();
			Map<String, Object>	rm	= new LinkedHashMap<>();
			rm.put( "route", route );
			rm.put( "p95Ms", percentile( t, 0.95 ) );
			rm.put( "calls", rs.size() );
			rm.put( "errors", err );
			slow.add( rm );
		} );
		slow.sort( Comparator.comparingDouble( ( Map<String, Object> r ) -> num( r.get( "p95Ms" ) ) ).reversed() );
		m.put( "slowest", slow.size() > 6 ? slow.subList( 0, 6 ) : slow );
		// Series for the sparkline: requests per 5 second bucket over the last 5 minutes
		int[] buckets = new int[ 60 ];
		for ( Map<String, Object> r : all ) {
			long age = now - ( long ) num( r.get( "at" ) );
			if ( age >= 0 && age < 300_000 ) {
				buckets[ 59 - ( int ) ( age / 5000 ) ]++;
			}
		}
		List<Integer> series = new ArrayList<>();
		for ( int b : buckets ) {
			series.add( b );
		}
		m.put( "series", series );
		m.put( "lens", service.asyncStats() );
		m.put( "server", service.getIdentity().get().toMap() );
		// Attention list from what we know today: failing routes and slow routes
		List<Map<String, Object>> attention = new ArrayList<>();
		for ( Map<String, Object> r : slow ) {
			if ( num( r.get( "errors" ) ) > 0 ) {
				attention.add( item( "crit", r.get( "route" ) + " returned " + r.get( "errors" ) + " server errors", "requests" ) );
			} else if ( num( r.get( "p95Ms" ) ) >= service.getConfig().slowRequestMs ) {
				attention.add( item( "warn", r.get( "route" ) + " p95 is " + Math.round( num( r.get( "p95Ms" ) ) ) + " ms", "requests" ) );
			}
		}
		m.put( "attention", attention.size() > 6 ? attention.subList( 0, 6 ) : attention );
		return m;
	}

	private static Map<String, Object> item( String sev, String text, String go ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "severity", sev );
		m.put( "text", text );
		m.put( "go", go );
		return m;
	}

	private static double percentile( double[] sorted, double p ) {
		if ( sorted.length == 0 ) {
			return 0;
		}
		int idx = ( int ) Math.min( sorted.length - 1, Math.ceil( p * sorted.length ) - 1 );
		return Math.round( sorted[ Math.max( 0, idx ) ] * 10.0 ) / 10.0;
	}

	private static double num( Object o ) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

}
