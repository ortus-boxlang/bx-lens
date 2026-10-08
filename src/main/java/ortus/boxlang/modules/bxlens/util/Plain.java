/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * Reads JSON into plain Java maps, lists, strings, numbers and booleans, and writes a file in one atomic step. Used by the stores that
 * keep their state on disk.
 */
public final class Plain {

	private Plain() {
	}

	/**
	 * Parse JSON text into plain Java values: Map with String keys, List, String, Number, Boolean or null.
	 */
	public static Object parse( String json ) {
		return plain( JSONUtil.fromJSON( json ) );
	}

	public static Object plain( Object v ) {
		if ( v instanceof Map<?, ?> m ) {
			Map<String, Object> out = new LinkedHashMap<>();
			for ( Map.Entry<?, ?> e : m.entrySet() ) {
				out.put( e.getKey() instanceof Key k ? k.getName() : String.valueOf( e.getKey() ), plain( e.getValue() ) );
			}
			return out;
		}
		if ( v instanceof Collection<?> c ) {
			List<Object> out = new ArrayList<>();
			for ( Object o : c ) {
				out.add( plain( o ) );
			}
			return out;
		}
		return v;
	}

	/**
	 * Write text to a file by writing a temp file next to it and moving it into place, so a reader never sees half a file.
	 */
	public static void writeAtomic( Path file, String text ) throws IOException {
		Files.createDirectories( file.toAbsolutePath().getParent() );
		Path tmp = file.resolveSibling( file.getFileName() + ".tmp" );
		Files.writeString( tmp, text, StandardCharsets.UTF_8 );
		try {
			Files.move( tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
		} catch ( java.nio.file.AtomicMoveNotSupportedException e ) {
			Files.move( tmp, file, StandardCopyOption.REPLACE_EXISTING );
		}
	}

	@SuppressWarnings( "unchecked" )
	public static Map<String, Object> map( Object v ) {
		return v instanceof Map ? ( Map<String, Object> ) v : new LinkedHashMap<>();
	}

	@SuppressWarnings( "unchecked" )
	public static List<Object> list( Object v ) {
		return v instanceof List ? ( List<Object> ) v : new ArrayList<>();
	}

	public static long num( Object v, long def ) {
		return v instanceof Number n ? n.longValue() : def;
	}

	public static String str( Object v ) {
		return v == null ? "" : v.toString();
	}

}
