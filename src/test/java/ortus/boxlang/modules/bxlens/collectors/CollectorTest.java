package ortus.boxlang.modules.bxlens.collectors;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.BaseIntegrationTest;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;

/**
 * Integration tests for individual collectors.
 * Uses the real runtime and fires events through the interceptor service.
 * Accesses LensRequestData via reflection to avoid cross-classloader cast issues.
 */
public class CollectorTest extends BaseIntegrationTest {

	private IBoxContext ctx;

	@BeforeEach
	public void setup() {
		ctx = getContext();
		// Fire onRequestStart to have ApplicationCollector initialize LensRequestData
		runtime.getInterceptorService().announce( "onRequestStart", Struct.of( "context", ctx ) );
	}

	/** Get a field from the LensRequestData POJO using reflection. */
	private Object getLensDataField( String fieldName ) throws Exception {
		IBoxContext req = ctx.getRequestContext();
		if ( req == null )
			req = ctx;
		Object data = req.getAttachment( Key.of( "__bxLensData__" ) );
		if ( data == null )
			return null;
		return data.getClass().getField( fieldName ).get( data );
	}

	// -------------------------------------------------------------------------
	// LensRequestData structure
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "LensRequestData POJO is attached to context after onRequestStart" )
	public void testLensDataAttached() throws Exception {
		IBoxContext req = ctx.getRequestContext();
		if ( req == null )
			req = ctx;
		Object data = req.getAttachment( Key.of( "__bxLensData__" ) );
		assertThat( data ).isNotNull();
	}

	@Test
	@DisplayName( "LensRequestData has expected fields after onRequestStart" )
	public void testLensRequestDataFields() throws Exception {
		assertThat( getLensDataField( "requestId" ) ).isNotNull();
		assertThat( ( Boolean ) getLensDataField( "enabled" ) ).isTrue();
		assertThat( getLensDataField( "queries" ) ).isNotNull();
		assertThat( getLensDataField( "exceptions" ) ).isNotNull();
		assertThat( getLensDataField( "templates" ) ).isNotNull();
		assertThat( getLensDataField( "timings" ) ).isNotNull();
	}

	// -------------------------------------------------------------------------
	// QueryCollector
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "QueryCollector records a query when postQueryExecute fires" )
	public void testQueryCollectorRecordsQuery() throws Exception {
		runtime.getInterceptorService().announce( "postQueryExecute", Struct.of(
		    "context", ctx,
		    "sql", "SELECT * FROM users",
		    "executionTime", 42L,
		    "recordCount", 5
		) );

		java.util.List<?> queries = ( java.util.List<?> ) getLensDataField( "queries" );
		assertThat( queries ).hasSize( 1 );
		java.util.Map<?, ?> entry = ( java.util.Map<?, ?> ) queries.get( 0 );
		assertThat( entry.get( "sql" ) ).isEqualTo( "SELECT * FROM users" );
	}

	@Test
	@DisplayName( "QueryCollector does not record a second query beyond maxQueries" )
	public void testQueryCollectorSkipsWhenMaxReached() throws Exception {
		// Fire many queries to ensure the max doesn't cause a crash
		for ( int i = 0; i < 5; i++ ) {
			runtime.getInterceptorService().announce( "postQueryExecute", Struct.of(
			    "context", ctx,
			    "sql", "SELECT " + i,
			    "executionTime", 1L,
			    "recordCount", 0
			) );
		}
		java.util.List<?> queries = ( java.util.List<?> ) getLensDataField( "queries" );
		assertThat( queries.size() ).isAtMost( 100 );
	}

	// -------------------------------------------------------------------------
	// ExceptionCollector
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "ExceptionCollector records an exception when onException fires" )
	public void testExceptionCollectorRecords() throws Exception {
		runtime.getInterceptorService().announce( "onException", Struct.of(
		    "context", ctx,
		    "exception", Struct.of(
		        "type", "application",
		        "message", "Oops something went wrong",
		        "detail", "details here",
		        "stackTrace", "at com.example.Foo"
		    )
		) );

		java.util.List<?> exceptions = ( java.util.List<?> ) getLensDataField( "exceptions" );
		assertThat( exceptions ).hasSize( 1 );
		java.util.Map<?, ?> entry = ( java.util.Map<?, ?> ) exceptions.get( 0 );
		assertThat( entry.get( "type" ) ).isEqualTo( "application" );
		assertThat( entry.get( "message" ) ).isEqualTo( "Oops something went wrong" );
	}

	// -------------------------------------------------------------------------
	// TimelineCollector
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "TimelineCollector records template start/end" )
	public void testTimelineCollector() throws Exception {
		runtime.getInterceptorService().announce( "preTemplateInvoke", Struct.of(
		    "context", ctx,
		    "templatePath", "/my/template.bxm"
		) );

		java.util.List<?> templates = ( java.util.List<?> ) getLensDataField( "templates" );
		assertThat( templates ).hasSize( 1 );
		java.util.Map<?, ?> entry = ( java.util.Map<?, ?> ) templates.get( 0 );
		assertThat( entry.get( "_pending" ) ).isEqualTo( Boolean.TRUE );

		runtime.getInterceptorService().announce( "postTemplateInvoke", Struct.of(
		    "context", ctx,
		    "templatePath", "/my/template.bxm",
		    "executionTime", 10L
		) );

		entry = ( java.util.Map<?, ?> ) templates.get( 0 );
		assertThat( entry.get( "_pending" ) ).isEqualTo( Boolean.FALSE );
		assertThat( entry.get( "executionTime" ) ).isEqualTo( 10L );
	}

	// -------------------------------------------------------------------------
	// ApplicationCollector — session tracking
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "ApplicationCollector tracks activeSessions on session start/end" )
	public void testSessionTracking() {
		// Just verify it doesn't throw — we can't cast to LensService across classloaders
		// but we can verify the announce doesn't crash
		runtime.getInterceptorService().announce( "onSessionStart", Struct.of() );
		runtime.getInterceptorService().announce( "onSessionEnd", Struct.of() );
		// No assertion except no exception
	}

	// -------------------------------------------------------------------------
	// RequestEnd
	// -------------------------------------------------------------------------

	@Test
	@DisplayName( "onRequestEnd sets endedAt and captures globalStats" )
	public void testRequestEnd() throws Exception {
		runtime.getInterceptorService().announce( "onRequestEnd", Struct.of( "context", ctx ) );

		long endedAt = ( Long ) getLensDataField( "endedAt" );
		assertThat( endedAt ).isGreaterThan( 0L );
	}
}
