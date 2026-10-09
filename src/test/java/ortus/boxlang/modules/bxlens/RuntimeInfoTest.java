/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.BoxRuntime;

public class RuntimeInfoTest {

	@BeforeAll
	static void boot() {
		BoxRuntime.getInstance( true );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "the snapshot has the versions, the system, the heap, cache names and a short list of settings, and is cached" )
	void snapshot() {
		RuntimeInfo			info	= new RuntimeInfo();
		Map<String, Object>	s		= info.snapshot();
		assertThat( ( ( Map<String, Object> ) s.get( "boxlang" ) ).get( "version" ).toString() ).isNotEmpty();
		assertThat( ( ( Map<String, Object> ) s.get( "java" ) ).get( "version" ) ).isEqualTo( System.getProperty( "java.version" ) );
		assertThat( s.get( "os" ).toString() ).isNotEmpty();
		assertThat( ( ( Map<String, Object> ) s.get( "heap" ) ).get( "usedMb" ) ).isNotNull();
		assertThat( s.get( "caches" ) ).isInstanceOf( List.class );
		List<Map<String, Object>> settings = ( List<Map<String, Object>> ) s.get( "settings" );
		assertThat( settings ).isNotEmpty();
		assertThat( settings.stream().map( m -> m.get( "label" ) ).toList() ).contains( "Time zone" );
		// Session settings are named "session" but are not secrets
		for ( Map<String, Object> st : settings ) {
			assertThat( st.get( "value" ) ).isNotEqualTo( "[hidden]" );
		}
		// Cached: the same object comes back until the few seconds are over
		assertThat( info.snapshot() ).isSameInstanceAs( s );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "the configuration is grouped by area, every key exists, and a viewer does not get the sensitive areas" )
	void configuration() {
		RuntimeInfo					info		= new RuntimeInfo();
		Map<String, Object>			admin		= info.configuration( true );
		Map<String, Object>			viewer		= info.configuration( false );
		List<Map<String, Object>>	ag			= ( List<Map<String, Object>> ) admin.get( "groups" );
		List<Map<String, Object>>	vg			= ( List<Map<String, Object>> ) viewer.get( "groups" );
		List<Object>				adminNames	= ag.stream().map( g -> g.get( "name" ) ).toList();
		List<Object>				viewerNames	= vg.stream().map( g -> g.get( "name" ) ).toList();
		assertThat( adminNames ).containsAtLeast( "Runtime", "Requests and sessions", "Paths" );
		assertThat( viewerNames ).contains( "Runtime" );
		assertThat( viewerNames ).doesNotContain( "Paths" );
		assertThat( viewerNames ).doesNotContain( "Security" );
		assertThat( viewerNames ).doesNotContain( "Logging" );
		assertThat( viewer.get( "adminOnlyHidden" ) ).isEqualTo( true );
		Map<String, Object>			runtime	= ag.stream().filter( g -> "Runtime".equals( g.get( "name" ) ) ).findFirst().get();
		List<Map<String, Object>>	entries	= ( List<Map<String, Object>> ) runtime.get( "entries" );
		assertThat( entries.stream().map( e -> e.get( "key" ) ).toList() ).contains( "debugMode" );
		for ( Map<String, Object> e : entries ) {
			assertThat( e.containsKey( "source" ) ).isTrue();
		}
		// Nothing is invented: a key the runtime does not have is not listed
		assertThat( entries.stream().map( e -> e.get( "key" ) ).toList() ).doesNotContain( "madeUpKey" );
	}

	@Test
	@DisplayName( "values are walked with the shared secret matcher" )
	void masks() {
		Map<String, Object> out = ( Map<String, Object> ) RuntimeInfo.walk( Map.of( "user", "bob", "dbPassword", "x", "url", "https://u:p@h/x" ), "root", 0 );
		assertThat( out.get( "dbPassword" ) ).isEqualTo( "[hidden]" );
		assertThat( out.get( "user" ) ).isEqualTo( "bob" );
		assertThat( out.get( "url" ).toString() ).doesNotContain( "u:p" );
		assertThat( RuntimeInfo.walk( "memory", "sessionStorage", 0 ) ).isEqualTo( "memory" );
		assertThat( RuntimeInfo.walk( "x", "apiKey", 1 ) ).isEqualTo( "[hidden]" );
	}

}
