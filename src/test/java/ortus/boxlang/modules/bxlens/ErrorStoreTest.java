/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.modules.bxlens.util.Plain;

public class ErrorStoreTest {

	private static LensRequest request( String uri, int status, String message ) {
		LensRequest r = new LensRequest();
		r.method	= "GET";
		r.uri		= uri;
		r.status	= status;
		Map<String, Object> e = new LinkedHashMap<>();
		e.put( "type", "Custom" );
		e.put( "message", message );
		e.put( "origin", "uncaught" );
		e.put( "file", "/app/a.bxm" );
		e.put( "line", 7 );
		Map<String, Object> f = new LinkedHashMap<>();
		f.put( "file", "/app/a.bxm" );
		f.put( "line", 7 );
		e.put( "frames", List.of( f ) );
		r.exceptions.add( e );
		return r;
	}

	@Test
	@DisplayName( "the same error with other numbers and quoted values is one group" )
	@SuppressWarnings( "unchecked" )
	void groups() {
		ErrorStore store = new ErrorStore();
		store.record( request( "/a", 500, "Order 123 not found for 'bob'" ), LensConfig.defaults() );
		store.record( request( "/b", 500, "Order 987 not found for 'alice'" ), LensConfig.defaults() );
		store.record( request( "/a", 500, "Something else entirely" ), LensConfig.defaults() );
		Map<String, Object> list = store.list();
		assertThat( ( List<?> ) list.get( "groups" ) ).hasSize( 2 );
		assertThat( list.get( "occurrences" ) ).isEqualTo( 3L );
		Map<String, Object>	first	= ( ( List<Map<String, Object>> ) list.get( "groups" ) ).stream().filter( g -> ( ( Long ) g.get( "count" ) ) == 2 )
		    .findFirst().get();
		Map<String, Object>	detail	= store.get( ( String ) first.get( "id" ) );
		assertThat( ( List<?> ) detail.get( "samples" ) ).hasSize( 2 );
		assertThat( ( Map<String, Object> ) detail.get( "urls" ) ).containsKey( "GET /a" );
	}

	@Test
	@DisplayName( "a failed status without an exception is still an error, a good request is not" )
	void statusOnly() {
		ErrorStore	store	= new ErrorStore();
		LensRequest	ok		= new LensRequest();
		ok.status = 200;
		store.record( ok, LensConfig.defaults() );
		LensRequest bad = new LensRequest();
		bad.status	= 503;
		bad.uri		= "/x";
		store.record( bad, LensConfig.defaults() );
		assertThat( store.size() ).isEqualTo( 1 );
	}

	@Test
	@DisplayName( "secret query parameters are hidden and samples are capped" )
	@SuppressWarnings( "unchecked" )
	void redactsAndCaps() {
		assertThat( ErrorStore.redactQuery( "id=5&password=hunter2&token=abc", LensConfig.defaults() ) )
		    .isEqualTo( "id=5&password=[redacted]&token=[redacted]" );
		ErrorStore store = new ErrorStore();
		for ( int i = 0; i < 9; i++ ) {
			store.record( request( "/a", 500, "same" ), LensConfig.defaults() );
		}
		String id = ( String ) ( ( Map<String, Object> ) ( ( List<?> ) store.list().get( "groups" ) ).get( 0 ) ).get( "id" );
		assertThat( ( List<?> ) store.get( id ).get( "samples" ) ).hasSize( ErrorStore.MAX_SAMPLES );
	}

	@Test
	@DisplayName( "groups survive a save and a load, and old ones are dropped" )
	void persists() {
		ErrorStore store = new ErrorStore();
		store.record( request( "/a", 500, "boom" ), LensConfig.defaults() );
		String		saved	= Json.write( store.toPersist() );
		ErrorStore	loaded	= new ErrorStore();
		loaded.load( Plain.list( Plain.parse( saved ) ), 0 );
		assertThat( loaded.size() ).isEqualTo( 1 );
		ErrorStore stale = new ErrorStore();
		stale.load( Plain.list( Plain.parse( saved ) ), System.currentTimeMillis() + 60_000 );
		assertThat( stale.size() ).isEqualTo( 0 );
		assertThat( new ArrayList<>( store.toPersist() ) ).hasSize( 1 );
	}

}
