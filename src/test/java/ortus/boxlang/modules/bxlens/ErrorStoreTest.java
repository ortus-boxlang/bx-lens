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

	@Test
	@DisplayName( "groups and samples carry the server of the request, and the list names the server" )
	@SuppressWarnings( "unchecked" )
	void serverIdentity() {
		ErrorStore store = new ErrorStore();
		store.identify( () -> Map.of( "id", "abc12345", "host", "web-1" ) );
		LensRequest r = request( "/a", 500, "boom" );
		r.serverId		= "abc12345";
		r.serverHost	= "web-1";
		r.serverIp		= "10.0.0.7";
		store.record( r, LensConfig.defaults() );
		Map<String, Object> list = store.list();
		assertThat( ( Map<String, Object> ) list.get( "server" ) ).containsEntry( "id", "abc12345" );
		Map<String, Object> group = ( ( List<Map<String, Object>> ) list.get( "groups" ) ).get( 0 );
		assertThat( group.get( "serverId" ) ).isEqualTo( "abc12345" );
		Map<String, Object> sample = ( Map<String, Object> ) ( ( List<?> ) store.get( ( String ) group.get( "id" ) ).get( "samples" ) ).get( 0 );
		assertThat( sample.get( "serverId" ) ).isEqualTo( "abc12345" );
		assertThat( sample.get( "serverHost" ) ).isEqualTo( "web-1" );
		assertThat( sample.get( "serverIp" ) ).isEqualTo( "10.0.0.7" );
	}

	@Test
	@DisplayName( "errors.json holds a top level server and the groups, and an older plain list still loads" )
	void fileFormat() {
		ErrorStore	store	= new ErrorStore();
		LensRequest	r		= request( "/a", 500, "boom" );
		r.serverId = "abc12345";
		store.record( r, LensConfig.defaults() );
		Map<String, Object>	file	= ErrorStore.fileOf( Map.of( "id", "abc12345" ), store.toPersist() );
		Object				parsed	= Plain.parse( Json.write( file ) );
		assertThat( Plain.map( Plain.map( parsed ).get( "server" ) ) ).containsEntry( "id", "abc12345" );
		assertThat( ErrorStore.groupsOf( parsed ) ).hasSize( 1 );
		// The older format was the list itself
		Object legacy = Plain.parse( Json.write( Plain.map( parsed ).get( "groups" ) ) );
		assertThat( ErrorStore.groupsOf( legacy ) ).hasSize( 1 );
		ErrorStore loaded = new ErrorStore();
		loaded.load( ErrorStore.groupsOf( parsed ), 0 );
		assertThat( loaded.size() ).isEqualTo( 1 );
	}

}
