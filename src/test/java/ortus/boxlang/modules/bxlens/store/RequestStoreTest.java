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

}
