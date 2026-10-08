/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.Query;

/**
 * Turns arbitrary BoxLang and Java values into JSON-safe plain data: maps, lists, strings, numbers and booleans.
 * Applies depth and size caps, truncates long strings, and masks the value of any key on the redact list.
 */
public final class Sanitizer {

	private final LensConfig config;

	public Sanitizer( LensConfig config ) {
		this.config = config;
	}

	/**
	 * Clean a value with the configured limits.
	 */
	public Object clean( Object value ) {
		return clean( value, 0, new java.util.IdentityHashMap<>() );
	}

	/**
	 * Clean a value that sits under a key, masking it when the key is on the redact list.
	 */
	public Object cleanKeyed( String key, Object value ) {
		if ( config.shouldRedact( key ) ) {
			return config.redactMask;
		}
		return clean( value );
	}

	/**
	 * Truncate a string to the configured maximum.
	 */
	public String text( Object value ) {
		if ( value == null ) {
			return "";
		}
		String s = value.toString();
		return s.length() > config.maxString ? s.substring( 0, config.maxString ) + "... [" + ( s.length() - config.maxString ) + " more chars]" : s;
	}

	@SuppressWarnings( "unchecked" )
	private Object clean( Object value, int depth, Map<Object, Boolean> seen ) {
		if ( value == null ) {
			return null;
		}
		if ( value instanceof Boolean || value instanceof Number ) {
			return value;
		}
		if ( value instanceof CharSequence ) {
			return text( value );
		}
		if ( value instanceof Key k ) {
			return k.getName();
		}
		if ( value instanceof Date || value instanceof TemporalAccessor ) {
			return value.toString();
		}
		if ( value instanceof Function ) {
			return "[function]";
		}
		if ( value instanceof Throwable t ) {
			return t.getClass().getSimpleName() + ": " + text( t.getMessage() );
		}
		if ( value instanceof Query q ) {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put( "type", "query" );
			out.put( "rows", q.size() );
			List<String> cols = new ArrayList<>();
			q.getColumns().keySet().forEach( c -> cols.add( c.getName() ) );
			out.put( "columns", cols );
			return out;
		}
		if ( depth >= config.maxDepth ) {
			return "[max depth]";
		}
		if ( value instanceof Map<?, ?> || value instanceof Collection<?> ) {
			if ( seen.put( value, Boolean.TRUE ) != null ) {
				return "[circular]";
			}
			try {
				if ( value instanceof Map<?, ?> m ) {
					Map<String, Object>	out		= new LinkedHashMap<>();
					int					count	= 0;
					for ( Map.Entry<?, ?> e : ( ( Map<Object, Object> ) m ).entrySet() ) {
						if ( ++count > config.maxItems ) {
							out.put( "...", ( m.size() - config.maxItems ) + " more" );
							break;
						}
						String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
						out.put( k, config.shouldRedact( k ) ? config.redactMask : clean( e.getValue(), depth + 1, seen ) );
					}
					return out;
				}
				Collection<?>	col		= ( Collection<?> ) value;
				List<Object>	out		= new ArrayList<>();
				int				count	= 0;
				for ( Object o : col ) {
					if ( ++count > config.maxItems ) {
						out.add( "... " + ( col.size() - config.maxItems ) + " more" );
						break;
					}
					out.add( clean( o, depth + 1, seen ) );
				}
				return out;
			} finally {
				seen.remove( value );
			}
		}
		return text( value.toString() );
	}

}
