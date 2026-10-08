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
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Plain;

public class ReportsTest {

	private static LensRequest request( String uri, int status ) {
		LensRequest r = new LensRequest();
		r.method	= "GET";
		r.uri		= uri;
		r.status	= status;
		r.endNanos	= r.startNanos + 20_000_000L;
		return r;
	}

	@Test
	@DisplayName( "counts requests, errors, status classes and URLs" )
	@SuppressWarnings( "unchecked" )
	void counts() {
		Reports reports = new Reports();
		reports.record( request( "/a", 200 ), 500 );
		reports.record( request( "/a", 200 ), 500 );
		reports.record( request( "/b", 500 ), 500 );
		reports.record( request( "/c", 404 ), 500 );
		Map<String, Object>	snap	= reports.snapshot( false );
		Map<String, Object>	s		= ( Map<String, Object> ) snap.get( "session" );
		assertThat( s.get( "requests" ) ).isEqualTo( 4L );
		assertThat( s.get( "errors" ) ).isEqualTo( 1L );
		assertThat( ( ( Map<String, Object> ) s.get( "status" ) ).get( "2xx" ) ).isEqualTo( 2L );
		assertThat( ( ( Map<String, Object> ) s.get( "status" ) ).get( "4xx" ) ).isEqualTo( 1L );
		assertThat( snap.containsKey( "lifetime" ) ).isFalse();
		assertThat( ( ( java.util.List<Map<String, Object>> ) snap.get( "busiestUrls" ) ).get( 0 ).get( "url" ) ).isEqualTo( "GET /a" );
		assertThat( ( Long ) s.get( "p95" ) ).isAtLeast( 10L );
	}

	@Test
	@DisplayName( "lifetime totals add up across a save and a load" )
	@SuppressWarnings( "unchecked" )
	void lifetime() {
		Reports first = new Reports();
		first.record( request( "/a", 200 ), 500 );
		first.record( request( "/a", 500 ), 500 );
		Map<String, Object>	saved	= Plain.map( Plain.parse( ortus.boxlang.modules.bxlens.util.Json.write( first.toPersist() ) ) );
		Reports				second	= new Reports();
		second.keepMinutes( 600 );
		second.load( saved );
		second.record( request( "/a", 200 ), 500 );
		Map<String, Object> life = ( Map<String, Object> ) second.snapshot( true ).get( "lifetime" );
		assertThat( life.get( "requests" ) ).isEqualTo( 3L );
		assertThat( life.get( "errors" ) ).isEqualTo( 1L );
		assertThat( life.get( "runs" ) ).isEqualTo( 2L );
	}

}
