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
package ortus.boxlang.modules.bxlens.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.LensConfig;

public class SanitizerTest {

	private Sanitizer sanitizer( Map<String, Object> limits ) {
		return new Sanitizer( new LensConfig( Map.of( "limits", limits ) ) );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "Values under redacted keys are masked at any depth" )
	public void redactsNested() {
		Map<String, Object> in = new LinkedHashMap<>();
		in.put( "user", "luis" );
		in.put( "password", "hunter2" );
		in.put( "profile", Map.of( "apiToken", "abc", "city", "Miami" ) );
		Map<String, Object> out = ( Map<String, Object> ) new Sanitizer( LensConfig.defaults() ).clean( in );
		assertThat( out.get( "user" ) ).isEqualTo( "luis" );
		assertThat( out.get( "password" ) ).isEqualTo( "[redacted]" );
		Map<String, Object> profile = ( Map<String, Object> ) out.get( "profile" );
		assertThat( profile.get( "apiToken" ) ).isEqualTo( "[redacted]" );
		assertThat( profile.get( "city" ) ).isEqualTo( "Miami" );
	}

	@Test
	@DisplayName( "Long strings are truncated" )
	public void truncates() {
		String out = ( String ) sanitizer( Map.of( "maxString", 10 ) ).clean( "0123456789ABCDEFGH" );
		assertThat( out ).startsWith( "0123456789" );
		assertThat( out ).contains( "8 more chars" );
	}

	@Test
	@DisplayName( "Depth and item limits are enforced" )
	public void limits() {
		Object deep = Map.of( "a", Map.of( "b", Map.of( "c", "x" ) ) );
		assertThat( sanitizer( Map.of( "maxDepth", 2 ) ).clean( deep ).toString() ).contains( "[max depth]" );
		List<Integer> many = new ArrayList<>();
		for ( int i = 0; i < 20; i++ ) {
			many.add( i );
		}
		List<?> out = ( List<?> ) sanitizer( Map.of( "maxItems", 5 ) ).clean( many );
		assertThat( out ).hasSize( 6 );
		assertThat( out.get( 5 ).toString() ).contains( "15 more" );
	}

	@Test
	@DisplayName( "Circular references do not recurse forever" )
	public void circular() {
		Map<String, Object> a = new LinkedHashMap<>();
		a.put( "self", a );
		assertThat( new Sanitizer( LensConfig.defaults() ).clean( a ).toString() ).contains( "[circular]" );
	}

	@Test
	@DisplayName( "Null, numbers and booleans pass through" )
	public void primitives() {
		Sanitizer s = new Sanitizer( LensConfig.defaults() );
		assertThat( s.clean( null ) ).isNull();
		assertThat( s.clean( 42 ) ).isEqualTo( 42 );
		assertThat( s.clean( true ) ).isEqualTo( true );
	}

}
