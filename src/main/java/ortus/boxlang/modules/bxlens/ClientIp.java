/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the real client address when the server sits behind a proxy or load balancer. The proxy header (default
 * <code>X-Forwarded-For</code>) is only believed when the direct connection comes from a trusted proxy peer (<code>access.proxyPeers</code>,
 * default <code>private</code>: loopback and private networks). A header can never claim a loopback address for a peer that is not loopback.
 */
public final class ClientIp {

	private ClientIp() {
	}

	/**
	 * Is the proxy header honored for this connection?
	 */
	public static boolean trustsProxy( LensConfig cfg, String peer ) {
		return cfg.getBool( "access.trustProxyHeader", true ) && AccessGuard.matchesAny( peers( cfg ), peer );
	}

	/**
	 * The client address.
	 *
	 * @param peer        the address of the direct connection
	 * @param headerValue the value of the proxy header or null
	 */
	public static String resolve( LensConfig cfg, String peer, String headerValue ) {
		if ( headerValue == null || headerValue.isBlank() || !trustsProxy( cfg, peer ) ) {
			return peer;
		}
		List<String>	trusted		= peers( cfg );
		String[]		parts		= headerValue.split( "," );
		String			leftmost	= null;
		for ( int i = parts.length - 1; i >= 0; i-- ) {
			String ip = clean( parts[ i ] );
			if ( ip.isEmpty() ) {
				continue;
			}
			leftmost = ip;
			if ( !AccessGuard.matchesAny( trusted, ip ) ) {
				return accept( peer, ip );
			}
		}
		return leftmost == null ? peer : accept( peer, leftmost );
	}

	private static String accept( String peer, String candidate ) {
		// A forwarded header must not turn a remote peer into a loopback caller
		if ( AccessGuard.isLoopback( candidate ) && !AccessGuard.isLoopback( peer ) ) {
			return peer;
		}
		return candidate;
	}

	private static String clean( String raw ) {
		String t = raw.trim();
		if ( t.startsWith( "[" ) && t.contains( "]" ) ) {
			return t.substring( 1, t.indexOf( ']' ) );
		}
		// IPv4 with a port
		if ( t.indexOf( ':' ) > 0 && t.indexOf( ':' ) == t.lastIndexOf( ':' ) ) {
			return t.substring( 0, t.indexOf( ':' ) );
		}
		return t;
	}

	private static List<String> peers( LensConfig cfg ) {
		List<String> l = new ArrayList<>( cfg.getList( "access.proxyPeers", List.of() ) );
		if ( l.isEmpty() ) {
			String single = cfg.getString( "access.proxyPeers", "private" );
			l.add( single.isBlank() ? "private" : single );
		}
		return l;
	}

}
