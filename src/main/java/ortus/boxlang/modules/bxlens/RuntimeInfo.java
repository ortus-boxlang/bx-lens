/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * What the runtime is and how it is set up, read from the real objects: the version, the JVM, the memory, and the effective configuration of
 * BoxLang. Two views use it. The bar shows a small snapshot ({@link #snapshot()}, rebuilt at most every few seconds, not for every request). The
 * console Configuration page shows every area with the effective value and where it came from ({@link #configuration(boolean)}).
 * <p>
 * Only keys that exist in the configuration are shown, nothing is invented. A value whose name looks secret is hidden by the shared matcher
 * ({@link Secrets}).
 */
public final class RuntimeInfo {

	private static final long				TTL			= 5_000L;

	private volatile long					builtAt;
	private volatile Map<String, Object>	cached		= Map.of();
	private volatile Map<String, Object>	shipped;

	/** The keys of the small list on the bar, in order, with a label. */
	private static final String[][]			BAR_KEYS	= { { "debugMode", "Debug mode" }, { "compiler", "Compiler" }, { "timezone", "Time zone" },
	    { "locale", "Locale" }, { "requestTimeout", "Request timeout" }, { "sessionTimeout", "Session timeout" },
	    { "applicationTimeout", "Application timeout" }, { "sessionStorage", "Session storage" }, { "sessionManagement", "Session management" },
	    { "defaultDatasource", "Default datasource" } };

	/** The areas of the Configuration page: name, whether a viewer may read it, and the keys it holds. */
	private static final Object[][]			GROUPS		= {
	    { "Runtime", true, new String[] { "version", "debugMode", "compiler", "timezone", "locale", "whitespaceCompressionEnabled", "useHighPrecisionMath",
	        "enforceUDFTypeChecks", "invokeImplicitAccessor", "trustedCache", "classResolverCache", "storeClassFilesOnDisk", "clearClassFilesOnStartup",
	        "jarTempFileCaching", "maxTrackedCompletedThreads", "validClassExtensions", "validTemplateExtensions", "validExtensions" } },
	    { "Requests and sessions", true, new String[] { "requestTimeout", "applicationTimeout", "sessionTimeout", "sessionManagement", "sessionStorage",
	        "sessionType", "setClientCookies", "setDomainCookies", "globalErrorTemplate" } },
	    { "Data", true, new String[] { "defaultDatasource", "datasources", "caches", "enableNestedTransactions", "defaultRemoteMethodReturnFormat",
	        "defaultJSONQuerySerializationFormat", "queries" } },
	    { "Executors and tasks", true, new String[] { "executors", "scheduler", "watcher" } },
	    { "Experimental", true, new String[] { "experimental" } },
	    { "Paths", false, new String[] { "mappings", "modulesDirectory", "customComponentsDirectory", "classPaths", "javaLibraryPaths",
	        "classGenerationDirectory" } },
	    { "Security", false, new String[] { "security", "disallowedImports" } },
	    { "Logging", false, new String[] { "logging" } },
	    { "Modules", false, new String[] { "modules", "runtimes" } }
	};

	private static BoxRuntime runtime() {
		return BoxRuntime.getInstance();
	}

	// ---------------------------------------------------------------------------------------------
	// The small snapshot for the bar
	// ---------------------------------------------------------------------------------------------

	/**
	 * A small snapshot for the bar. Rebuilt at most every few seconds, whatever the number of requests.
	 */
	public Map<String, Object> snapshot() {
		long now = System.currentTimeMillis();
		if ( now - builtAt > TTL || cached.isEmpty() ) {
			synchronized ( this ) {
				if ( now - builtAt > TTL || cached.isEmpty() ) {
					cached	= build();
					builtAt	= now;
				}
			}
		}
		return cached;
	}

	private Map<String, Object> build() {
		Map<String, Object> m = new LinkedHashMap<>();
		try {
			IStruct				v	= runtime().getVersionInfo();
			Map<String, Object>	bx	= new LinkedHashMap<>();
			bx.put( "version", String.valueOf( v.getOrDefault( Key.of( "version" ), "" ) ) );
			bx.put( "build", String.valueOf( v.getOrDefault( Key.of( "buildDate" ), "" ) ) );
			bx.put( "codename", String.valueOf( v.getOrDefault( Key.of( "codename" ), "" ) ) );
			m.put( "boxlang", bx );
		} catch ( Throwable t ) {
			m.put( "boxlang", Map.of() );
		}
		Map<String, Object> java = new LinkedHashMap<>();
		java.put( "version", System.getProperty( "java.version", "" ) );
		java.put( "vendor", System.getProperty( "java.vendor", "" ) );
		m.put( "java", java );
		m.put( "os", System.getProperty( "os.name", "" ) + " " + System.getProperty( "os.version", "" ) + " " + System.getProperty( "os.arch", "" ) );
		m.put( "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime() );
		var					heap	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		Map<String, Object>	mem		= new LinkedHashMap<>();
		mem.put( "usedMb", Math.round( heap.getUsed() / 104857.6 ) / 10.0 );
		mem.put( "maxMb", heap.getMax() < 0 ? 0 : Math.round( heap.getMax() / 104857.6 ) / 10.0 );
		m.put( "heap", mem );
		m.put( "server", hostName() );
		IStruct			cfg		= config();
		List<String>	caches	= new ArrayList<>();
		if ( cfg != null && cfg.get( Key.of( "caches" ) ) instanceof Map<?, ?> c ) {
			c.keySet().forEach( k -> caches.add( k instanceof Key key ? key.getName() : String.valueOf( k ) ) );
		}
		m.put( "caches", caches );
		List<Map<String, Object>> settings = new ArrayList<>();
		if ( cfg != null ) {
			for ( String[] k : BAR_KEYS ) {
				Object v = cfg.get( Key.of( k[ 0 ] ) );
				if ( v != null && !String.valueOf( v ).isEmpty() ) {
					settings.add( entry( k[ 1 ], show( k[ 0 ], v ) ) );
				}
			}
			if ( cfg.get( Key.of( "mappings" ) ) instanceof Map<?, ?> mp ) {
				settings.add( entry( "Mappings", String.valueOf( mp.size() ) ) );
			}
			if ( cfg.get( Key.of( "modulesDirectory" ) ) instanceof Collection<?> md ) {
				settings.add( entry( "Modules directory", Secrets.text( String.join( ", ", strings( md ) ) ) ) );
			}
			if ( cfg.get( Key.of( "security" ) ) instanceof Map<?, ?> sec ) {
				for ( Map.Entry<?, ?> e : sec.entrySet() ) {
					String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
					// "allowed..." and "disallowed..." lists, only when they hold something
					if ( e.getValue() instanceof Collection<?> c && !c.isEmpty() && k.toLowerCase( Locale.ROOT ).contains( "allow" ) ) {
						settings.add( entry( "Security " + k, c.size() + " entries" ) );
					}
				}
			}
			if ( cfg.get( Key.of( "experimental" ) ) instanceof Map<?, ?> ex ) {
				List<String> on = new ArrayList<>();
				ex.forEach( ( k, v ) -> {
					if ( Boolean.TRUE.equals( v ) || "true".equalsIgnoreCase( String.valueOf( v ) ) ) {
						on.add( k instanceof Key key ? key.getName() : String.valueOf( k ) );
					}
				} );
				if ( !on.isEmpty() ) {
					settings.add( entry( "Experimental on", String.join( ", ", on ) ) );
				}
			}
		}
		m.put( "settings", settings );
		return m;
	}

	private static Map<String, Object> entry( String label, String value ) {
		Map<String, Object> e = new LinkedHashMap<>();
		e.put( "label", label );
		e.put( "value", value );
		return e;
	}

	private static List<String> strings( Collection<?> c ) {
		List<String> out = new ArrayList<>();
		for ( Object o : c ) {
			out.add( String.valueOf( o ) );
		}
		return out;
	}

	private static String hostName() {
		try {
			return java.net.InetAddress.getLocalHost().getHostName();
		} catch ( Throwable t ) {
			return "localhost";
		}
	}

	private static IStruct config() {
		try {
			return runtime().getConfiguration().asStruct();
		} catch ( Throwable t ) {
			return null;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// The Configuration page
	// ---------------------------------------------------------------------------------------------

	/**
	 * The configuration by area. Each entry has the key, the effective value, where it came from when that is known (<code>default</code>,
	 * <code>boxlang.json</code>, <code>environment variable</code>) and the type.
	 *
	 * @param admin may the caller read the areas that show paths, security lists and logging? A viewer may not.
	 */
	public Map<String, Object> configuration( boolean admin ) {
		Map<String, Object>	out		= new LinkedHashMap<>();
		IStruct				cfg		= config();
		Map<String, Object>	base	= shippedDefaults();
		List<Object>		groups	= new ArrayList<>();
		if ( cfg == null ) {
			out.put( "groups", groups );
			out.put( "error", "The configuration could not be read" );
			return out;
		}
		for ( Object[] g : GROUPS ) {
			boolean open = ( Boolean ) g[ 1 ];
			if ( !open && !admin ) {
				continue;
			}
			List<Object> entries = new ArrayList<>();
			for ( String k : ( String[] ) g[ 2 ] ) {
				Object v = cfg.get( Key.of( k ) );
				if ( v == null && !cfg.containsKey( Key.of( k ) ) ) {
					continue;
				}
				Map<String, Object> e = new LinkedHashMap<>();
				e.put( "key", k );
				e.put( "value", walk( v, k, 0 ) );
				e.put( "source", source( k, v, base ) );
				entries.add( e );
			}
			if ( entries.isEmpty() ) {
				continue;
			}
			Map<String, Object> group = new LinkedHashMap<>();
			group.put( "name", g[ 0 ] );
			group.put( "admin", !open );
			group.put( "entries", entries );
			groups.add( group );
		}
		out.put( "groups", groups );
		out.put( "adminOnlyHidden", !admin );
		return out;
	}

	/**
	 * Where a value came from. The shipped defaults are read from the BoxLang jar. A name that matches an environment variable, such as
	 * <code>BOXLANG_DEBUGMODE</code>, is reported as such. Anything else that differs from the shipped default was set in a configuration file.
	 */
	private String source( String key, Object value, Map<String, Object> base ) {
		try {
			String env = System.getenv( "BOXLANG_" + key.toUpperCase( Locale.ROOT ) );
			if ( env != null ) {
				return "environment variable";
			}
			if ( base.isEmpty() || !base.containsKey( key ) ) {
				return "";
			}
			Object shipped = base.get( key );
			return same( shipped, value ) ? "default" : "boxlang.json";
		} catch ( Throwable t ) {
			return "";
		}
	}

	private static boolean same( Object shipped, Object value ) {
		if ( shipped == null ) {
			return value == null || String.valueOf( value ).isEmpty();
		}
		String s = String.valueOf( shipped ).trim();
		if ( s.contains( "${" ) ) {
			// A path built from a placeholder: the shipped value is a default by definition
			return true;
		}
		if ( value instanceof Duration d ) {
			try {
				long n = Long.parseLong( s );
				return d.toMinutes() == n || d.getSeconds() == n;
			} catch ( NumberFormatException e ) {
				return s.equals( d.toString() ) || d.isZero() && ( s.equals( "0" ) || s.isEmpty() || s.startsWith( "0," ) );
			}
		}
		if ( value instanceof Map<?, ?> || value instanceof Collection<?> ) {
			// Compare the size: the shipped file holds the defaults, a user file adds or removes entries
			int	n	= shipped instanceof Map<?, ?> m ? m.size() : shipped instanceof Collection<?> c ? c.size() : -1;
			int	v	= value instanceof Map<?, ?> m ? m.size() : ( ( Collection<?> ) value ).size();
			return n == v;
		}
		return s.equalsIgnoreCase( String.valueOf( value ).trim() );
	}

	private Map<String, Object> shippedDefaults() {
		Map<String, Object> s = shipped;
		if ( s == null ) {
			s = new LinkedHashMap<>();
			try ( InputStream in = BoxRuntime.class.getResourceAsStream( "/config/boxlang.json" ) ) {
				if ( in != null ) {
					String text = new String( in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8 );
					s.putAll( Plain.map( Plain.parse( text ) ) );
				}
			} catch ( Throwable t ) {
				// Sources are then unknown
			}
			shipped = s;
		}
		return s;
	}

	/**
	 * Copy a value with secrets hidden, depth and size capped.
	 */
	public static Object walk( Object v, String name, int depth ) {
		if ( v == null ) {
			return null;
		}
		if ( Secrets.isSecretName( name ) && ! ( v instanceof Map ) && ! ( v instanceof Collection ) ) {
			return v.toString().isEmpty() ? "" : Secrets.HIDDEN;
		}
		if ( depth > 12 ) {
			return "...";
		}
		if ( v instanceof Map<?, ?> map ) {
			Map<String, Object> out = new LinkedHashMap<>();
			for ( Map.Entry<?, ?> e : map.entrySet() ) {
				String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
				out.put( k, walk( e.getValue(), k, depth + 1 ) );
			}
			return out;
		}
		if ( v instanceof Collection<?> c ) {
			List<Object>	out	= new ArrayList<>();
			int				n	= 0;
			for ( Object o : c ) {
				if ( n++ >= 200 ) {
					out.add( "..." );
					break;
				}
				out.add( walk( o, name, depth + 1 ) );
			}
			return out;
		}
		if ( v instanceof Number || v instanceof Boolean ) {
			return v;
		}
		return show( name, v );
	}

	private static String show( String name, Object v ) {
		String s = Secrets.show( name, String.valueOf( v ) );
		return s.length() > 500 ? s.substring( 0, 500 ) + "..." : s;
	}

}
