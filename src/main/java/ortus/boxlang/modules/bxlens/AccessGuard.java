/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a caller may see the bar or the console. One guard per surface, built from a rule at a settings path such as
 * <code>bar.access</code> or <code>console.access</code>.
 * <p>
 * The rule is <code>"local"</code> (loopback only, the default), <code>"all"</code>, or a list of exact IPs, CIDR ranges and the
 * keywords <code>local</code> and <code>private</code> (10/8, 172.16/12, 192.168/16, fc00::/7 and link-local). The shared extras
 * <code>access.allowedHosts</code> (Host header names, empty means any) and <code>access.requireHeader</code> (<code>Name</code> or
 * <code>Name=value</code>) apply on top of the rule.
 */
public final class AccessGuard {

	private final List<String>	rules;
	private final boolean		allowAll;
	private final boolean		downgraded;
	private final List<String>	allowedHosts;
	/** The rule is exactly "local": then only a loopback Host name is accepted unless access.allowedHosts says otherwise. */
	private final boolean		localOnly;
	private final String		requireHeaderName;
	private final String		requireHeaderValue;

	/**
	 * @param config            the settings
	 * @param rulePath          dotted path of the rule, for example <code>bar.access</code>
	 * @param allowAllPermitted may the rule be <code>all</code>? When false an <code>all</code> rule is replaced by <code>local</code>
	 */
	public AccessGuard( LensConfig config, String rulePath, boolean allowAllPermitted ) {
		Object			raw		= config.get( rulePath );
		List<String>	list	= new ArrayList<>();
		boolean			all		= false;
		if ( raw instanceof Collection<?> c ) {
			for ( Object o : c ) {
				if ( o != null && !o.toString().isBlank() ) {
					list.add( o.toString().trim() );
				}
			}
		} else if ( raw != null && !raw.toString().isBlank() ) {
			list.add( raw.toString().trim() );
		}
		if ( list.isEmpty() ) {
			list.add( "local" );
		}
		for ( String r : list ) {
			if ( "all".equalsIgnoreCase( r ) || "*".equals( r ) ) {
				all = true;
			}
		}
		this.downgraded = all && !allowAllPermitted;
		if ( this.downgraded ) {
			this.allowAll	= false;
			this.rules		= List.of( "local" );
		} else {
			this.allowAll	= all;
			this.rules		= list;
		}
		this.localOnly = !this.allowAll && this.rules.size() == 1 && "local".equalsIgnoreCase( this.rules.get( 0 ) );
		List<String> hosts = new ArrayList<>();
		for ( String h : config.getList( "access.allowedHosts", List.of() ) ) {
			hosts.add( h.toLowerCase( Locale.ROOT ) );
		}
		this.allowedHosts = hosts;
		String rh = config.getString( "access.requireHeader", "" );
		if ( rh.contains( "=" ) ) {
			this.requireHeaderName	= rh.substring( 0, rh.indexOf( '=' ) ).trim();
			this.requireHeaderValue	= rh.substring( rh.indexOf( '=' ) + 1 ).trim();
		} else {
			this.requireHeaderName	= rh.trim();
			this.requireHeaderValue	= null;
		}
	}

	/**
	 * True when the rule asked for <code>all</code> but that was not permitted, so <code>local</code> applies.
	 */
	public boolean isDowngraded() {
		return downgraded;
	}

	/**
	 * Does this guard let everyone in?
	 */
	public boolean isOpenToAll() {
		return allowAll;
	}

	/**
	 * Check a caller.
	 *
	 * @param remoteAddr  remote IP address as text
	 * @param host        Host header value or null
	 * @param headerValue value of the required header (resolved by the caller from {@link #requiredHeader()}) or null
	 *
	 * @return true when the caller is allowed
	 */
	public boolean isAllowed( String remoteAddr, String host, String headerValue ) {
		if ( !requireHeaderName.isEmpty() ) {
			if ( headerValue == null || ( requireHeaderValue != null && !requireHeaderValue.equals( headerValue ) ) ) {
				return false;
			}
		}
		if ( !allowedHosts.isEmpty() ) {
			if ( !allowedHosts.contains( hostName( host ) ) ) {
				return false;
			}
		} else if ( localOnly ) {
			// DNS rebinding: a page on another site can point its own name at 127.0.0.1. The Host header still carries that name, so a
			// local-only guard accepts only a loopback name. Widen it with access.allowedHosts.
			String h = hostName( host );
			if ( !h.isEmpty() && !h.equals( "localhost" ) && !h.equals( "127.0.0.1" ) && !h.equals( "[::1]" ) ) {
				return false;
			}
		}
		if ( allowAll ) {
			return true;
		}
		InetAddress addr = parse( remoteAddr );
		if ( addr == null ) {
			return false;
		}
		return matchesAny( rules, addr );
	}

	/**
	 * The host name of a Host header: lower case, without the port. An IPv6 literal keeps its brackets.
	 */
	static String hostName( String host ) {
		String h = host == null ? "" : host.trim().toLowerCase( Locale.ROOT );
		if ( h.startsWith( "[" ) ) {
			int close = h.indexOf( ']' );
			return close > 0 ? h.substring( 0, close + 1 ) : h;
		}
		int colon = h.lastIndexOf( ':' );
		if ( colon > 0 && h.indexOf( ':' ) == colon ) {
			h = h.substring( 0, colon );
		}
		return h;
	}

	/**
	 * Does an address match any of the rules? Rules are exact IPs, CIDR ranges and the words <code>local</code> and <code>private</code>.
	 */
	public static boolean matchesAny( List<String> rules, String addressText ) {
		InetAddress addr = parse( addressText );
		if ( addr == null ) {
			return false;
		}
		return matchesAny( rules, addr );
	}

	private static boolean matchesAny( List<String> rules, InetAddress addr ) {
		for ( String rule : rules ) {
			if ( "local".equalsIgnoreCase( rule ) ) {
				if ( addr.isLoopbackAddress() ) {
					return true;
				}
			} else if ( "private".equalsIgnoreCase( rule ) ) {
				if ( addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || isUniqueLocalV6( addr ) ) {
					return true;
				}
			} else if ( matches( rule, addr ) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Is the text an IP address literal (IPv4 or IPv6)? No name lookup is ever made.
	 */
	public static boolean isIpLiteral( String text ) {
		return parse( text ) != null;
	}

	/**
	 * Is this a loopback address?
	 */
	public static boolean isLoopback( String addressText ) {
		InetAddress addr = parse( addressText );
		return addr != null && addr.isLoopbackAddress();
	}

	/**
	 * Name of the header the caller must send, or empty.
	 */
	public String requiredHeader() {
		return requireHeaderName;
	}

	private static boolean isUniqueLocalV6( InetAddress addr ) {
		byte[] b = addr.getAddress();
		return b.length == 16 && ( b[ 0 ] & 0xFE ) == 0xFC;
	}

	private static InetAddress parse( String text ) {
		if ( text == null || text.isBlank() ) {
			return null;
		}
		String t = text.trim();
		if ( t.startsWith( "[" ) && t.contains( "]" ) ) {
			t = t.substring( 1, t.indexOf( ']' ) );
		}
		int pct = t.indexOf( '%' );
		if ( pct > 0 ) {
			t = t.substring( 0, pct );
		}
		// Only literal IPs, never trigger a DNS lookup
		for ( int i = 0; i < t.length(); i++ ) {
			char c = t.charAt( i );
			if ( Character.digit( c, 16 ) < 0 && c != ':' && c != '.' ) {
				return null;
			}
		}
		if ( t.isEmpty() ) {
			return null;
		}
		if ( t.indexOf( ':' ) < 0 ) {
			// IPv4 only: digits and dots. A bare word of hex letters could otherwise be looked up as a host name
			if ( t.indexOf( '.' ) < 0 ) {
				return null;
			}
			for ( int i = 0; i < t.length(); i++ ) {
				if ( t.charAt( i ) != '.' && !Character.isDigit( t.charAt( i ) ) ) {
					return null;
				}
			}
		}
		try {
			return InetAddress.getByName( t );
		} catch ( Exception e ) {
			return null;
		}
	}

	private static boolean matches( String rule, InetAddress addr ) {
		try {
			if ( rule.contains( "/" ) ) {
				String		base	= rule.substring( 0, rule.indexOf( '/' ) );
				int			bits	= Integer.parseInt( rule.substring( rule.indexOf( '/' ) + 1 ) );
				InetAddress	net		= parse( base );
				if ( net == null || net.getAddress().length != addr.getAddress().length ) {
					return false;
				}
				int			total	= net.getAddress().length * 8;
				BigInteger	mask	= BigInteger.ONE.shiftLeft( total ).subtract( BigInteger.ONE ).shiftRight( total - bits ).shiftLeft( total - bits );
				return new BigInteger( 1, net.getAddress() ).and( mask ).equals( new BigInteger( 1, addr.getAddress() ).and( mask ) );
			}
			InetAddress other = parse( rule );
			return other != null && other.equals( addr );
		} catch ( Exception e ) {
			return false;
		}
	}

}
