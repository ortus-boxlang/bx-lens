/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * The rules for the address of an MCP server. An admin types the address, so it is checked as untrusted input, on the server, when it is
 * saved and again before every connection:
 * <ul>
 * <li>https only. Plain http is accepted only for a loopback host (localhost, 127.x.x.x, [::1]), which is how a server on this machine is
 * reached and how the test harness works.</li>
 * <li>no user name or password in the address, no fragment, at most 500 characters, a host, a port from 1 to 65535.</li>
 * <li>every address the host name resolves to must be public. A host that resolves to a private (10/8, 172.16/12, 192.168/16),
 * loopback, link-local (169.254/16, which holds the cloud metadata address), unique local (fc00::/7), carrier-grade NAT (100.64/10),
 * multicast or unspecified address is refused. A name that is not a loopback literal but resolves to loopback is refused too.</li>
 * </ul>
 * The resolver is a seam so tests can use fake names. A DNS answer can change between this check and the connection (rebinding); the check is
 * repeated immediately before each connection and redirects are never followed, which leaves a very small window. The documentation says so.
 */
public final class McpUrls {

	public static final int MAX_LENGTH = 500;

	/** Resolves a host name to its addresses. */
	@FunctionalInterface
	public interface Resolver {

		InetAddress[] resolve( String host ) throws UnknownHostException;
	}

	/** The system resolver. */
	public static final Resolver	SYSTEM		= InetAddress::getAllByName;

	/** Pass this to {@link #check} to test the form of an address only, without resolving the host (used when the saved list is read). */
	public static final Resolver	NO_RESOLVE	= host -> new InetAddress[ 0 ];

	private McpUrls() {
	}

	/**
	 * Check an address and return it normalized (trimmed).
	 *
	 * @throws IllegalArgumentException with a message an admin can act on
	 */
	public static String check( String raw, Resolver resolver ) {
		if ( raw == null || raw.isBlank() ) {
			throw new IllegalArgumentException( "The address is empty." );
		}
		String url = raw.trim();
		if ( url.length() > MAX_LENGTH ) {
			throw new IllegalArgumentException( "The address is longer than " + MAX_LENGTH + " characters." );
		}
		for ( int i = 0; i < url.length(); i++ ) {
			char c = url.charAt( i );
			if ( c <= 0x20 || c == 0x7f || c == '\\' ) {
				throw new IllegalArgumentException( "The address has a space or a control character." );
			}
		}
		URI uri;
		try {
			uri = new URI( url );
		} catch ( URISyntaxException e ) {
			throw new IllegalArgumentException( "The address is not a valid URL." );
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase( Locale.ROOT );
		if ( !scheme.equals( "https" ) && !scheme.equals( "http" ) ) {
			throw new IllegalArgumentException( "The address must start with https://." );
		}
		if ( uri.getRawUserInfo() != null ) {
			throw new IllegalArgumentException( "The address must not hold a user name or password." );
		}
		if ( uri.getRawFragment() != null ) {
			throw new IllegalArgumentException( "The address must not have a # fragment." );
		}
		String host = uri.getHost();
		if ( host == null || host.isBlank() ) {
			throw new IllegalArgumentException( "The address has no host name." );
		}
		if ( uri.getPort() == 0 || uri.getPort() > 65535 ) {
			throw new IllegalArgumentException( "The port must be from 1 to 65535." );
		}
		boolean loopback = isLoopbackLiteral( host );
		if ( scheme.equals( "http" ) && !loopback ) {
			throw new IllegalArgumentException( "Only https:// is accepted. Plain http is allowed only for localhost." );
		}
		if ( !loopback && resolver != NO_RESOLVE ) {
			InetAddress[] found;
			try {
				found = ( resolver == null ? SYSTEM : resolver ).resolve( stripBrackets( host ) );
			} catch ( UnknownHostException e ) {
				throw new IllegalArgumentException( "The host name [" + host + "] cannot be resolved." );
			}
			if ( found == null || found.length == 0 ) {
				throw new IllegalArgumentException( "The host name [" + host + "] cannot be resolved." );
			}
			for ( InetAddress a : found ) {
				String why = notPublic( a );
				if ( why != null ) {
					throw new IllegalArgumentException( "The host [" + host + "] resolves to " + why + " address " + a.getHostAddress()
					    + ". Only public addresses are accepted." );
				}
			}
		}
		return url;
	}

	/** Is this host name the loopback literal localhost, 127.x.x.x (four numbers) or [::1]? */
	static boolean isLoopbackLiteral( String host ) {
		String h = stripBrackets( host ).toLowerCase( Locale.ROOT );
		if ( h.equals( "localhost" ) || h.equals( "::1" ) || h.equals( "0:0:0:0:0:0:0:1" ) ) {
			return true;
		}
		java.util.List<String> parts = dots( h );
		if ( parts.size() != 4 || !parts.get( 0 ).equals( "127" ) ) {
			return false;
		}
		for ( String p : parts ) {
			if ( p.isEmpty() || p.length() > 3 ) {
				return false;
			}
			for ( int i = 0; i < p.length(); i++ ) {
				if ( p.charAt( i ) < '0' || p.charAt( i ) > '9' ) {
					return false;
				}
			}
			if ( Integer.parseInt( p ) > 255 ) {
				return false;
			}
		}
		return true;
	}

	private static java.util.List<String> dots( String h ) {
		java.util.List<String>	out		= new java.util.ArrayList<>();
		int						from	= 0;
		for ( int i = h.indexOf( '.' ); i >= 0; i = h.indexOf( '.', from ) ) {
			out.add( h.substring( from, i ) );
			from = i + 1;
		}
		out.add( h.substring( from ) );
		return out;
	}

	private static String stripBrackets( String h ) {
		return h.startsWith( "[" ) && h.endsWith( "]" ) ? h.substring( 1, h.length() - 1 ) : h;
	}

	/**
	 * Why an address is not public, or null when it is.
	 */
	static String notPublic( InetAddress a ) {
		if ( a.isAnyLocalAddress() ) {
			return "an unspecified";
		}
		if ( a.isLoopbackAddress() ) {
			return "a loopback";
		}
		if ( a.isLinkLocalAddress() ) {
			return "a link-local (metadata)";
		}
		if ( a.isSiteLocalAddress() ) {
			return "a private";
		}
		if ( a.isMulticastAddress() ) {
			return "a multicast";
		}
		byte[] b = a.getAddress();
		if ( a instanceof Inet4Address ) {
			int b0 = b[ 0 ] & 0xff, b1 = b[ 1 ] & 0xff;
			if ( b0 == 0 ) {
				return "an unspecified";
			}
			if ( b0 == 100 && b1 >= 64 && b1 <= 127 ) {
				return "a carrier-grade NAT";
			}
			if ( b0 == 169 && b1 == 254 ) {
				return "a link-local (metadata)";
			}
			if ( b0 == 192 && b1 == 0 && ( b[ 2 ] & 0xff ) == 0 ) {
				return "a reserved";
			}
			if ( b0 == 198 && ( b1 == 18 || b1 == 19 ) ) {
				return "a benchmarking";
			}
			if ( b0 >= 240 ) {
				return "a reserved";
			}
		} else if ( a instanceof Inet6Address ) {
			if ( ( b[ 0 ] & 0xfe ) == 0xfc ) {
				return "a unique local";
			}
			if ( ( b[ 0 ] & 0xff ) == 0xfe && ( b[ 1 ] & 0xc0 ) == 0xc0 ) {
				return "a private (site-local)";
			}
		}
		return null;
	}

}
