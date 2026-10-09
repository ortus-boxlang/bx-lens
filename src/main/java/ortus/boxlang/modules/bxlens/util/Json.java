/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
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

	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private static void string( StringBuilder sb, CharSequence s ) {
		sb.append( '"' );
		int	n		= s.length();
		int	copied	= 0;
		for ( int i = 0; i < n; i++ ) {
			char c = s.charAt( i );
			if ( c >= 0x20 && c != '"' && c != '\\' && c != '<' && c != '>' && c != '&' && c != '\u2028' && c != '\u2029' ) {
				continue;
			}
			sb.append( s, copied, i );
			copied = i + 1;
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
				default :
					sb.append( "\\u" ).append( HEX[ c >> 12 & 15 ] ).append( HEX[ c >> 8 & 15 ] ).append( HEX[ c >> 4 & 15 ] ).append( HEX[ c & 15 ] );
			}
		}
		sb.append( s, copied, n ).append( '"' );
	}

}
