/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings an admin may change from the console without a restart, and the ones that stay in <code>boxlang.json</code> only.
 * Everything else is rejected, so the console can never reach the password, the access rules or the dev options.
 */
public final class SettingsRegistry {

	/**
	 * One setting. <code>live</code> means it can be changed from the console.
	 */
	public record Def( String key, String type, String group, String label, boolean live, Object def, List<String> options, int min, int max ) {

		public Map<String, Object> toMap() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "key", key );
			m.put( "type", type );
			m.put( "group", group );
			m.put( "label", label );
			m.put( "live", live );
			m.put( "default", def );
			m.put( "options", options );
			m.put( "min", min );
			m.put( "max", max );
			return m;
		}
	}

	public static final List<String>	GROUPS	= List.of( "Bar", "Collection", "Collectors", "Thresholds", "Checks", "Interface", "Limits", "Editor",
	    "Console", "Access" );

	private final List<Def>				defs	= new ArrayList<>();
	private final Map<String, Def>		byKey	= new LinkedHashMap<>();

	/**
	 * @param collectorIds the collector ids, each one gets an on/off switch
	 */
	public SettingsRegistry( List<String> collectorIds ) {
		add( "bar.enabled", "bool", "Bar", "Show the bar", true, false );
		add( "collect.level", "enum", "Collection", "Collect level", true, "full", List.of( "off", "light", "full" ) );
		add( "tabs.hide", "list", "Collection", "Hidden tabs", true, List.of() );
		add( "inject", "bool", "Bar", "Inject the bar into HTML pages", true, true );
		add( "history.trackNonHtml", "bool", "Collection", "Track JSON and SSE requests", true, true );
		for ( String id : collectorIds ) {
			add( "collectors." + id + ".enabled", "bool", "Collectors", id, true, !List.of( "functions", "logs" ).contains( id ) );
		}
		add( "collectors.queries.includeParams", "bool", "Collectors", "queries: keep parameter values", true, true );
		add( "collectors.queries.captureCaller", "bool", "Collectors", "queries: find the calling line", true, true );
		add( "thresholds.slowRequestMs", "int", "Thresholds", "Slow request (ms)", true, 500, 0, 600000 );
		add( "thresholds.slowQueryMs", "int", "Thresholds", "Slow query (ms)", true, 25, 0, 600000 );
		add( "thresholds.slowTemplateMs", "int", "Thresholds", "Slow template (ms)", true, 100, 0, 600000 );
		add( "thresholds.nPlusOneMin", "int", "Thresholds", "N+1 minimum repeats", true, 3, 2, 1000 );
		add( "checks.securityHeaders", "bool", "Checks", "Security header notes", true, true );
		add( "checks.slowSample", "bool", "Checks", "Stack sample of slow requests", true, true );
		add( "ui.theme", "enum", "Interface", "Theme", true, "auto", List.of( "auto", "dark", "light" ) );
		add( "ui.startOpen", "bool", "Interface", "Start with the panel open", true, false );
		add( "ui.autoOpenOnException", "bool", "Interface", "Open on caught exceptions", true, true );
		add( "ui.defaultTab", "string", "Interface", "Default tab", true, "timeline" );
		add( "ui.height", "int", "Interface", "Panel height (px)", true, 360, 120, 2000 );
		add( "ui.allowDetach", "bool", "Interface", "Allow detaching", true, true );
		add( "ui.hotkey", "string", "Interface", "Hotkey", true, "Ctrl+`" );
		add( "limits.maxString", "int", "Limits", "Longest string sent to the page", true, 2000, 50, 100000 );
		add( "limits.maxDepth", "int", "Limits", "Deepest structure shown", true, 4, 1, 20 );
		add( "limits.maxItems", "int", "Limits", "Most items shown per list", true, 100, 1, 10000 );
		add( "editor.linkPattern", "string", "Editor", "Editor link pattern", true, "vscode://file/{path}:{line}" );
		add( "editor.remoteBase", "string", "Editor", "Remote base path", true, "" );
		add( "editor.localBase", "string", "Editor", "Local base path", true, "" );
		// Shown but locked: these need a restart or are too dangerous to change from a browser
		add( "console.enabled", "bool", "Console", "Console on", false, false );
		add( "console.password", "secret", "Console", "Admin password", false, "" );
		add( "console.viewerPassword", "secret", "Console", "Viewer password", false, "" );
		add( "console.access", "string", "Console", "Who can reach the console", false, "local" );
		add( "console.readOnly", "bool", "Console", "Read-only mode", false, false );
		add( "console.allowHeapDump", "bool", "Console", "Allow heap dumps", false, false );
		add( "console.overridesFile", "string", "Console", "Where overrides are saved", false, "" );
		add( "console.sessionMinutes", "int", "Console", "Session minutes", false, 30 );
		add( "console.requireHttps", "bool", "Console", "Require HTTPS", false, false );
		add( "access.trustProxyHeader", "bool", "Access", "Trust the proxy header", false, true );
		add( "access.proxyHeader", "string", "Access", "Proxy header", false, "X-Forwarded-For" );
		add( "access.proxyPeers", "string", "Access", "Trusted proxy peers", false, "private" );
		add( "store.enabled", "bool", "Console", "Disk store (Plus)", false, true );
		add( "store.dir", "string", "Console", "Disk store folder", false, "" );
		add( "store.retentionHours", "int", "Console", "Keep saved data (hours)", false, 72 );
		add( "store.maxMB", "int", "Console", "Disk store size limit (MB)", false, 50 );
		add( "ai.enabled", "bool", "Console", "AI: let the server call a model (Plus)", false, false );
		add( "ai.provider", "string", "Console", "AI provider", false, "" );
		add( "ai.model", "string", "Console", "AI model", false, "" );
		add( "ai.apiKey", "secret", "Console", "AI API key", false, "" );
		add( "ai.links", "bool", "Console", "AI: show copy and chat links", false, true );
		add( "bar.access", "string", "Access", "Who sees the bar", false, "local" );
		add( "bar.allowAllIPs", "bool", "Access", "Confirm bar.access all", false, false );
		add( "history.maxRequests", "int", "Access", "Requests kept in memory", false, 50 );
	}

	private void add( String key, String type, String group, String label, boolean live, Object def ) {
		add( key, type, group, label, live, def, List.of() );
	}

	private void add( String key, String type, String group, String label, boolean live, Object def, List<String> options ) {
		put( new Def( key, type, group, label, live, def, options, 0, 0 ) );
	}

	private void add( String key, String type, String group, String label, boolean live, Object def, int min, int max ) {
		put( new Def( key, type, group, label, live, def, List.of(), min, max ) );
	}

	private void put( Def d ) {
		defs.add( d );
		byKey.put( d.key().toLowerCase( Locale.ROOT ), d );
	}

	public List<Def> all() {
		return defs;
	}

	public Def get( String key ) {
		return key == null ? null : byKey.get( key.toLowerCase( Locale.ROOT ) );
	}

	/**
	 * Check and normalize a value for a setting.
	 *
	 * @return the value as the right Java type
	 *
	 * @throws IllegalArgumentException when the key is unknown, locked or the value is not valid
	 */
	public Object coerce( String key, Object value ) {
		Def d = get( key );
		if ( d == null ) {
			throw new IllegalArgumentException( "Unknown setting: " + key );
		}
		if ( !d.live() ) {
			throw new IllegalArgumentException( d.key() + " can only be changed in boxlang.json" );
		}
		switch ( d.type() ) {
			case "bool" :
				if ( value instanceof Boolean b ) {
					return b;
				}
				if ( value != null && ( "true".equalsIgnoreCase( value.toString() ) || "false".equalsIgnoreCase( value.toString() ) ) ) {
					return Boolean.parseBoolean( value.toString() );
				}
				throw new IllegalArgumentException( d.key() + " must be true or false" );
			case "int" :
				try {
					int n = value instanceof Number num ? num.intValue() : Integer.parseInt( String.valueOf( value ).trim() );
					if ( n < d.min() || n > d.max() ) {
						throw new IllegalArgumentException( d.key() + " must be between " + d.min() + " and " + d.max() );
					}
					return n;
				} catch ( NumberFormatException e ) {
					throw new IllegalArgumentException( d.key() + " must be a whole number" );
				}
			case "enum" :
				String s = String.valueOf( value ).trim().toLowerCase( Locale.ROOT );
				if ( !d.options().contains( s ) ) {
					throw new IllegalArgumentException( d.key() + " must be one of " + String.join( ", ", d.options() ) );
				}
				return s;
			case "list" :
				List<String> out = new ArrayList<>();
				if ( value instanceof java.util.Collection<?> c ) {
					for ( Object o : c ) {
						if ( o != null && !o.toString().isBlank() ) {
							out.add( o.toString().trim() );
						}
					}
				} else if ( value != null ) {
					for ( String p : value.toString().split( "," ) ) {
						if ( !p.isBlank() ) {
							out.add( p.trim() );
						}
					}
				}
				if ( out.size() > 100 ) {
					throw new IllegalArgumentException( d.key() + " has too many entries" );
				}
				return out;
			default :
				String str = value == null ? "" : value.toString();
				if ( str.length() > 500 ) {
					throw new IllegalArgumentException( d.key() + " is too long" );
				}
				return str;
		}
	}

}
