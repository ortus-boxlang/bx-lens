/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.bxlens.model;

import static com.google.common.truth.Truth.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.LensConfig;

public class IssueEngineTest {

	private final LensConfig cfg = new LensConfig(
	    Map.of( "thresholds", Map.of( "slowQueryMs", 25, "nPlusOneMin", 3, "slowRequestMs", 500, "slowTemplateMs", 100 ) ) );

	private void query( LensRequest req, String sql, double ms ) {
		Span s = req.begin( Span.QUERY, sql, 100 );
		req.end( s );
		Map<String, Object> q = new LinkedHashMap<>();
		q.put( "span", s.id );
		q.put( "sql", sql );
		q.put( "ms", ms );
		q.put( "file", "page.bxm" );
		q.put( "line", 7 );
		req.queries.add( q );
	}

	@Test
	@DisplayName( "The same statement three times is an N+1 warning" )
	public void nPlusOne() {
		LensRequest req = new LensRequest();
		for ( int i = 0; i < 4; i++ ) {
			query( req, "SELECT name FROM customers WHERE id = ?", 1.0 );
		}
		IssueEngine.analyze( req, cfg );
		assertThat( req.issues ).hasSize( 1 );
		assertThat( req.issues.get( 0 ).get( "title" ) ).isEqualTo( "N+1 query pattern" );
		assertThat( req.issues.get( 0 ).get( "severity" ) ).isEqualTo( "warn" );
		assertThat( req.queries.get( 0 ).get( "count" ) ).isEqualTo( 4 );
		assertThat( IssueEngine.severity( req ) ).isEqualTo( "warn" );
	}

	@Test
	@DisplayName( "Statements that differ only in case and spacing group together" )
	public void normalizes() {
		assertThat( IssueEngine.normalize( "SELECT  a\nFROM t" ) ).isEqualTo( IssueEngine.normalize( "select a from t" ) );
	}

	@Test
	@DisplayName( "Two runs is not yet an N+1" )
	public void belowThreshold() {
		LensRequest req = new LensRequest();
		query( req, "SELECT 1", 1.0 );
		query( req, "SELECT 1", 1.0 );
		IssueEngine.analyze( req, cfg );
		assertThat( req.issues ).isEmpty();
	}

	@Test
	@DisplayName( "A slow query warns, and four times the limit is critical" )
	public void slowQuery() {
		LensRequest req = new LensRequest();
		query( req, "SELECT a", 30 );
		query( req, "SELECT b", 120 );
		IssueEngine.analyze( req, cfg );
		assertThat( req.issues ).hasSize( 2 );
		assertThat( req.issues.get( 0 ).get( "severity" ) ).isEqualTo( "warn" );
		assertThat( req.issues.get( 1 ).get( "severity" ) ).isEqualTo( "crit" );
		assertThat( IssueEngine.severity( req ) ).isEqualTo( "crit" );
	}

	@Test
	@DisplayName( "Exceptions become critical issues with their location" )
	public void exceptions() {
		LensRequest			req	= new LensRequest();
		Map<String, Object>	ex	= new LinkedHashMap<>();
		ex.put( "type", "KeyNotFoundException" );
		ex.put( "message", "no key" );
		ex.put( "file", "a.bxm" );
		ex.put( "line", 5 );
		req.exceptions.add( ex );
		IssueEngine.analyze( req, cfg );
		assertThat( req.issues.get( 0 ).get( "severity" ) ).isEqualTo( "crit" );
		assertThat( req.issues.get( 0 ).get( "file" ) ).isEqualTo( "a.bxm" );
		assertThat( req.issues.get( 0 ).get( "line" ) ).isEqualTo( 5 );
	}

	@Test
	@DisplayName( "Server errors are critical and client errors warn" )
	public void statuses() {
		LensRequest a = new LensRequest();
		a.status = 503;
		IssueEngine.analyze( a, cfg );
		assertThat( IssueEngine.severity( a ) ).isEqualTo( "crit" );
		LensRequest b = new LensRequest();
		b.status = 404;
		IssueEngine.analyze( b, cfg );
		assertThat( IssueEngine.severity( b ) ).isEqualTo( "warn" );
		LensRequest c = new LensRequest();
		IssueEngine.analyze( c, cfg );
		assertThat( IssueEngine.severity( c ) ).isEqualTo( "none" );
	}

	@Test
	@DisplayName( "A template is slow by its own time, not because a child was slow" )
	public void selfTime() {
		LensRequest	req		= new LensRequest();
		Span		parent	= new Span( 1, Span.TEMPLATE, "parent.bxm", 0, 0 );
		parent.endNs = 300_000_000L;
		Span child = new Span( 2, Span.QUERY, "select", 10_000_000L, 1 );
		child.endNs = 290_000_000L;
		req.spans.addAll( List.of( parent, child ) );
		IssueEngine.analyze( req, cfg );
		assertThat( parent.flagSeverity ).isNull();
		Span lone = new Span( 3, Span.TEMPLATE, "lone.bxm", 0, 0 );
		lone.endNs = 300_000_000L;
		LensRequest req2 = new LensRequest();
		req2.spans.add( lone );
		IssueEngine.analyze( req2, cfg );
		assertThat( lone.flagSeverity ).isEqualTo( "warn" );
	}

	@Test
	@DisplayName( "Failed outgoing HTTP calls are issues: 4xx warn, 5xx and transport errors are critical" )
	public void outgoingHttp() {
		LensRequest req = new LensRequest();
		req.http.add( Map.of( "method", "GET", "url", "http://a/ok", "status", 200 ) );
		req.http.add( Map.of( "method", "GET", "url", "http://a/missing", "status", 404 ) );
		req.http.add( Map.of( "method", "POST", "url", "http://a/boom", "status", 503 ) );
		req.http.add( Map.of( "method", "GET", "url", "http://a/down", "status", 0 ) );
		IssueEngine.analyze( req, cfg );
		assertThat( req.issues ).hasSize( 3 );
		assertThat( req.issues.get( 0 ).get( "severity" ) ).isEqualTo( "warn" );
		assertThat( req.issues.get( 1 ).get( "severity" ) ).isEqualTo( "crit" );
		assertThat( req.issues.get( 2 ).get( "title" ) ).isEqualTo( "Outgoing HTTP call failed" );
	}

}
