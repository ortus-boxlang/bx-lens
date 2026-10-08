/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * The bar layout a developer designs in the console: which tabs show and in what order. Stored as a small JSON file in the BoxLang home
 * (<code>config/bxlens-layout.json</code>) so it survives restarts. Not a database: one list, written on save.
 */
public final class LayoutStore {

	private static final int					MAX_ENTRIES	= 100;

	private final Path							file;
	private volatile List<Map<String, Object>>	tabs		= List.of();

	/**
	 * @param file where the layout is kept, may be null to keep it in memory only
	 */
	public LayoutStore( Path file ) {
		this.file = file;
		load();
	}

	/**
	 * The saved layout: entries with <code>id</code> and <code>visible</code>, in order. Empty means the default order with everything shown.
	 */
	public List<Map<String, Object>> get() {
		return tabs;
	}

	/**
	 * Save a layout. Entries with an unknown id, a bad id or a repeated id are dropped.
	 *
	 * @param entries  id and visible per tab
	 * @param validIds the ids that exist
	 */
	public synchronized void save( Collection<?> entries, Set<String> validIds ) throws IOException {
		List<Map<String, Object>>	clean	= new ArrayList<>();
		Set<String>					seen	= new LinkedHashSet<>();
		for ( Object o : entries ) {
			if ( clean.size() >= MAX_ENTRIES || ! ( o instanceof Map<?, ?> m ) ) {
				continue;
			}
			String id = valueOf( m, "id" );
			if ( id == null || !id.matches( "[A-Za-z0-9_.\\-]{1,64}" ) || !validIds.contains( id ) || !seen.add( id ) ) {
				continue;
			}
			Object				vis	= value( m, "visible" );
			Map<String, Object>	e	= new LinkedHashMap<>();
			e.put( "id", id );
			e.put( "visible", vis == null || ! ( "false".equalsIgnoreCase( vis.toString() ) ) );
			clean.add( e );
		}
		write( clean );
		this.tabs = List.copyOf( clean );
	}

	/**
	 * Forget the layout and go back to the default.
	 */
	public synchronized void reset() throws IOException {
		write( List.of() );
		this.tabs = List.of();
	}

	private void write( List<Map<String, Object>> list ) throws IOException {
		if ( file == null ) {
			return;
		}
		Files.createDirectories( file.getParent() );
		Path tmp = file.resolveSibling( file.getFileName() + ".tmp" );
		Files.writeString( tmp, Json.write( Map.of( "tabs", list ) ), StandardCharsets.UTF_8 );
		Files.move( tmp, file, StandardCopyOption.REPLACE_EXISTING );
	}

	private void load() {
		try {
			if ( file == null || !Files.exists( file ) ) {
				return;
			}
			Object parsed = JSONUtil.fromJSON( Files.readString( file, StandardCharsets.UTF_8 ) );
			if ( parsed instanceof Map<?, ?> m && value( m, "tabs" ) instanceof Collection<?> c ) {
				List<Map<String, Object>> out = new ArrayList<>();
				for ( Object o : c ) {
					if ( o instanceof Map<?, ?> em ) {
						String id = valueOf( em, "id" );
						if ( id != null && id.matches( "[A-Za-z0-9_.\\-]{1,64}" ) ) {
							Map<String, Object> e = new LinkedHashMap<>();
							e.put( "id", id );
							Object vis = value( em, "visible" );
							e.put( "visible", vis == null || !"false".equalsIgnoreCase( vis.toString() ) );
							out.add( e );
						}
					}
				}
				this.tabs = List.copyOf( out );
			}
		} catch ( Throwable t ) {
			// A damaged file means the default layout
		}
	}

	private static Object value( Map<?, ?> m, String field ) {
		for ( Map.Entry<?, ?> e : m.entrySet() ) {
			String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
			if ( k.equalsIgnoreCase( field ) ) {
				return e.getValue();
			}
		}
		return null;
	}

	private static String valueOf( Map<?, ?> m, String field ) {
		Object v = value( m, field );
		return v == null ? null : v.toString();
	}

}
