/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.store;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RequestStoreTest {

	private RequestStore.Entry entry( String id ) {
		return new RequestStore.Entry( id, Map.of( "id", id ), "{\"id\":\"" + id + "\"}" );
	}

	@Test
	@DisplayName( "The oldest request is recycled when the buffer is full" )
	public void recycles() {
		RequestStore store = new RequestStore( 3 );
		for ( String id : new String[] { "a", "b", "c", "d", "e" } ) {
			store.add( entry( id ) );
		}
		assertThat( store.size() ).isEqualTo( 3 );
		assertThat( store.get( "a" ) ).isNull();
		assertThat( store.get( "b" ) ).isNull();
		assertThat( store.get( "e" ) ).isNotNull();
	}

	@Test
	@DisplayName( "Summaries are newest first" )
	public void newestFirst() {
		RequestStore store = new RequestStore( 5 );
		store.add( entry( "a" ) );
		store.add( entry( "b" ) );
		assertThat( store.summaries().get( 0 ).get( "id" ) ).isEqualTo( "b" );
		assertThat( store.summaries().get( 1 ).get( "id" ) ).isEqualTo( "a" );
	}

	@Test
	@DisplayName( "Clear empties the history" )
	public void clears() {
		RequestStore store = new RequestStore( 5 );
		store.add( entry( "a" ) );
		store.clear();
		assertThat( store.size() ).isEqualTo( 0 );
		assertThat( store.summaries() ).isEmpty();
	}

	@Test
	@DisplayName( "Capacity is at least one" )
	public void minimumCapacity() {
		RequestStore store = new RequestStore( 0 );
		store.add( entry( "a" ) );
		store.add( entry( "b" ) );
		assertThat( store.size() ).isEqualTo( 1 );
		assertThat( store.get( "b" ) ).isNotNull();
	}

	@Test
	@DisplayName( "The store counts the distinct servers it has seen, so the console shows a Server column only for more than one" )
	public void serverCount() {
		RequestStore store = new RequestStore( 10 );
		store.add( entry( "a" ).server( "s1" ) );
		store.add( entry( "b" ).server( "s1" ) );
		assertThat( store.serverCount() ).isEqualTo( 1 );
		store.add( entry( "c" ).server( "s2" ) );
		assertThat( store.serverCount() ).isEqualTo( 2 );
		store.add( entry( "d" ) );
		assertThat( store.serverCount() ).isEqualTo( 2 );
		store.clear();
		assertThat( store.serverCount() ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "The set of server ids is capped" )
	public void serverCap() {
		RequestStore store = new RequestStore( 5 );
		for ( int i = 0; i < RequestStore.MAX_SERVERS + 20; i++ ) {
			store.add( entry( "r" + i ).server( "server" + i ) );
		}
		assertThat( store.serverCount() ).isEqualTo( RequestStore.MAX_SERVERS );
	}
}
