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

	public static final List<String>	GROUPS			= List.of( "Bar", "Collection", "Collectors", "Thresholds", "Checks", "Interface", "Limits", "Editor",
	    "Console", "Access", "AI" );

	/** The providers of bx-ai that can chat with tools. Bedrock needs a credentials struct, so it is not offered. */
	public static final List<String>	AI_PROVIDERS	= List.of( "ollama", "openai", "claude", "gemini", "mistral", "groq", "grok", "deepseek", "openrouter",
	    "openai-compatible", "cohere", "docker" );

	private final List<Def>				defs			= new ArrayList<>();
	private final Map<String, Def>		byKey			= new LinkedHashMap<>();

	/**
	 * @param collectorIds the collector ids, each one gets an on/off switch
	 */
	public SettingsRegistry( List<String> collectorIds ) {
		add( "bar.enabled", "bool", "Bar", "Show the bar", true, false );
		add( "collect.level", "enum", "Collection", "Collect level", true, "light", List.of( "off", "light", "full" ) );
		add( "tabs.hide", "list", "Collection", "Hidden tabs", true, List.of() );
		add( "inject", "bool", "Bar", "Inject the bar into HTML pages", true, true );
		add( "history.trackNonHtml", "bool", "Collection", "Track JSON and SSE requests (default: off in light, on in full)", true, false );
		add( "history.headerAlways", "bool", "Collection", "Send the request id header on every tracked request", true, true );
		for ( String id : collectorIds ) {
			add( "collectors." + id + ".enabled", "bool", "Collectors", id, true, !List.of( "functions", "logs", "bifs", "orm" ).contains( id ) );
		}
		add( "collectors.queries.includeParams", "bool", "Collectors", "queries: keep parameter values", true, false );
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
		add( "ai.enabled", "bool", "AI", "AI: let the server call a model (Plus)", true, false );
		add( "ai.provider", "enum", "AI", "AI provider", true, "ollama", AI_PROVIDERS );
		add( "ai.model", "string", "AI", "AI model (empty: llama3.2 for Ollama, else the provider default)", true, "llama3.2" );
		add( "ai.baseUrl", "string", "AI", "Address of the model server (empty: http://localhost:11434 for Ollama)", true, "http://localhost:11434" );
		add( "ai.embeddingModel", "string", "AI", "Embedding model for the documentation search", true, "nomic-embed-text" );
		add( "ai.temperature", "string", "AI", "Temperature (0 to 2)", true, "0.2" );
		add( "ai.timeoutSeconds", "int", "AI", "Seconds an answer may take", true, 120, 10, 600 );
		add( "ai.maxToolCalls", "int", "AI", "Tool calls allowed per answer", true, 8, 1, 20 );
		add( "ai.memoryMessages", "int", "AI", "Messages the conversation remembers", true, 20, 2, 100 );
		add( "ai.maxConcurrentChats", "int", "AI", "Chats that may run at once on this server", true, 3, 1, 20 );
		add( "ai.actions", "bool", "AI", "Let the agent propose actions (each one needs an approval)", true, true );
		add( "ai.rag", "bool", "AI", "Let the agent search the Lens documentation", true, true );
		add( "ai.apiKeyEnv", "string", "AI", "Name of the environment variable that holds the API key", true, "" );
		add( "ai.apiKey", "secret", "AI", "AI API key (a bxsecret: value in boxlang.json only)", false, "" );
		add( "ai.links", "bool", "AI", "AI: show copy and chat links", false, true );
		add( "collectors.http.propagateId", "bool", "Collectors", "HTTP: add X-Request-Id to outgoing calls", false, false );
		add( "async.enabled", "bool", "Console", "Run statistics and history on a worker thread", false, true );
		add( "async.queueSize", "int", "Console", "Work queue size", false, 2000 );
		add( "bar.access", "string", "Access", "Who sees the bar", false, "local" );
		add( "bar.allowAllIPs", "bool", "Access", "Confirm bar.access all", false, false );
		add( "history.maxRequests", "int", "Access", "Requests kept in memory", false, 50 );
	}

	/**
	 * An address the agent may be pointed at: http or https, a host, no user name and password in it.
	 */
	public static boolean isSafeBaseUrl( String url ) {
		try {
			java.net.URI u = java.net.URI.create( url );
			return ( "http".equals( u.getScheme() ) || "https".equals( u.getScheme() ) ) && u.getHost() != null && u.getUserInfo() == null
			    && url.length() <= 300;
		} catch ( IllegalArgumentException e ) {
			return false;
		}
	}

	/**
	 * Is this the name of an environment variable? Letters, digits and underscores, not starting with a digit.
	 */
	public static boolean isEnvName( String name ) {
		if ( name.isEmpty() || name.length() > 100 || Character.isDigit( name.charAt( 0 ) ) ) {
			return false;
		}
		for ( int i = 0; i < name.length(); i++ ) {
			char ch = name.charAt( i );
			if ( !Character.isLetterOrDigit( ch ) && ch != '_' || ch > 127 ) {
				return false;
			}
		}
		return true;
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
				str = str.trim();
				switch ( d.key().toLowerCase( Locale.ROOT ) ) {
					case "ai.baseurl" :
						if ( !str.isEmpty() && !isSafeBaseUrl( str ) ) {
							throw new IllegalArgumentException( d.key() + " must be an http or https address without a user name or password" );
						}
						break;
					case "ai.temperature" :
						try {
							double t = Double.parseDouble( str );
							if ( t < 0 || t > 2 || Double.isNaN( t ) ) {
								throw new NumberFormatException();
							}
						} catch ( NumberFormatException e ) {
							throw new IllegalArgumentException( d.key() + " must be a number from 0 to 2" );
						}
						break;
					case "ai.apikeyenv" :
						if ( !str.isEmpty() && !isEnvName( str ) ) {
							throw new IllegalArgumentException(
							    d.key() + " is the NAME of an environment variable: letters, digits and underscores. Do not type the key itself here" );
						}
						break;
					case "ai.model", "ai.embeddingmodel" :
						for ( int i = 0; i < str.length(); i++ ) {
							char ch = str.charAt( i );
							if ( ch < 0x21 || ch > 0x7e ) {
								throw new IllegalArgumentException( d.key() + " is a model name without spaces or special characters" );
							}
						}
						break;
					default :
						break;
				}
				if ( "editor.linkPattern".equalsIgnoreCase( d.key() ) && !LensConfig.isSafeEditorLink( str ) ) {
					throw new IllegalArgumentException(
					    d.key() + " must start with one of vscode:, vscode-insiders:, idea:, phpstorm:, subl:, file:, http:, https:, cursor: or zed:" );
				}
				return str;
		}
	}

}
