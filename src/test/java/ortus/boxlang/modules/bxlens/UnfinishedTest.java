/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Snapshot;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.store.RequestStore;

public class UnfinishedTest {

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "a request the watchdog finishes is marked unfinished, has its spans closed and interrupted, and is counted" )
	void unfinished() throws Exception {
		LensService	svc	= LensService.getInstance();
		LensConfig	cfg	= new LensConfig( Map.of( "console", Map.of( "enabled", true ), "request", Map.of( "maxMinutes", 1 ) ) );
		LensRequest	req	= new LensRequest();
		req.method	= "GET";
		req.uri		= "/stuck.bxm";
		Span span = req.begin( Span.TEMPLATE, "/stuck.bxm", 10 );
		Thread.sleep( 10 );
		long before = svc.getReports().snapshot( false ).get( "session" ) instanceof Map<?, ?> m ? ( ( Number ) m.get( "requests" ) ).longValue() : 0;
		svc.finishUnfinished( req, cfg );
		assertThat( req.unfinished ).isTrue();
		assertThat( span.isOpen() ).isFalse();
		assertThat( span.interrupted ).isTrue();
		long after = ( ( Number ) ( ( Map<String, Object> ) svc.getReports().snapshot( false ).get( "session" ) ).get( "requests" ) ).longValue();
		assertThat( after ).isEqualTo( before + 1 );
		RequestStore.Entry e = svc.getStore().get( req.id );
		assertThat( e ).isNotNull();
		assertThat( e.summary().get( "state" ) ).isEqualTo( "unfinished" );
		String console = e.consoleJson();
		assertThat( console ).contains( "Request never finished" );
		assertThat( console ).contains( "\"interrupted\":true" );
		Map<String, Object> request = ( Map<String, Object> ) Snapshot.build( req, cfg ).get( "request" );
		assertThat( request.get( "state" ) ).isEqualTo( "unfinished" );
		assertThat( req.requestContext ).isNull();
	}

	@Test
	@DisplayName( "the issue analysis runs only for the console, once, and the bar payload is built without it" )
	void lazyAnalysis() {
		LensRequest			req		= new LensRequest();
		LensConfig			cfg		= LensConfig.defaults();
		int[]				runs	= { 0 };
		RequestStore.Entry	e		= new RequestStore.Entry( req.id, Snapshot.summary( req, cfg ), () -> "{\"bar\":1}", () -> {
										runs[ 0 ]++;
										return "{\"console\":1}";
									}, () -> {
										runs[ 0 ]++;
										return Snapshot.summaryWithIssues( req, cfg );
									} );
		assertThat( e.json() ).isEqualTo( "{\"bar\":1}" );
		assertThat( runs[ 0 ] ).isEqualTo( 0 );
		assertThat( e.summary( false ).containsKey( "issues" ) ).isFalse();
		assertThat( runs[ 0 ] ).isEqualTo( 0 );
		assertThat( e.summary( true ).containsKey( "issues" ) ).isTrue();
		e.summary( true );
		assertThat( e.consoleJson() ).isEqualTo( "{\"console\":1}" );
		e.consoleJson();
		assertThat( runs[ 0 ] ).isEqualTo( 2 );
	}

}
