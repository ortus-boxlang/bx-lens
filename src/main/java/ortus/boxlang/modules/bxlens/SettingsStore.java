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
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * The settings an admin changed from the console. A flat map of dotted keys to values, kept in one small JSON file so the changes survive a
 * restart. Values are checked against the {@link SettingsRegistry} when loaded, so a hand-edited or damaged file can never switch on
 * something the console may not change. The file holds no secrets: the password settings are not changeable here.
 */
public final class SettingsStore {

	private final Path						file;
	private final SettingsRegistry			registry;
	private volatile Map<String, Object>	overrides	= Map.of();

	/**
	 * @param file     where overrides are kept, may be null to keep them in memory only
	 * @param registry decides which keys and values are valid
	 */
	public SettingsStore( Path file, SettingsRegistry registry ) {
		this.file		= file;
		this.registry	= registry;
		load();
	}

	public Path file() {
		return file;
	}

	/**
	 * The current overrides, as an immutable copy.
	 */
	public Map<String, Object> get() {
		return overrides;
	}

	/**
	 * Replace the overrides. Every key and value must be valid or nothing is saved.
	 *
	 * @throws IllegalArgumentException when a value is not valid
	 */
	public synchronized void set( Map<String, Object> next ) throws IOException {
		Map<String, Object> clean = new LinkedHashMap<>();
		for ( Map.Entry<String, Object> e : next.entrySet() ) {
			clean.put( registry.get( e.getKey() ).key(), registry.coerce( e.getKey(), e.getValue() ) );
		}
		write( clean );
		this.overrides = Map.copyOf( clean );
	}

	private void write( Map<String, Object> map ) throws IOException {
		if ( file == null ) {
			return;
		}
		Files.createDirectories( file.toAbsolutePath().getParent() );
		Path tmp = file.resolveSibling( file.getFileName() + ".tmp" );
		Files.writeString( tmp, Json.write( Map.of( "overrides", map ) ), StandardCharsets.UTF_8 );
		Files.move( tmp, file, StandardCopyOption.REPLACE_EXISTING );
	}

	/**
	 * Read the file. A bad entry is skipped and reported; a bad file means no overrides. Startup is never blocked.
	 *
	 * @return the number of entries that were skipped
	 */
	private int load() {
		int skipped = 0;
		try {
			if ( file == null || !Files.exists( file ) ) {
				return 0;
			}
			Object parsed = JSONUtil.fromJSON( Files.readString( file, StandardCharsets.UTF_8 ) );
			if ( ! ( parsed instanceof Map<?, ?> m ) ) {
				return 1;
			}
			Object o = null;
			for ( Map.Entry<?, ?> e : m.entrySet() ) {
				String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
				if ( k.equalsIgnoreCase( "overrides" ) ) {
					o = e.getValue();
				}
			}
			Map<String, Object> out = new LinkedHashMap<>();
			if ( o instanceof Map<?, ?> om ) {
				for ( Map.Entry<?, ?> e : om.entrySet() ) {
					String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
					try {
						out.put( registry.get( k ).key(), registry.coerce( k, e.getValue() ) );
					} catch ( RuntimeException bad ) {
						skipped++;
					}
				}
			}
			this.overrides = Map.copyOf( out );
		} catch ( Throwable t ) {
			skipped++;
		}
		this.skipped = skipped;
		return skipped;
	}

	private int skipped;

	/**
	 * How many entries of the file were ignored when it was loaded.
	 */
	public int skipped() {
		return skipped;
	}

}
