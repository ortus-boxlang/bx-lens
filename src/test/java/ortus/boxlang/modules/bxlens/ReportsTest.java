/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
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

	@Test
	@DisplayName( "URLs that differ only by an id are one entry, and the table is bounded" )
	@SuppressWarnings( "unchecked" )
	void collapsesPaths() {
		Reports reports = new Reports();
		for ( int i = 0; i < 500; i++ ) {
			reports.record( request( "/orders/" + i, 200 ), 500 );
		}
		reports.record( request( "/orders/123e4567-e89b-12d3-a456-426614174000", 200 ), 500 );
		java.util.List<Map<String, Object>> busiest = ( java.util.List<Map<String, Object>> ) reports.snapshot( false ).get( "busiestUrls" );
		assertThat( busiest ).hasSize( 1 );
		assertThat( busiest.get( 0 ).get( "url" ) ).isEqualTo( "GET /orders/:id" );
		assertThat( busiest.get( 0 ).get( "count" ) ).isEqualTo( 501L );
		Reports many = new Reports();
		for ( int i = 0; i < 1000; i++ ) {
			many.record( request( "/page" + i + "/x", 200 ), 500 );
		}
		assertThat( ( ( java.util.List<?> ) many.snapshot( false ).get( "busiestUrls" ) ) ).hasSize( 10 );
	}

}
