/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.bxlens;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a caller may see (and be tracked by) Lens. Default policy: loopback and private networks only.
 * <p>
 * Settings read: <code>access.allowedIPs</code> (exact IP, CIDR or <code>*</code>), <code>access.allowPrivateNetworks</code>,
 * <code>access.allowedHosts</code> (request Host header names, empty = any) and <code>access.requireHeader</code> (<code>Name</code> or
 * <code>Name=value</code>).
 */
public final class AccessGuard {

	private final List<String>	allowedIPs;
	private final boolean		allowPrivate;
	private final List<String>	allowedHosts;
	private final String		requireHeaderName;
	private final String		requireHeaderValue;
	private final boolean		allowAll;

	public AccessGuard( LensConfig config ) {
		List<String> ips = new ArrayList<>( config.getList( "access.allowedIPs", List.of( "127.0.0.1", "::1" ) ) );
		this.allowAll		= ips.contains( "*" );
		this.allowedIPs		= ips;
		this.allowPrivate	= config.getBool( "access.allowPrivateNetworks", true );
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
			String	h		= host == null ? "" : host.toLowerCase( Locale.ROOT );
			int		colon	= h.lastIndexOf( ':' );
			if ( colon > 0 && h.indexOf( ':' ) == colon ) {
				h = h.substring( 0, colon );
			}
			if ( !allowedHosts.contains( h ) ) {
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
		if ( allowPrivate && ( addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || isUniqueLocalV6( addr ) ) ) {
			return true;
		}
		for ( String rule : allowedIPs ) {
			if ( matches( rule, addr ) ) {
				return true;
			}
		}
		return false;
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
		if ( !t.matches( "[0-9a-fA-F:.]+" ) ) {
			return null;
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
