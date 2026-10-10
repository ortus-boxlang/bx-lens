/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Which machine produced a record. Every request, error, report, statistic and audit line carries this identity, because the data of many
 * servers ends up in one place. It is detected once when Lens starts and refreshed at most every few minutes by the watchdog, never per
 * request and never with a DNS lookup on a request thread.
 * <p>
 * The host name comes from the settings, then <code>LENS_SERVER_NAME</code>, then the operating system, then <code>HOSTNAME</code> or
 * <code>COMPUTERNAME</code>. The primary address is the first IPv4 address of an interface that is up, not loopback and not virtual, then IPv6,
 * then <code>127.0.0.1</code>. The instance id is stable for one JVM start: a short hash of the host name and the start time.
 */
public final class ServerIdentity {

	/** How long a detection is kept before the watchdog looks again. */
	public static final long REFRESH_MILLIS = 5 * 60_000L;

	/**
	 * One network interface as the detection sees it. Plain data, so the choice of the primary address can be tested.
	 */
	public record Nic( String name, boolean up, boolean loopback, boolean virtual, List<String> addresses ) {
	}

	/**
	 * What is known about this server.
	 *
	 * @param host      the host name
	 * @param ip        the primary address
	 * @param addresses every address of every usable interface, the primary first
	 * @param id        short id of this instance, stable for one JVM start
	 * @param runtime   the name of the BoxLang runtime instance, or an empty string
	 * @param detected  when it was detected, epoch milliseconds
	 */
	public record Info( String host, String ip, List<String> addresses, String id, String runtime, long detected ) {

		/**
		 * The identity as a map for JSON, a BIF result or a file.
		 */
		public Map<String, Object> toMap() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "host", this.host );
			m.put( "ip", this.ip );
			m.put( "addresses", this.addresses );
			m.put( "id", this.id );
			m.put( "runtime", this.runtime );
			return m;
		}
	}

	private static final long				JVM_START		= System.currentTimeMillis();

	private volatile Info					info;
	private volatile String					nameSetting		= "";
	private volatile String					addressSetting	= "";
	private volatile String					idSetting		= "";
	private final Function<String, String>	env;

	public ServerIdentity() {
		this( System::getenv );
	}

	/**
	 * @param env reads an environment variable, replaced in tests
	 */
	public ServerIdentity( Function<String, String> env ) {
		this.env	= env;
		this.info	= detect( "", "", "", env );
	}

	/**
	 * The cached identity. Never does a lookup.
	 */
	public Info get() {
		return this.info;
	}

	/**
	 * Use these settings (empty means detect) and detect again now. Called when Lens starts and when the settings change, never per request.
	 */
	public void configure( String name, String address, String id ) {
		this.nameSetting	= name == null ? "" : name.trim();
		this.addressSetting	= address == null ? "" : address.trim();
		this.idSetting		= id == null ? "" : id.trim();
		this.info			= detect( this.nameSetting, this.addressSetting, this.idSetting, this.env );
	}

	/**
	 * Look again when the cached identity is older than {@link #REFRESH_MILLIS}. Called by the watchdog.
	 *
	 * @return true when it looked
	 */
	public boolean refreshIfStale() {
		if ( System.currentTimeMillis() - this.info.detected() < REFRESH_MILLIS ) {
			return false;
		}
		this.info = detect( this.nameSetting, this.addressSetting, this.idSetting, this.env );
		return true;
	}

	/**
	 * Detect the identity with the real host and network.
	 */
	public static Info detect( String nameSetting, String addressSetting, String idSetting, Function<String, String> env ) {
		return detect( nameSetting, addressSetting, idSetting, env, hostFromSystem(), nics(), JVM_START );
	}

	/**
	 * Detect the identity from given inputs. The settings win over the environment, which wins over what the machine reports.
	 *
	 * @param osHost the host name the operating system reports, or an empty string
	 * @param nics   the network interfaces
	 */
	public static Info detect( String nameSetting, String addressSetting, String idSetting, Function<String, String> env, String osHost, List<Nic> nics,
	    long startMillis ) {
		String host = clean( nameSetting );
		if ( host.isEmpty() ) {
			host = clean( env.apply( "LENS_SERVER_NAME" ) );
		}
		if ( host.isEmpty() ) {
			host = clean( osHost );
		}
		if ( host.isEmpty() ) {
			host = clean( env.apply( "HOSTNAME" ) );
		}
		if ( host.isEmpty() ) {
			host = clean( env.apply( "COMPUTERNAME" ) );
		}
		if ( host.isEmpty() ) {
			host = "localhost";
		}
		List<String>	all	= addresses( nics );
		String			ip	= clean( addressSetting );
		if ( ip.isEmpty() ) {
			ip = clean( env.apply( "LENS_SERVER_ADDRESS" ) );
		}
		if ( ip.isEmpty() ) {
			ip = primary( nics );
		}
		if ( !all.contains( ip ) ) {
			all = new ArrayList<>( all );
			all.add( 0, ip );
		}
		String id = clean( idSetting );
		if ( id.isEmpty() ) {
			id = clean( env.apply( "LENS_SERVER_ID" ) );
		}
		if ( id.isEmpty() ) {
			id = shortId( host, startMillis );
		}
		String runtime = clean( env.apply( "BOXLANG_INSTANCE_NAME" ) );
		if ( runtime.isEmpty() ) {
			runtime = clean( System.getProperty( "boxlang.instanceName" ) );
		}
		return new Info( host, ip, Collections.unmodifiableList( all ), id, runtime, System.currentTimeMillis() );
	}

	/**
	 * The primary address: the first IPv4 of an interface that is up, not loopback and not virtual, then the first IPv6, else
	 * <code>127.0.0.1</code>. Link local addresses are never primary.
	 */
	public static String primary( List<Nic> nics ) {
		for ( int family : new int[] { 4, 6 } ) {
			for ( Nic n : nics ) {
				if ( !n.up() || n.loopback() || n.virtual() ) {
					continue;
				}
				for ( String a : n.addresses() ) {
					if ( family == 4 ? isIpv4( a ) && !a.startsWith( "169.254." ) : isIpv6( a ) && !isLinkLocal6( a ) ) {
						return a;
					}
				}
			}
		}
		return "127.0.0.1";
	}

	/**
	 * Every address of the usable interfaces, IPv4 before IPv6, loopback and link local left out.
	 */
	public static List<String> addresses( List<Nic> nics ) {
		List<String> v4 = new ArrayList<>(), v6 = new ArrayList<>();
		for ( Nic n : nics ) {
			if ( !n.up() || n.loopback() ) {
				continue;
			}
			for ( String a : n.addresses() ) {
				if ( isIpv4( a ) && !a.startsWith( "169.254." ) && !v4.contains( a ) ) {
					v4.add( a );
				} else if ( isIpv6( a ) && !isLinkLocal6( a ) && !v6.contains( a ) ) {
					v6.add( a );
				}
			}
		}
		// The primary comes first, then the rest
		String			p	= primary( nics );
		List<String>	out	= new ArrayList<>();
		if ( v4.contains( p ) || v6.contains( p ) ) {
			out.add( p );
		}
		for ( String a : v4 ) {
			if ( !out.contains( a ) ) {
				out.add( a );
			}
		}
		for ( String a : v6 ) {
			if ( !out.contains( a ) ) {
				out.add( a );
			}
		}
		return out;
	}

	/**
	 * A short id that is the same for one JVM start and differs between machines and restarts.
	 */
	public static String shortId( String host, long startMillis ) {
		try {
			byte[]			d	= MessageDigest.getInstance( "SHA-256" ).digest( ( host + "|" + startMillis ).getBytes( StandardCharsets.UTF_8 ) );
			StringBuilder	sb	= new StringBuilder();
			for ( int i = 0; i < 4; i++ ) {
				sb.append( Character.forDigit( d[ i ] >> 4 & 15, 16 ) ).append( Character.forDigit( d[ i ] & 15, 16 ) );
			}
			return sb.toString();
		} catch ( Exception e ) {
			return Long.toString( Math.abs( ( host + startMillis ).hashCode() ), 16 );
		}
	}

	private static boolean isIpv4( String a ) {
		return a.indexOf( ':' ) < 0 && a.indexOf( '.' ) > 0;
	}

	private static boolean isIpv6( String a ) {
		return a.indexOf( ':' ) >= 0;
	}

	private static boolean isLinkLocal6( String a ) {
		return a.toLowerCase( Locale.ROOT ).startsWith( "fe80" );
	}

	/**
	 * A value for a header, a log line or a file name: trimmed, without control characters, cut to 128 characters.
	 */
	static String clean( String s ) {
		if ( s == null ) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < s.length() && sb.length() < 128; i++ ) {
			char c = s.charAt( i );
			if ( c >= 0x20 && c != 0x7f ) {
				sb.append( c );
			}
		}
		return sb.toString().trim();
	}

	private static String hostFromSystem() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch ( Throwable t ) {
			return "";
		}
	}

	private static List<Nic> nics() {
		List<Nic> out = new ArrayList<>();
		try {
			List<NetworkInterface> all = Collections.list( NetworkInterface.getNetworkInterfaces() );
			all.sort( Comparator.comparingInt( NetworkInterface::getIndex ) );
			for ( NetworkInterface ni : all ) {
				try {
					List<String> addrs = new ArrayList<>();
					for ( InetAddress a : Collections.list( ni.getInetAddresses() ) ) {
						if ( a instanceof Inet4Address || a instanceof Inet6Address ) {
							String	s		= a.getHostAddress();
							int		scope	= s.indexOf( '%' );
							addrs.add( scope > 0 ? s.substring( 0, scope ) : s );
						}
					}
					out.add( new Nic( ni.getName(), ni.isUp(), ni.isLoopback(), ni.isVirtual(), addrs ) );
				} catch ( Throwable t ) {
					// An interface that cannot be read is skipped
				}
			}
		} catch ( Throwable t ) {
			// No network information
		}
		return out;
	}

}
