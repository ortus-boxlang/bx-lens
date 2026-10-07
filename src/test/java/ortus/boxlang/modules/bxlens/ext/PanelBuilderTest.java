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
package ortus.boxlang.modules.bxlens.ext;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PanelBuilderTest {

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "A table panel keeps its columns, rows, badge and order" )
	public void table() {
		LensPanelBuilder	p	= new LensPanelBuilder( "orm", "ORM", "table" ).columns( List.of( "Entity", "ms" ) ).rows( List.of( List.of( "User", 3 ) ) )
		    .badge( 1, "warn" ).order( 40 );
		Map<String, Object>	m	= p.toMap();
		assertThat( m.get( "id" ) ).isEqualTo( "orm" );
		assertThat( m.get( "renderer" ) ).isEqualTo( "table" );
		assertThat( m.get( "order" ) ).isEqualTo( 40 );
		assertThat( ( ( Map<String, Object> ) m.get( "badge" ) ).get( "severity" ) ).isEqualTo( "warn" );
		assertThat( ( ( Map<String, Object> ) m.get( "content" ) ).keySet() ).containsExactly( "columns", "rows" );
	}

	@Test
	@DisplayName( "Choosing a content type switches the renderer" )
	public void renderers() {
		assertThat( new LensPanelBuilder( "a", "A", "table" ).kv( Map.of( "k", "v" ) ).toMap().get( "renderer" ) ).isEqualTo( "kv" );
		assertThat( new LensPanelBuilder( "a", "A", "table" ).json( Map.of( "k", "v" ) ).toMap().get( "renderer" ) ).isEqualTo( "json" );
		assertThat( new LensPanelBuilder( "a", "A", "table" ).text( "hello" ).toMap().get( "renderer" ) ).isEqualTo( "text" );
		assertThat( new LensPanelBuilder( "a", "A", "table" ).tree( List.of() ).toMap().get( "renderer" ) ).isEqualTo( "tree" );
	}

	@Test
	@DisplayName( "Values are redacted like built in data" )
	public void redacts() {
		Object content = new LensPanelBuilder( "a", "A", "kv" ).kv( Map.of( "apiKey", "123", "name", "x" ) ).toMap().get( "content" );
		assertThat( content.toString() ).contains( "[redacted]" );
		assertThat( content.toString() ).doesNotContain( "123" );
	}

	@Test
	@DisplayName( "Issues reported by a panel are kept with a normalized severity" )
	public void issues() {
		LensPanelBuilder p = new LensPanelBuilder( "a", "A", "table" ).issue( "CRIT", "Bad", "detail", "x.bx", 3 ).issue( "whatever", "Hmm", null, null, null );
		assertThat( p.getIssues() ).hasSize( 2 );
		assertThat( p.getIssues().get( 0 ).get( "severity" ) ).isEqualTo( "crit" );
		assertThat( p.getIssues().get( 1 ).get( "severity" ) ).isEqualTo( "warn" );
	}

	@Test
	@DisplayName( "The registry keeps declared panels in order" )
	public void registry() {
		LensRegistry reg = new LensRegistry().panel( "a", "A" ).panel( "b", "B", "cpu", 20, "kv" );
		assertThat( reg.list() ).hasSize( 2 );
		assertThat( reg.list().get( 1 ).get( "icon" ) ).isEqualTo( "cpu" );
		reg.clear();
		assertThat( reg.list() ).isEmpty();
	}

}
