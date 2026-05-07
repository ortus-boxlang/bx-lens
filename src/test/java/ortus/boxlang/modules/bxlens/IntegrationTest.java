package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class IntegrationTest extends BaseIntegrationTest {

	private static final Key LENS_KEY = Key.of( "__bxLensData__" );

	/** Create a minimal enabled lensData struct and attach it to ctx. */
	private void attachLensData( IBoxContext ctx ) {
		IStruct	vars	= Struct.of(
		    "form", Struct.of(),
		    "url", Struct.of(),
		    "cgi", Struct.of(),
		    "request", Struct.of(),
		    "session", Struct.of(),
		    "application", Struct.of()
		);

		IStruct	data	= Struct.of(
		    "requestId", java.util.UUID.randomUUID().toString(),
		    "enabled", Boolean.TRUE,
		    "startedAt", System.currentTimeMillis(),
		    "endedAt", 0L,
		    "method", "GET",
		    "url", "/test",
		    "statusCode", 200,
		    "queries", Array.of(),
		    "exceptions", Array.of(),
		    "templates", Array.of(),
		    "httpCalls", Array.of(),
		    "soapCalls", Array.of(),
		    "messages", Array.of(),
		    "timings", Array.of(),
		    "_pendingTimings", Struct.of(),
		    "variables", vars,
		    "globalStats", Struct.of()
		);

		ctx.putAttachment( LENS_KEY, data );
	}

	@Test
	@DisplayName( "Module bxLens is registered" )
	public void testModuleRegistered() {
		assertThat( moduleService.hasModule( MODULE_NAME ) ).isTrue();
	}

	@Test
	@DisplayName( "LensEnable() returns true" )
	public void testLensEnable() {
		IBoxContext	ctx		= getContext();
		Object		result	= runtime.executeStatement( "LensEnable()", ctx );
		assertThat( result ).isEqualTo( true );
	}

	@Test
	@DisplayName( "LensDisable() returns true" )
	public void testLensDisable() {
		IBoxContext	ctx		= getContext();
		Object		result	= runtime.executeStatement( "LensDisable()", ctx );
		assertThat( result ).isEqualTo( true );
	}

	@Test
	@DisplayName( "LensRender() returns empty string when disabled" )
	public void testLensRenderDisabled() {
		IBoxContext	ctx		= getContext();
		Object		result	= runtime.executeStatement( "LensRender()", ctx );
		assertThat( result.toString() ).isEmpty();
	}

	@Test
	@DisplayName( "LensRender() returns HTML containing bxlens-root and bxLensBar when enabled" )
	public void testLensRenderEnabled() {
		IBoxContext ctx = getContext();
		attachLensData( ctx );

		Object	result	= runtime.executeStatement( "LensRender()", ctx );
		String	html	= result.toString();

		assertThat( html ).contains( "bxlens-root" );
		assertThat( html ).contains( "bxLensBar" );
	}

	@Test
	@DisplayName( "LensRender() output contains no CDN references" )
	public void testLensRenderNoCDN() {
		IBoxContext ctx = getContext();
		attachLensData( ctx );

		Object	result	= runtime.executeStatement( "LensRender()", ctx );
		String	html	= result.toString();

		assertThat( html ).doesNotContain( "cdn.jsdelivr" );
		assertThat( html ).doesNotContain( "cdnjs" );
		assertThat( html ).doesNotContain( "unpkg.com" );
	}

	@Test
	@DisplayName( "LensMessage() adds a message to the data attachment" )
	public void testLensMessage() {
		IBoxContext ctx = getContext();
		attachLensData( ctx );

		runtime.executeStatement( "LensMessage('hello world','info')", ctx );

		IStruct data = ( IStruct ) ctx.getAttachment( LENS_KEY );
		assertThat( data ).isNotNull();
		Array messages = ( Array ) data.get( Key.of( "messages" ) );
		assertThat( messages.size() ).isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "LensStart/LensStop produces a timing entry" )
	public void testLensStartStop() {
		IBoxContext ctx = getContext();
		attachLensData( ctx );

		// LensStart returns a hash; pass it to LensStop
		Object hash = runtime.executeStatement( "LensStart('myOp')", ctx );
		runtime.executeStatement( "LensStop('" + hash + "')", ctx );

		IStruct	data	= ( IStruct ) ctx.getAttachment( LENS_KEY );
		Array	timings	= ( Array ) data.get( Key.of( "timings" ) );
		assertThat( timings.size() ).isEqualTo( 1 );
	}
}
