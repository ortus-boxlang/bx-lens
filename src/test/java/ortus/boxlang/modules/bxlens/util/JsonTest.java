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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JsonTest {

	@Test
	@DisplayName( "Writes maps, lists, numbers, booleans and null" )
	public void basics() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "a", 1 );
		m.put( "b", List.of( true, "x", 2.5 ) );
		m.put( "c", null );
		assertThat( Json.write( m ) ).isEqualTo( "{\"a\":1,\"b\":[true,\"x\",2.5],\"c\":null}" );
	}

	@Test
	@DisplayName( "Output is safe inside an HTML script element" )
	public void scriptSafe() {
		String json = Json.write( Map.of( "x", "</script><script>alert(1)</script> & \u2028" ) );
		assertThat( json ).doesNotContain( "<" );
		assertThat( json ).doesNotContain( ">" );
		assertThat( json ).doesNotContain( "&" );
		assertThat( json ).doesNotContain( "\u2028" );
		assertThat( json ).contains( "\\u003c/script\\u003e" );
	}

	@Test
	@DisplayName( "Escapes quotes, backslashes and control characters" )
	public void escapes() {
		assertThat( Json.write( "a\"b\\c\n\t\u0001" ) ).isEqualTo( "\"a\\\"b\\\\c\\n\\t\\u0001\"" );
	}

	@Test
	@DisplayName( "NaN and infinity become null" )
	public void nonFinite() {
		assertThat( Json.write( List.of( Double.NaN, Double.POSITIVE_INFINITY ) ) ).isEqualTo( "[null,null]" );
	}

}
