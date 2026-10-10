/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.runtime.scopes.Key;

/**
 * One MCP server Lensy may ask. The builtin ones are the Ortus documentation servers: they cannot be removed and their address cannot be
 * changed. A custom one is added by an admin.
 *
 * @param id           short name used in tool names (<code>id.tool</code> for people, <code>id__tool</code> for the model)
 * @param name         the name shown in the console
 * @param url          the address of the server
 * @param builtin      a server that ships with Lens
 * @param enabled      Lensy may use it
 * @param trusted      for a custom server: the admin vouches that its tools only read, so they run without a click (builtin servers never need one)
 * @param allowedTools the tools Lensy may use, or <code>["*"]</code> for all of them
 */
public record McpServer( String id, String name, String url, boolean builtin, boolean enabled, boolean trusted, List<String> allowedTools ) {

	public static final int MAX_SERVERS = 20;
	public static final int MAX_NAME = 40;
	public static final String ALL = "*";
	private static final Set<String> KEYS = Set.of( "id", "name", "url", "builtin", "enabled", "trusted", "allowedtools" );

	public McpServer {
		allowedTools = List.copyOf( allowedTools );
	}

	public boolean allowsAll() {
		return this.allowedTools.contains( ALL );
	}

	public boolean allows( String tool ) {
		return allowsAll() || this.allowedTools.contains( tool );
	}

	public McpServer with( boolean enabled, boolean trusted, List<String> allowed ) {
		return new McpServer( this.id, this.name, this.url, this.builtin, enabled, trusted, allowed );
	}

	public Map<String, Object> toStored() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "id", this.id );
		m.put( "name", this.name );
		m.put( "url", this.url );
		m.put( "builtin", this.builtin );
		m.put( "enabled", this.enabled );
		m.put( "trusted", this.trusted );
		m.put( "allowedTools", this.allowedTools );
		return m;
	}

	/** A name an admin typed: 1 to 40 characters, no control characters. */
	public static String cleanName( String raw ) {
		String n = raw == null ? "" : raw.trim();
		if ( n.isEmpty() || n.length() > MAX_NAME ) {
			throw new IllegalArgumentException( "The name must be 1 to " + MAX_NAME + " characters." );
		}
		for ( int i = 0; i < n.length(); i++ ) {
			if ( Character.isISOControl( n.charAt( i ) ) ) {
				throw new IllegalArgumentException( "The name has a control character." );
			}
		}
		return n;
	}

	/** The id for a name: lower case letters and digits, other characters become single hyphens, at most 24 characters. */
	public static String slug( String name ) {
		StringBuilder sb = new StringBuilder();
		for ( char c : name.toLowerCase( Locale.ROOT ).toCharArray() ) {
			if ( c >= 'a' && c <= 'z' || c >= '0' && c <= '9' ) {
				sb.append( c );
			} else if ( sb.length() > 0 && sb.charAt( sb.length() - 1 ) != '-' ) {
				sb.append( '-' );
			}
			if ( sb.length() >= 24 ) {
				break;
			}
		}
		while ( sb.length() > 0 && sb.charAt( sb.length() - 1 ) == '-' ) {
			sb.setLength( sb.length() - 1 );
		}
		return sb.length() == 0 ? "server" : sb.toString();
	}

	/**
	 * Read one saved entry. The result is checked as strictly as one typed in the console; anything wrong throws, and the caller skips the
	 * entry and reports it.
	 *
	 * @throws IllegalArgumentException what is wrong with the entry
	 */
	public static McpServer fromStored( Object raw ) {
		if ( ! ( raw instanceof Map<?, ?> m ) ) {
			throw new IllegalArgumentException( "An entry is not an object." );
		}
		Map<String, Object> f = new LinkedHashMap<>();
		for ( Map.Entry<?, ?> e : m.entrySet() ) {
			String k = ( e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() ) ).toLowerCase( Locale.ROOT );
			if ( KEYS.contains( k ) ) {
				f.put( k, e.getValue() );
			}
		}
		String id = str( f.get( "id" ) );
		if ( id.isEmpty() || id.length() > 30 || !slug( id ).equals( id ) ) {
			throw new IllegalArgumentException( "The id [" + id + "] is not valid." );
		}
		boolean			builtin	= Boolean.TRUE.equals( f.get( "builtin" ) );
		String			name	= cleanName( str( f.get( "name" ) ) );
		String			url		= McpUrls.check( str( f.get( "url" ) ), McpUrls.NO_RESOLVE );
		boolean			enabled	= Boolean.TRUE.equals( f.get( "enabled" ) );
		boolean			trusted	= Boolean.TRUE.equals( f.get( "trusted" ) );
		List<String>	allowed	= new ArrayList<>();
		if ( f.get( "allowedtools" ) instanceof List<?> l ) {
			for ( Object o : l ) {
				String t = str( o );
				if ( t.isEmpty() || t.length() > 100 ) {
					throw new IllegalArgumentException( "A tool name in the allowed list is not valid." );
				}
				allowed.add( t );
			}
		}
		return new McpServer( id, name, url, builtin, enabled, trusted, allowed );
	}

	private static String str( Object o ) {
		return o == null ? "" : o.toString().trim();
	}

}
