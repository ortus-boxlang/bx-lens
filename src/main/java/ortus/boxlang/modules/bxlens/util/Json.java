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

import java.util.Collection;
import java.util.Map;

/**
 * Minimal JSON writer for the plain data shapes Lens produces (maps, lists, strings, numbers, booleans, null).
 * The output is safe to embed inside an HTML script block: angle brackets, ampersands and the JS line separators are written as unicode escapes.
 */
public final class Json {

	private Json() {
	}

	/**
	 * Serialize a value to JSON.
	 *
	 * @param value Map, Collection, array of Object, CharSequence, Number, Boolean or null
	 *
	 * @return the JSON string
	 */
	public static String write( Object value ) {
		StringBuilder sb = new StringBuilder( 1024 );
		write( sb, value );
		return sb.toString();
	}

	@SuppressWarnings( "unchecked" )
	private static void write( StringBuilder sb, Object value ) {
		if ( value == null ) {
			sb.append( "null" );
		} else if ( value instanceof CharSequence cs ) {
			string( sb, cs );
		} else if ( value instanceof Boolean ) {
			sb.append( value );
		} else if ( value instanceof Double d ) {
			sb.append( d.isNaN() || d.isInfinite() ? "null" : d.toString() );
		} else if ( value instanceof Float f ) {
			sb.append( f.isNaN() || f.isInfinite() ? "null" : f.toString() );
		} else if ( value instanceof Number ) {
			sb.append( value );
		} else if ( value instanceof Map<?, ?> map ) {
			sb.append( '{' );
			boolean first = true;
			for ( Map.Entry<?, ?> e : ( ( Map<Object, Object> ) map ).entrySet() ) {
				if ( !first ) {
					sb.append( ',' );
				}
				first = false;
				string( sb, String.valueOf( e.getKey() ) );
				sb.append( ':' );
				write( sb, e.getValue() );
			}
			sb.append( '}' );
		} else if ( value instanceof Collection<?> col ) {
			sb.append( '[' );
			boolean first = true;
			for ( Object o : col ) {
				if ( !first ) {
					sb.append( ',' );
				}
				first = false;
				write( sb, o );
			}
			sb.append( ']' );
		} else if ( value instanceof Object[] arr ) {
			sb.append( '[' );
			for ( int i = 0; i < arr.length; i++ ) {
				if ( i > 0 ) {
					sb.append( ',' );
				}
				write( sb, arr[ i ] );
			}
			sb.append( ']' );
		} else {
			string( sb, String.valueOf( value ) );
		}
	}

	private static void string( StringBuilder sb, CharSequence s ) {
		sb.append( '"' );
		for ( int i = 0; i < s.length(); i++ ) {
			char c = s.charAt( i );
			switch ( c ) {
				case '"' :
					sb.append( "\\\"" );
					break;
				case '\\' :
					sb.append( "\\\\" );
					break;
				case '\n' :
					sb.append( "\\n" );
					break;
				case '\r' :
					sb.append( "\\r" );
					break;
				case '\t' :
					sb.append( "\\t" );
					break;
				case '<' :
				case '>' :
				case '&' :
				case ' ' :
				case ' ' :
					sb.append( String.format( "\\u%04x", ( int ) c ) );
					break;
				default :
					if ( c < 0x20 ) {
						sb.append( String.format( "\\u%04x", ( int ) c ) );
					} else {
						sb.append( c );
					}
			}
		}
		sb.append( '"' );
	}

}
