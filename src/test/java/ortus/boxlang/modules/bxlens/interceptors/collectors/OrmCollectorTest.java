/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.BaseIntegrationTest;
import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.OrmTotals;
import ortus.boxlang.modules.bxlens.QueryStats;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Drives the ORM collector with the events bx-orm announces, built by hand.
 */
public class OrmCollectorTest extends BaseIntegrationTest {

	/** The collector with its own settings and totals, so no module and no service is needed. */
	static final class Probe extends OrmCollector {

		final OrmTotals	totals	= new OrmTotals();
		LensConfig		cfg;

		Probe( Map<String, Object> settings ) {
			this.cfg = new LensConfig( settings );
		}

		@Override
		protected LensConfig config() {
			return this.cfg;
		}

		@Override
		protected OrmTotals totals() {
			return this.totals;
		}
	}

	private static final Map<String, Object>	FULL		= Map.of( "collect", Map.of( "level", "full" ) );
	private static final Map<String, Object>	FULL_PARAMS	= Map.of( "collect", Map.of( "level", "full" ), "collectors",
	    Map.of( "queries", Map.of( "includeParams", true ) ) );

	private LensRequest request() {
		LensRequest req = new LensRequest();
		context.putAttachment( Keys.requestAttach, req );
		RequestBoxContext.setCurrent( context );
		return req;
	}

	/** Creating a request context makes it current on the thread: start and end every test with a bare thread. */
	@BeforeEach
	@AfterEach
	void clearCurrent() {
		while ( RequestBoxContext.getCurrent() != null ) {
			RequestBoxContext.removeCurrent();
		}
	}

	private static IStruct query( String sql, String kind, long nanos, long rows, Throwable error, List<Object> params ) {
		IStruct e = Struct.of( Key.of( "sql" ), sql, Key.of( "kind" ), kind, Key.of( "elapsedNanos" ), nanos, Key.of( "rows" ), rows, Key.of( "datasource" ),
		    "demo",
		    Key.of( "appName" ), "shop", Key.of( "hql" ), null, Key.of( "entityName" ), null, Key.of( "error" ), error );
		if ( params != null ) {
			e.put( Key.of( "params" ), Array.fromList( params ) );
		}
		return e;
	}

	private static void pause() {
		try {
			Thread.sleep( 15 );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	@DisplayName( "a query event becomes a query span and a query entry on the current request" )
	void queryBecomesSpan() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		pause();
		probe.onORMQuery( query( "select * from books where id = ?", "select", 2_000_000L, 3L, null, null ) );
		assertThat( req.spans ).hasSize( 1 );
		Span span = req.spans.get( 0 );
		assertThat( span.type ).isEqualTo( Span.QUERY );
		assertThat( span.isOpen() ).isFalse();
		// The span is as long as elapsedNanos and ends now
		assertThat( span.durationNs() ).isEqualTo( 2_000_000L );
		assertThat( span.detail.get( "orm" ) ).isEqualTo( true );
		assertThat( span.detail.get( "kind" ) ).isEqualTo( "select" );
		assertThat( req.queries ).hasSize( 1 );
		Map<String, Object> q = req.queries.get( 0 );
		assertThat( q.get( "span" ) ).isEqualTo( span.id );
		assertThat( q.get( "sql" ) ).isEqualTo( "select * from books where id = ?" );
		assertThat( q.get( "ms" ) ).isEqualTo( 2.0 );
		assertThat( q.get( "rows" ) ).isEqualTo( 3L );
		assertThat( q.get( "datasource" ) ).isEqualTo( "demo" );
		assertThat( q.get( "orm" ) ).isEqualTo( true );
		assertThat( q.get( "kind" ) ).isEqualTo( "select" );
		assertThat( q ).containsKey( "file" );
		assertThat( q ).containsKey( "line" );
		assertThat( q ).doesNotContainKey( "error" );
		assertThat( q ).doesNotContainKey( "params" );
		// Counted in the totals as a statement that showed in a request
		Map<String, Object> total = rows( probe ).get( 0 );
		assertThat( total.get( "queries" ) ).isEqualTo( 1L );
		assertThat( total.get( "unattributed" ) ).isEqualTo( 0L );
	}

	@Test
	@DisplayName( "a statement longer than the request has been running starts at the request start" )
	void clampsToStart() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		probe.onORMQuery( query( "create table t (id int)", "ddl", 60_000_000_000L, -1L, null, null ) );
		Span span = req.spans.get( 0 );
		assertThat( span.startNs ).isEqualTo( 0L );
		assertThat( span.endNs ).isAtLeast( 0L );
		// DDL has no row count: empty, not -1
		assertThat( req.queries.get( 0 ).get( "rows" ) ).isNull();
		assertThat( req.queries.get( 0 ).get( "ms" ) ).isEqualTo( 60000.0 );
	}

	@Test
	@DisplayName( "parameter values come only when the event has them and queries.includeParams is on" )
	void params() {
		List<Object>	bound	= List.of( 42, "Ada" );
		// Event without params, setting on: nothing to show
		Probe			probe	= new Probe( FULL_PARAMS );
		LensRequest		req		= request();
		probe.onORMQuery( query( "select 1", "select", 1_000L, 1L, null, null ) );
		assertThat( req.queries.get( 0 ) ).doesNotContainKey( "params" );
		// Event with params, setting on: shown
		probe.onORMQuery( query( "select 2", "select", 1_000L, 1L, null, bound ) );
		assertThat( req.queries.get( 1 ).get( "params" ) ).isEqualTo( List.of( 42, "Ada" ) );
		assertThat( req.spans.get( 1 ).detail.get( "params" ) ).isEqualTo( List.of( 42, "Ada" ) );
		// Event with params, setting off (the default): never shown
		Probe		off		= new Probe( FULL );
		LensRequest	req2	= request();
		off.onORMQuery( query( "select 3", "select", 1_000L, 1L, null, bound ) );
		assertThat( req2.queries.get( 0 ) ).doesNotContainKey( "params" );
		// Light level: never shown
		Probe		light	= new Probe( Map.of( "collectors", Map.of( "queries", Map.of( "includeParams", true ) ) ) );
		LensRequest	req3	= request();
		light.onORMQuery( query( "select 4", "select", 1_000L, 1L, null, bound ) );
		assertThat( req3.queries.get( 0 ) ).doesNotContainKey( "params" );
	}

	@Test
	@DisplayName( "a failed statement carries its error into the request and counts as a failure in the query statistics" )
	void failedStatement() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		probe.onORMQuery( query( "select * from nope", "select", 1_000L, -1L, new RuntimeException( "Table NOPE does not exist" ), null ) );
		Map<String, Object> q = req.queries.get( 0 );
		assertThat( q.get( "error" ) ).isEqualTo( "Table NOPE does not exist" );
		assertThat( req.spans.get( 0 ).detail.get( "error" ) ).isEqualTo( "Table NOPE does not exist" );
		assertThat( rows( probe ).get( 0 ).get( "errors" ) ).isEqualTo( 1L );
		QueryStats stats = new QueryStats();
		stats.record( req, 25 );
		@SuppressWarnings( "unchecked" )
		List<Map<String, Object>> statements = ( List<Map<String, Object>> ) stats.snapshot().get( "statements" );
		assertThat( statements ).hasSize( 1 );
		assertThat( statements.get( 0 ).get( "failures" ) ).isEqualTo( 1L );
		assertThat( statements.get( 0 ).get( "orm" ) ).isEqualTo( true );
		assertThat( statements.get( 0 ).get( "kind" ) ).isEqualTo( "select" );
	}

	@Test
	@DisplayName( "hql and entity are kept when the event has them" )
	void hqlAndEntity() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		IStruct		e		= query( "select b.id from books b", "select", 1_000L, 2L, null, null );
		e.put( Key.of( "hql" ), "from Book where author = :a" );
		e.put( Key.of( "entityName" ), "Book" );
		probe.onORMQuery( e );
		assertThat( req.queries.get( 0 ).get( "hql" ) ).isEqualTo( "from Book where author = :a" );
		assertThat( req.queries.get( 0 ).get( "entity" ) ).isEqualTo( "Book" );
	}

	@Test
	@DisplayName( "with no request on the thread the statement counts in the ORM totals only" )
	void noRequest() {
		Probe probe = new Probe( FULL );
		assertThat( RequestBoxContext.getCurrent() ).isNull();
		probe.onORMQuery( query( "create table t (id int)", "ddl", 1_000L, -1L, null, null ) );
		Map<String, Object> total = rows( probe ).get( 0 );
		assertThat( total.get( "queries" ) ).isEqualTo( 1L );
		assertThat( total.get( "ddl" ) ).isEqualTo( 1L );
		assertThat( total.get( "unattributed" ) ).isEqualTo( 1L );
	}

	@Test
	@DisplayName( "a request that is not tracked or already finished gets nothing" )
	void untrackedRequest() {
		Probe probe = new Probe( FULL );
		// A request context without a Lens request
		RequestBoxContext.setCurrent( context );
		probe.onORMQuery( query( "select 1", "select", 1_000L, 1L, null, null ) );
		assertThat( rows( probe ).get( 0 ).get( "unattributed" ) ).isEqualTo( 1L );
		// A finished one
		LensRequest req = request();
		req.finished.set( true );
		probe.onORMQuery( query( "select 2", "select", 1_000L, 1L, null, null ) );
		assertThat( req.queries ).isEmpty();
		assertThat( rows( probe ).get( 0 ).get( "unattributed" ) ).isEqualTo( 2L );
	}

	@Test
	@DisplayName( "over the query cap the statement is counted in the totals but not added to the request" )
	void queryCap() {
		Probe		probe	= new Probe( Map.of( "collect", Map.of( "level", "full" ), "collectors", Map.of( "queries", Map.of( "max", 1 ) ) ) );
		LensRequest	req		= request();
		probe.onORMQuery( query( "select 1", "select", 1_000L, 1L, null, null ) );
		probe.onORMQuery( query( "select 2", "select", 1_000L, 1L, null, null ) );
		assertThat( req.queries ).hasSize( 1 );
		assertThat( req.spans ).hasSize( 1 );
		Map<String, Object> total = rows( probe ).get( 0 );
		assertThat( total.get( "queries" ) ).isEqualTo( 2L );
		assertThat( total.get( "unattributed" ) ).isEqualTo( 1L );
	}

	@Test
	@DisplayName( "a flush event adds to the flush counts and touches no request" )
	void flush() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		probe.onORMFlush(
		    Struct.of( Key.of( "inserts" ), 2L, Key.of( "updates" ), 1L, Key.of( "deletes" ), 0L, Key.of( "elapsedNanos" ), 4_000_000L, Key.of( "datasource" ),
		        "demo",
		        Key.of( "appName" ), "shop" ) );
		Map<String, Object> total = rows( probe ).get( 0 );
		assertThat( total.get( "flushes" ) ).isEqualTo( 1L );
		assertThat( total.get( "flushInserts" ) ).isEqualTo( 2L );
		assertThat( total.get( "flushUpdates" ) ).isEqualTo( 1L );
		assertThat( total.get( "flushMs" ) ).isEqualTo( 4.0 );
		assertThat( req.spans ).isEmpty();
		assertThat( req.queries ).isEmpty();
	}

	@Test
	@DisplayName( "an exception event is listed as a recent failure" )
	@SuppressWarnings( "unchecked" )
	void exception() {
		Probe probe = new Probe( FULL );
		probe.onORMException( Struct.of( Key.of( "error" ), new IllegalStateException( "deadlock" ), Key.of( "sql" ), "update books set x = 1 where id = 9",
		    Key.of( "datasource" ), "demo", Key.of( "appName" ), "shop" ) );
		List<Map<String, Object>> failures = ( List<Map<String, Object>> ) probe.totals.snapshot().get( "failures" );
		assertThat( failures ).hasSize( 1 );
		assertThat( failures.get( 0 ).get( "error" ) ).isEqualTo( "deadlock" );
		assertThat( failures.get( 0 ).get( "datasource" ) ).isEqualTo( "demo" );
		assertThat( failures.get( 0 ).get( "app" ) ).isEqualTo( "shop" );
		assertThat( rows( probe ).get( 0 ).get( "exceptions" ) ).isEqualTo( 1L );
	}

	@Test
	@DisplayName( "a broken or empty event never throws" )
	void neverThrows() {
		Probe probe = new Probe( FULL );
		request();
		probe.onORMQuery( new Struct() );
		probe.onORMQuery( Struct.of( Key.of( "sql" ), null, Key.of( "elapsedNanos" ), "not a number", Key.of( "error" ), "not a throwable" ) );
		probe.onORMFlush( new Struct() );
		probe.onORMException( new Struct() );
		probe.onORMException( null );
		probe.onORMQuery( null );
	}

	@Test
	@DisplayName( "the collector listens to exactly the three ORM events, belongs to the orm integration and is off by default" )
	void listensToEvents() {
		List<String> points = new ArrayList<>();
		for ( Method m : OrmCollector.class.getDeclaredMethods() ) {
			if ( m.isAnnotationPresent( InterceptionPoint.class ) ) {
				points.add( m.getName() );
			}
		}
		assertThat( points ).containsExactly( "onORMQuery", "onORMFlush", "onORMException" );
		OrmCollector c = new OrmCollector();
		assertThat( c.integration() ).isEqualTo( "orm" );
		assertThat( c.id() ).isEqualTo( "orm" );
		assertThat( c.enabledByDefault() ).isFalse();
	}

	@Test
	@DisplayName( "announced through the interceptor service the collector receives the events and the span lands on the request" )
	void announced() {
		Probe		probe	= new Probe( FULL );
		LensRequest	req		= request();
		Key			point	= Key.of( "onORMQuery" );
		runtime.getInterceptorService().register( probe );
		try {
			runtime.getInterceptorService().announce( point, query( "select * from books", "select", 3_000_000L, 5L, null, null ) );
			runtime.getInterceptorService().announce( Key.of( "onORMFlush" ),
			    Struct.of( Key.of( "inserts" ), 1L, Key.of( "datasource" ), "demo", Key.of( "appName" ), "shop" ) );
		} finally {
			runtime.getInterceptorService().unregister( probe );
		}
		assertThat( req.queries ).hasSize( 1 );
		assertThat( req.queries.get( 0 ).get( "rows" ) ).isEqualTo( 5L );
		assertThat( rows( probe ).get( 0 ).get( "flushes" ) ).isEqualTo( 1L );
		// Unregistered: further events are not heard
		runtime.getInterceptorService().announce( point, query( "select 2", "select", 1L, 1L, null, null ) );
		assertThat( req.queries ).hasSize( 1 );
	}

	@SuppressWarnings( "unchecked" )
	private static List<Map<String, Object>> rows( Probe probe ) {
		return ( List<Map<String, Object>> ) probe.totals.snapshot().get( "datasources" );
	}

}
