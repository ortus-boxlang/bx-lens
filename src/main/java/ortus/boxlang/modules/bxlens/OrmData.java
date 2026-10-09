/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.collectors.OrmCollector;

/**
 * Global Hibernate statistics of every bx-orm session factory Lens hooked, read by reflection (Hibernate lives in bx-orm's class loader).
 * These are totals since the factory was built: no attribution to a request. Empty when bx-orm is not installed or no ORM application has started.
 */
public final class OrmData {

	private static final String[] COUNTS = { "queryExecutionCount", "entityLoadCount", "entityFetchCount", "entityInsertCount", "entityUpdateCount",
	    "entityDeleteCount", "collectionLoadCount", "collectionFetchCount", "collectionUpdateCount", "flushCount", "sessionOpenCount", "sessionCloseCount",
	    "transactionCount", "successfulTransactionCount", "connectCount", "prepareStatementCount", "closeStatementCount", "optimisticFailureCount",
	    "secondLevelCacheHitCount", "secondLevelCacheMissCount", "secondLevelCachePutCount", "queryCacheHitCount", "queryCacheMissCount",
	    "queryCachePutCount" };

	public Map<String, Object> stats() {
		List<Map<String, Object>>						out	= new ArrayList<>();
		List<Map.Entry<Object, Map<String, Object>>>	all;
		synchronized ( OrmCollector.FACTORIES ) {
			all = new ArrayList<>( OrmCollector.FACTORIES.entrySet() );
		}
		for ( Map.Entry<Object, Map<String, Object>> e : all ) {
			Map<String, Object> m = new LinkedHashMap<>( e.getValue() );
			try {
				Object st = e.getKey().getClass().getMethod( "getStatistics" ).invoke( e.getKey() );
				m.put( "enabled", Boolean.TRUE.equals( st.getClass().getMethod( "isStatisticsEnabled" ).invoke( st ) ) );
				Map<String, Object> counts = new LinkedHashMap<>();
				for ( String c : COUNTS ) {
					Object v = get( st, c );
					if ( v != null ) {
						counts.put( c, v );
					}
				}
				m.put( "counts", counts );
				m.put( "slowestMs", get( st, "queryExecutionMaxTime" ) );
				m.put( "slowestQuery", get( st, "queryExecutionMaxTimeQueryString" ) );
				m.put( "startedAt", get( st, "start" ) instanceof java.time.Instant i ? i.toEpochMilli() : get( st, "startTime" ) );
				Object ents = get( st, "entityNames" );
				m.put( "entities", ents instanceof String[] a ? List.of( a ) : List.of() );
				List<Map<String, Object>>	queries	= new ArrayList<>();
				Object						qs		= get( st, "queries" );
				if ( qs instanceof String[] arr ) {
					Method one = st.getClass().getMethod( "getQueryStatistics", String.class );
					for ( String hql : arr ) {
						Object				q	= one.invoke( st, hql );
						Map<String, Object>	qm	= new LinkedHashMap<>();
						qm.put( "hql", hql.length() > 1000 ? hql.substring( 0, 1000 ) : hql );
						for ( String f : new String[] { "executionCount", "executionRowCount", "executionAvgTime", "executionMaxTime", "executionMinTime",
						    "cacheHitCount",
						    "cacheMissCount", "cachePutCount" } ) {
							qm.put( f, get( q, f ) );
						}
						queries.add( qm );
					}
				}
				queries.sort(
				    Comparator.comparingLong( ( Map<String, Object> q ) -> q.get( "executionMaxTime" ) instanceof Number n ? n.longValue() : 0 ).reversed() );
				m.put( "queries", queries.size() > 50 ? queries.subList( 0, 50 ) : queries );
			} catch ( Throwable t ) {
				m.put( "error", String.valueOf( t.getMessage() ) );
			}
			out.add( m );
		}
		Map<String, Object> r = new LinkedHashMap<>();
		r.put( "factories", out );
		r.put( "installed", BoxRuntimeOrm.installed() );
		return r;
	}

	private static Object get( Object o, String name ) {
		try {
			String cap = Character.toUpperCase( name.charAt( 0 ) ) + name.substring( 1 );
			try {
				return o.getClass().getMethod( "get" + cap ).invoke( o );
			} catch ( NoSuchMethodException e ) {
				return o.getClass().getMethod( name ).invoke( o );
			}
		} catch ( Throwable t ) {
			return null;
		}
	}

	/** Is bx-orm present? */
	private static final class BoxRuntimeOrm {

		static boolean installed() {
			try {
				return ortus.boxlang.runtime.BoxRuntime.getInstance().getGlobalService( ortus.boxlang.runtime.scopes.Key.of( "ORMService" ) ) != null;
			} catch ( Throwable t ) {
				return false;
			}
		}
	}

}
