/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Immutable, parsed view of the module settings. Built once at activation so hot paths never walk BoxLang structs.
 * Every getter takes a default, so a partial user override of a nested key never breaks a lookup.
 */
public final class LensConfig {

	private static final Object													MISSING			= new Object();

	private final Map<String, Object>											root;
	/** Dotted paths already resolved. The config never changes after it is built, so a path is walked once. */
	private final ConcurrentHashMap<String, Object>								resolved		= new ConcurrentHashMap<>();
	/** Collector id to key to value, so hot reads do not build a path string per event. */
	private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>	collectorValues	= new ConcurrentHashMap<>();

	/** The bar is turned on. It is only shown to callers allowed by <code>bar.access</code>. */
	public final boolean														barEnabled;
	/** The standalone console is turned on. It also needs a password. */
	public final boolean														consoleEnabled;
	/** Requests are collected: the bar or the console is on and the collect level is not off. */
	public final boolean														active;
	/** off, light or full. Light skips bindings, scopes, headers, stack traces and the expensive collectors. */
	public final String															collectLevel;
	public final boolean														light;
	public final int															sessionMinutes;
	public final int															maxLoginAttempts;
	public final int															lockoutMinutes;
	public final List<String>													hiddenTabs;
	public final boolean														inject;
	public final boolean														trackNonHtml;
	public final int															maxRequests;
	public final List<String>													contentTypes;
	public final List<String>													excludePaths;
	public final List<String>													redactKeys;
	public final String															redactMask;
	public final String															idHeader;
	public final int															slowRequestMs;
	public final int															slowQueryMs;
	public final int															slowTemplateMs;
	public final int															nPlusOneMin;
	public final int															maxString;
	public final int															maxDepth;
	public final int															maxItems;
	/** A request still running after this many minutes is finished by the watchdog as unfinished. */
	public final int															requestMaxMinutes;
	/** The proxy settings, read once: they are needed for every request. */
	public final boolean														trustProxyHeader;
	public final String															proxyHeader;
	public final List<String>													proxyPeers;
	/** Hot reads resolved once when the config is built. */
	public final boolean														queriesIncludeParams;
	public final boolean														queriesCaptureCaller;
	public final boolean														ormEnabled;

	/**
	 * Parse module settings.
	 *
	 * @param settings a Map, a BoxLang IStruct (it is a Map) or null for all defaults
	 */
	public LensConfig( Map<?, ?> settings ) {
		this.root			= deepCopy( settings );
		this.barEnabled		= getBool( "bar.enabled", false );
		this.consoleEnabled	= getBool( "console.enabled", false );
		String level = getString( "collect.level", "light" ).trim().toLowerCase( Locale.ROOT );
		this.collectLevel		= List.of( "off", "light", "full" ).contains( level ) ? level : "light";
		this.light				= "light".equals( this.collectLevel );
		this.active				= ( this.barEnabled || this.consoleEnabled ) && !"off".equals( this.collectLevel );
		this.sessionMinutes		= Math.max( 1, getInt( "console.sessionMinutes", 30 ) );
		this.maxLoginAttempts	= Math.max( 1, getInt( "console.maxLoginAttempts", 5 ) );
		this.lockoutMinutes		= Math.max( 1, getInt( "console.lockoutMinutes", 5 ) );
		this.hiddenTabs			= lower( getList( "tabs.hide", List.of() ) );
		this.inject				= getBool( "inject", true );
		// Off in light, on in full, unless the setting says otherwise
		this.trackNonHtml		= getBool( "history.trackNonHtml", !this.light );
		this.maxRequests		= Math.max( 1, getInt( "history.maxRequests", 50 ) );
		this.idHeader			= getString( "history.header", "X-BxLens-Id" );
		this.contentTypes		= lower( getList( "contentTypes", List.of( "text/html" ) ) );
		this.excludePaths		= getList( "excludePaths", List.of( "/~bxlens/*", "/favicon.ico" ) );
		this.redactKeys			= lower( getList( "redact.keys",
		    List.of( "password", "pwd", "passwd", "token", "secret", "apikey", "api_key", "authorization", "cookie", "credential" ) ) );
		this.redactMask			= getString( "redact.mask", "[redacted]" );
		this.slowRequestMs		= getInt( "thresholds.slowRequestMs", 500 );
		this.slowQueryMs		= getInt( "thresholds.slowQueryMs", 25 );
		this.slowTemplateMs		= getInt( "thresholds.slowTemplateMs", 100 );
		this.nPlusOneMin		= Math.max( 2, getInt( "thresholds.nPlusOneMin", 3 ) );
		this.maxString			= getInt( "limits.maxString", 2000 );
		this.maxDepth			= getInt( "limits.maxDepth", 4 );
		this.maxItems			= getInt( "limits.maxItems", 100 );
		this.trustProxyHeader	= getBool( "access.trustProxyHeader", true );
		this.proxyHeader		= getString( "access.proxyHeader", "X-Forwarded-For" );
		List<String> peers = new ArrayList<>( getList( "access.proxyPeers", List.of() ) );
		if ( peers.isEmpty() ) {
			String single = getString( "access.proxyPeers", "private" );
			peers.add( single.isBlank() ? "private" : single );
		}
		this.proxyPeers				= Collections.unmodifiableList( peers );
		this.requestMaxMinutes		= Math.max( 1, getInt( "request.maxMinutes", 10 ) );
		this.queriesIncludeParams	= collectorBool( "queries", "includeParams", false );
		this.queriesCaptureCaller	= collectorBool( "queries", "captureCaller", true );
		this.ormEnabled				= isCollectorEnabled( "orm", false );
	}

	/**
	 * The base settings with dotted-key overrides applied on top, for example <code>thresholds.slowQueryMs</code>. The inputs are not changed.
	 */
	public static Map<String, Object> overlay( Map<?, ?> base, Map<String, Object> overrides ) {
		Map<String, Object> out = deepCopy( base );
		if ( overrides != null ) {
			for ( Map.Entry<String, Object> e : overrides.entrySet() ) {
				setPath( out, e.getKey(), copyValue( e.getValue() ) );
			}
		}
		return out;
	}

	@SuppressWarnings( "unchecked" )
	private static void setPath( Map<String, Object> root, String path, Object value ) {
		String[]			parts	= path.split( "\\." );
		Map<String, Object>	cur		= root;
		for ( int i = 0; i < parts.length - 1; i++ ) {
			Object next = cur.get( parts[ i ] );
			if ( ! ( next instanceof Map ) ) {
				next = new TreeMap<String, Object>( String.CASE_INSENSITIVE_ORDER );
				cur.put( parts[ i ], next );
			}
			cur = ( Map<String, Object> ) next;
		}
		cur.put( parts[ parts.length - 1 ], value );
	}

	/**
	 * Defaults only.
	 */
	public static LensConfig defaults() {
		return new LensConfig( null );
	}

	/**
	 * Is a collector enabled? A collector is enabled when its block has <code>enabled: true</code>, or when the block is a plain boolean.
	 *
	 * @param id  collector id
	 * @param def default when the key is missing
	 */
	public boolean isCollectorEnabled( String id, boolean def ) {
		Object v = collectorValue( id, "" );
		if ( v instanceof Boolean b ) {
			return b;
		}
		return toBool( collectorValue( id, "enabled" ), def );
	}

	/**
	 * Read an int from a collector block.
	 */
	public int collectorInt( String id, String key, int def ) {
		return toInt( collectorValue( id, key ), def );
	}

	/**
	 * Read a boolean from a collector block.
	 */
	public boolean collectorBool( String id, String key, boolean def ) {
		return toBool( collectorValue( id, key ), def );
	}

	private Object collectorValue( String id, String key ) {
		ConcurrentHashMap<String, Object> m = collectorValues.get( id );
		if ( m == null ) {
			m = collectorValues.computeIfAbsent( id, k -> new ConcurrentHashMap<>() );
		}
		Object v = m.get( key );
		if ( v == null ) {
			v = get( key.isEmpty() ? "collectors." + id : "collectors." + id + "." + key );
			m.put( key, v == null ? MISSING : v );
			return v;
		}
		return v == MISSING ? null : v;
	}

	/**
	 * Read a dotted path, case insensitive. The path is walked once; later reads come from a cache.
	 *
	 * @return the value or null
	 */
	public Object get( String path ) {
		Object v = resolved.get( path );
		if ( v == null ) {
			v = walk( path );
			resolved.put( path, v == null ? MISSING : v );
			return v;
		}
		return v == MISSING ? null : v;
	}

	@SuppressWarnings( "unchecked" )
	private Object walk( String path ) {
		Object	cur		= root;
		int		start	= 0;
		int		n		= path.length();
		while ( start <= n ) {
			int end = path.indexOf( '.', start );
			if ( end < 0 ) {
				end = n;
			}
			if ( ! ( cur instanceof Map ) ) {
				return null;
			}
			cur		= ( ( Map<String, Object> ) cur ).get( path.substring( start, end ) );
			start	= end + 1;
		}
		return cur;
	}

	private static boolean toBool( Object v, boolean def ) {
		if ( v instanceof Boolean b ) {
			return b;
		}
		if ( v instanceof String s && !s.isBlank() ) {
			return Boolean.parseBoolean( s.trim() ) || "yes".equalsIgnoreCase( s.trim() );
		}
		return def;
	}

	private static int toInt( Object v, int def ) {
		if ( v instanceof Number n ) {
			return n.intValue();
		}
		if ( v instanceof String s ) {
			try {
				return Integer.parseInt( s.trim() );
			} catch ( NumberFormatException e ) {
				return def;
			}
		}
		return def;
	}

	public boolean getBool( String path, boolean def ) {
		return toBool( get( path ), def );
	}

	public int getInt( String path, int def ) {
		return toInt( get( path ), def );
	}

	public String getString( String path, String def ) {
		Object v = get( path );
		return v == null ? def : v.toString();
	}

	/**
	 * Read a list of strings. A comma separated string is accepted too.
	 */
	public List<String> getList( String path, List<String> def ) {
		Object v = get( path );
		if ( v instanceof Collection<?> c ) {
			List<String> out = new ArrayList<>();
			for ( Object o : c ) {
				if ( o != null ) {
					out.add( o.toString() );
				}
			}
			return Collections.unmodifiableList( out );
		}
		if ( v instanceof String s && !s.isBlank() ) {
			List<String> out = new ArrayList<>();
			for ( String p : s.split( "," ) ) {
				if ( !p.isBlank() ) {
					out.add( p.trim() );
				}
			}
			return Collections.unmodifiableList( out );
		}
		return def;
	}

	/** The pattern used when the configured one has a scheme that is not allowed. */
	public static final String			DEFAULT_EDITOR_LINK	= "vscode://file/{path}:{line}";
	private static final List<String>	EDITOR_SCHEMES		= List.of( "vscode", "vscode-insiders", "idea", "phpstorm", "subl", "file", "http", "https",
	    "cursor",
	    "zed" );

	/**
	 * Is this an editor link pattern with an allowed scheme? A pattern is a template for a link, so a scheme such as <code>javascript:</code>
	 * or <code>data:</code> would run code when the link is clicked.
	 */
	public static boolean isSafeEditorLink( String pattern ) {
		if ( pattern == null || pattern.isBlank() ) {
			return false;
		}
		String	p		= pattern.trim();
		int		colon	= p.indexOf( ':' );
		if ( colon <= 0 ) {
			return false;
		}
		for ( int i = 0; i < p.length(); i++ ) {
			if ( p.charAt( i ) < 0x20 ) {
				return false;
			}
		}
		return EDITOR_SCHEMES.contains( p.substring( 0, colon ).toLowerCase( Locale.ROOT ) );
	}

	/**
	 * The editor link pattern to send to the page: the configured one when its scheme is allowed, else the default.
	 */
	public String editorLink() {
		String p = getString( "editor.linkPattern", DEFAULT_EDITOR_LINK );
		return isSafeEditorLink( p ) ? p.trim() : DEFAULT_EDITOR_LINK;
	}

	/**
	 * The password for the console. A <code>bxsecret:</code> value is decrypted with the runtime seed.
	 *
	 * @return the plain password or an empty string when none is set or it cannot be decrypted
	 */
	public String consolePassword() {
		return secret( "console.password" );
	}

	/**
	 * The optional password of the view-only role. Same format as the admin password.
	 */
	public String viewerPassword() {
		return secret( "console.viewerPassword" );
	}

	/**
	 * The API key for the language model, a <code>bxsecret:</code> value is decrypted.
	 */
	public String aiApiKey() {
		return secret( "ai.apiKey" );
	}

	private String secret( String path ) {
		String v = getString( path, "" );
		if ( v.isBlank() ) {
			return "";
		}
		try {
			return ortus.boxlang.runtime.util.ConfigSecretUtil.decryptIfEncrypted( v );
		} catch ( Throwable t ) {
			return "";
		}
	}

	/**
	 * Does a request path match one of the excluded path patterns? A trailing * means prefix match.
	 */
	public boolean isExcluded( String path ) {
		if ( path == null ) {
			return false;
		}
		// The console is never tracked, whatever the user configures
		if ( path.startsWith( "/~bxlens/" ) || path.equals( "/~bxlens" ) ) {
			return true;
		}
		for ( String p : excludePaths ) {
			if ( p.endsWith( "*" ) ? path.startsWith( p.substring( 0, p.length() - 1 ) ) : path.equals( p ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Is this content type one we inject the bar into?
	 */
	public boolean isInjectable( String contentType ) {
		if ( contentType == null ) {
			return false;
		}
		String ct = contentType.toLowerCase( Locale.ROOT );
		for ( String allowed : contentTypes ) {
			if ( ct.startsWith( allowed ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Should a key be redacted? Case insensitive contains match against the redact list.
	 */
	public boolean shouldRedact( String key ) {
		if ( key == null ) {
			return false;
		}
		String k = key.toLowerCase( Locale.ROOT );
		for ( String r : redactKeys ) {
			if ( k.contains( r ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The raw settings as nested maps, for the UI config block.
	 */
	public Map<String, Object> raw() {
		return root;
	}

	private static List<String> lower( List<String> in ) {
		List<String> out = new ArrayList<>( in.size() );
		for ( String s : in ) {
			out.add( s.toLowerCase( Locale.ROOT ) );
		}
		return Collections.unmodifiableList( out );
	}

	private static Map<String, Object> deepCopy( Map<?, ?> in ) {
		Map<String, Object> out = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
		if ( in == null ) {
			return out;
		}
		for ( Map.Entry<?, ?> e : in.entrySet() ) {
			String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
			out.put( k, copyValue( e.getValue() ) );
		}
		return out;
	}

	private static Object copyValue( Object v ) {
		if ( v instanceof Map<?, ?> m ) {
			return deepCopy( m );
		}
		if ( v instanceof Collection<?> c ) {
			List<Object> l = new ArrayList<>();
			for ( Object o : c ) {
				l.add( copyValue( o ) );
			}
			return l;
		}
		return v;
	}

}
