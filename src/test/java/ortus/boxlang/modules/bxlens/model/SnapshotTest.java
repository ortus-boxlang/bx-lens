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
		Map<String, Object>	snap	= Snapshot.build( req, LensConfig.defaults(), null );
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
		Map<String, Object> s = Snapshot.summary( req );
		assertThat( s.get( "url" ) ).isEqualTo( "/api/orders?page=2" );
		assertThat( s.get( "type" ) ).isEqualTo( "json" );
		assertThat( s.keySet() ).containsAtLeast( "id", "method", "status", "ms", "issues", "severity", "queries", "at" );
	}

}
