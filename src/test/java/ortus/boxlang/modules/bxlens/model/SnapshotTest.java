/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.model;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;
import ortus.boxlang.modules.bxlens.util.Json;

public class SnapshotTest {

	@Test
	@DisplayName( "Content types classify into short names" )
	public void classify() {
		assertThat( Snapshot.classify( "text/html;charset=UTF-8" ) ).isEqualTo( "html" );
		assertThat( Snapshot.classify( "application/json" ) ).isEqualTo( "json" );
		assertThat( Snapshot.classify( "application/vnd.api+json" ) ).isEqualTo( "json" );
		assertThat( Snapshot.classify( "text/event-stream" ) ).isEqualTo( "sse" );
		assertThat( Snapshot.classify( "image/png" ) ).isEqualTo( "image" );
		assertThat( Snapshot.classify( "" ) ).isEqualTo( "other" );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "A snapshot carries the request, spans, counts and custom panel spans" )
	public void build() {
		LensRequest req = new LensRequest();
		req.method	= "GET";
		req.uri		= "/orders";
		req.status	= 200;
		Span s = req.begin( Span.TEMPLATE, "/orders.bxm", 10 );
		req.end( s );
		req.messages.add( Map.of( "level", "info", "text", "hi" ) );
		LensPanelBuilder p = req.panel( "extra", "Extra", "spans" );
		p.spans( List.of( Map.of( "label", "phase", "start", 1, "dur", 2 ) ) );
		IssueEngine.analyze( req, LensConfig.defaults() );
		Map<String, Object>	snap	= Snapshot.build( req, LensConfig.defaults(), true );
		Map<String, Object>	r		= ( Map<String, Object> ) snap.get( "request" );
		assertThat( r.get( "method" ) ).isEqualTo( "GET" );
		assertThat( r.get( "type" ) ).isEqualTo( "other" );
		List<Map<String, Object>> spans = ( List<Map<String, Object>> ) snap.get( "spans" );
		assertThat( spans ).hasSize( 2 );
		assertThat( spans.get( 1 ).get( "panel" ) ).isEqualTo( "extra" );
		Map<String, Object> counts = ( Map<String, Object> ) snap.get( "counts" );
		assertThat( counts.get( "templates" ) ).isEqualTo( 1 );
		assertThat( counts.get( "messages" ) ).isEqualTo( 1 );
		assertThat( Json.write( snap ) ).contains( "\"spans\"" );
	}

	@Test
	@DisplayName( "A summary has what the History list needs" )
	public void summary() {
		LensRequest req = new LensRequest();
		req.uri			= "/api/orders";
		req.queryString	= "page=2";
		req.contentType	= "application/json";
		Map<String, Object> s = Snapshot.summary( req, LensConfig.defaults() );
		assertThat( s.get( "url" ) ).isEqualTo( "/api/orders?page=2" );
		assertThat( s.get( "type" ) ).isEqualTo( "json" );
		assertThat( s.keySet() ).containsAtLeast( "id", "method", "status", "ms", "queries", "at" );
		assertThat( s.keySet() ).doesNotContain( "issues" );
		assertThat( Snapshot.summaryWithIssues( req, LensConfig.defaults() ).keySet() ).containsAtLeast( "issues", "severity" );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "Secret query parameters are masked in the snapshot and the summary" )
	public void redactsQuery() {
		LensRequest req = new LensRequest();
		req.uri			= "/login";
		req.queryString	= "user=bob&password=hunter2&api_key=abc";
		Map<String, Object>	snap	= Snapshot.build( req, LensConfig.defaults() );
		String				query	= ( String ) ( ( Map<String, Object> ) snap.get( "request" ) ).get( "query" );
		assertThat( query ).isEqualTo( "user=bob&password=[redacted]&api_key=[redacted]" );
		assertThat( Snapshot.summary( req, LensConfig.defaults() ).get( "url" ).toString() ).doesNotContain( "hunter2" );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "Headers whose names look secret are masked, others are kept" )
	public void sanitizesHeaders() {
		LensRequest req = new LensRequest();
		req.requestHeaders = new java.util.LinkedHashMap<>( Map.of( "X-Api-Key", "k1", "Accept", "text/html", "X-Session-Id", "s1" ) );
		Map<String, Object> headers = ( Map<String, Object> ) Snapshot.build( req, new LensConfig( Map.of( "collect", Map.of( "level", "full" ) ) ) )
		    .get( "headers" );
		assertThat( headers.get( "X-Api-Key" ) ).isEqualTo( "[redacted]" );
		assertThat( headers.get( "X-Session-Id" ) ).isEqualTo( "[redacted]" );
		assertThat( headers.get( "Accept" ) ).isEqualTo( "text/html" );
	}

	@Test
	@DisplayName( "The snapshot JSON is built once, on first use, and not before" )
	public void lazy() {
		LensRequest												req		= new LensRequest();
		java.util.concurrent.atomic.AtomicInteger				builds	= new java.util.concurrent.atomic.AtomicInteger();
		ortus.boxlang.modules.bxlens.store.RequestStore.Entry	e		= new ortus.boxlang.modules.bxlens.store.RequestStore.Entry( req.id,
		    Snapshot.summary( req, LensConfig.defaults() ), () -> {
																			    builds.incrementAndGet();
																			    return Json.write( Snapshot.build( req, LensConfig.defaults() ) );
																		    } );
		assertThat( e.built() ).isFalse();
		assertThat( builds.get() ).isEqualTo( 0 );
		String first = e.json();
		assertThat( e.json() ).isSameInstanceAs( first );
		assertThat( builds.get() ).isEqualTo( 1 );
		assertThat( e.built() ).isTrue();
	}

	@Test
	@DisplayName( "Request ids are short, unique and made without a random generator" )
	public void ids() {
		java.util.Set<String> seen = new java.util.HashSet<>();
		for ( int i = 0; i < 5000; i++ ) {
			String id = new LensRequest().id;
			assertThat( id ).matches( "[0-9a-z]{9,16}" );
			assertThat( seen.add( id ) ).isTrue();
		}
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "The bar payload carries no issues, no flags and no N+1 counts, the console payload does" )
	public void barHasNoIssueEngine() {
		LensRequest req = new LensRequest();
		req.status = 200;
		for ( int i = 0; i < 4; i++ ) {
			Span s = req.begin( Span.QUERY, "SELECT 1", 10 );
			s.detail.put( "sql", "SELECT x FROM t WHERE id = ?" );
			req.end( s );
			Map<String, Object> q = new java.util.LinkedHashMap<>();
			q.put( "span", s.id );
			q.put( "sql", "SELECT x FROM t WHERE id = ?" );
			q.put( "ms", 1.0 );
			req.queries.add( q );
		}
		IssueEngine.analyze( req, LensConfig.defaults() );
		assertThat( req.issues ).isNotEmpty();
		Map<String, Object> bar = Snapshot.build( req, LensConfig.defaults() );
		assertThat( bar.containsKey( "issues" ) ).isFalse();
		assertThat( ( ( Map<String, Object> ) bar.get( "counts" ) ).containsKey( "issues" ) ).isFalse();
		for ( Map<String, Object> q : ( List<Map<String, Object>> ) bar.get( "queries" ) ) {
			assertThat( q.containsKey( "flag" ) ).isFalse();
			assertThat( q.containsKey( "count" ) ).isFalse();
		}
		for ( Map<String, Object> sp : ( List<Map<String, Object>> ) bar.get( "spans" ) ) {
			assertThat( sp.containsKey( "flag" ) ).isFalse();
		}
		Map<String, Object> console = Snapshot.build( req, LensConfig.defaults(), true );
		assertThat( console.containsKey( "issues" ) ).isTrue();
		assertThat( ( ( List<Map<String, Object>> ) console.get( "queries" ) ).get( 0 ).containsKey( "flag" ) ).isTrue();
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "The strip is red for a 5xx or an uncaught exception, and for nothing else" )
	public void stripState() {
		LensRequest ok = new LensRequest();
		ok.status = 404;
		ok.addIssue( "warn", "x", "", "", 0, "", 0 );
		assertThat( Snapshot.barState( ok ) ).isEqualTo( "none" );
		LensRequest bad = new LensRequest();
		bad.status = 503;
		assertThat( Snapshot.barState( bad ) ).isEqualTo( "crit" );
		LensRequest thrown = new LensRequest();
		thrown.exceptions.add( Map.of( "origin", "uncaught" ) );
		assertThat( Snapshot.barState( thrown ) ).isEqualTo( "crit" );
		LensRequest caught = new LensRequest();
		caught.exceptions.add( Map.of( "origin", "function:x" ) );
		assertThat( Snapshot.barState( caught ) ).isEqualTo( "none" );
		assertThat( ( ( Map<String, Object> ) Snapshot.build( bad, LensConfig.defaults() ).get( "request" ) ).get( "state" ) ).isEqualTo( "crit" );
	}

}
