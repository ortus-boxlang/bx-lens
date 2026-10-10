/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.util.ServerIdentity.Info;
import ortus.boxlang.modules.bxlens.util.ServerIdentity.Nic;

public class ServerIdentityTest {

	private static final Function<String, String> NO_ENV = k -> null;

	private static Function<String, String> env( Map<String, String> m ) {
		return m::get;
	}

	private static final List<Nic> NICS = List.of(
	    new Nic( "lo", true, true, false, List.of( "127.0.0.1", "::1" ) ),
	    new Nic( "docker0", true, false, true, List.of( "172.17.0.1" ) ),
	    new Nic( "eth1", false, false, false, List.of( "10.9.9.9" ) ),
	    new Nic( "eth0", true, false, false, List.of( "fe80::1", "10.0.0.7", "2001:db8::7" ) ) );

	@Test
	@DisplayName( "the primary address is the first IPv4 of an interface that is up, not loopback and not virtual" )
	void primaryIpv4() {
		assertThat( ServerIdentity.primary( NICS ) ).isEqualTo( "10.0.0.7" );
	}

	@Test
	@DisplayName( "IPv6 is used when there is no IPv4, link local addresses never are" )
	void primaryIpv6() {
		List<Nic> only6 = List.of( new Nic( "eth0", true, false, false, List.of( "fe80::2", "2001:db8::9" ) ) );
		assertThat( ServerIdentity.primary( only6 ) ).isEqualTo( "2001:db8::9" );
		List<Nic> linkLocal = List.of( new Nic( "eth0", true, false, false, List.of( "169.254.3.4", "fe80::2" ) ) );
		assertThat( ServerIdentity.primary( linkLocal ) ).isEqualTo( "127.0.0.1" );
	}

	@Test
	@DisplayName( "without a usable interface the address is 127.0.0.1" )
	void fallback() {
		assertThat( ServerIdentity.primary( List.of() ) ).isEqualTo( "127.0.0.1" );
		assertThat( ServerIdentity.primary( List.of( new Nic( "lo", true, true, false, List.of( "127.0.0.1" ) ) ) ) ).isEqualTo( "127.0.0.1" );
	}

	@Test
	@DisplayName( "the list of addresses has the primary first, then the rest, without loopback or link local" )
	void addresses() {
		List<String> all = ServerIdentity.addresses( NICS );
		assertThat( all.get( 0 ) ).isEqualTo( "10.0.0.7" );
		assertThat( all ).containsExactly( "10.0.0.7", "172.17.0.1", "2001:db8::7" ).inOrder();
	}

	@Test
	@DisplayName( "settings win over the environment, and the environment over the machine" )
	void overrides() {
		Info set = ServerIdentity.detect( "web-1", "192.0.2.10", "node-a", env( Map.of( "LENS_SERVER_NAME", "x", "LENS_SERVER_ADDRESS", "198.51.100.1" ) ),
		    "os-host",
		    NICS, 1000L );
		assertThat( set.host() ).isEqualTo( "web-1" );
		assertThat( set.ip() ).isEqualTo( "192.0.2.10" );
		assertThat( set.id() ).isEqualTo( "node-a" );
		assertThat( set.addresses() ).contains( "192.0.2.10" );
		Info fromEnv = ServerIdentity.detect( "", "", "", env( Map.of( "LENS_SERVER_NAME", "env-host", "LENS_SERVER_ADDRESS", "198.51.100.1" ) ), "os-host",
		    NICS,
		    1000L );
		assertThat( fromEnv.host() ).isEqualTo( "env-host" );
		assertThat( fromEnv.ip() ).isEqualTo( "198.51.100.1" );
		Info detected = ServerIdentity.detect( "", "", "", NO_ENV, "os-host", NICS, 1000L );
		assertThat( detected.host() ).isEqualTo( "os-host" );
		assertThat( detected.ip() ).isEqualTo( "10.0.0.7" );
	}

	@Test
	@DisplayName( "the host falls back to HOSTNAME, then COMPUTERNAME, then localhost" )
	void hostFallbacks() {
		assertThat( ServerIdentity.detect( "", "", "", env( Map.of( "HOSTNAME", "h1", "COMPUTERNAME", "c1" ) ), "", NICS, 1L ).host() ).isEqualTo( "h1" );
		assertThat( ServerIdentity.detect( "", "", "", env( Map.of( "COMPUTERNAME", "c1" ) ), "", NICS, 1L ).host() ).isEqualTo( "c1" );
		assertThat( ServerIdentity.detect( "", "", "", NO_ENV, "", NICS, 1L ).host() ).isEqualTo( "localhost" );
	}

	@Test
	@DisplayName( "the instance id is stable for one start and differs by host and by start time" )
	void instanceId() {
		String a = ServerIdentity.shortId( "web-1", 5000L );
		assertThat( ServerIdentity.shortId( "web-1", 5000L ) ).isEqualTo( a );
		assertThat( ServerIdentity.shortId( "web-2", 5000L ) ).isNotEqualTo( a );
		assertThat( ServerIdentity.shortId( "web-1", 5001L ) ).isNotEqualTo( a );
		assertThat( a ).hasLength( 8 );
		assertThat( ServerIdentity.detect( "", "", "", NO_ENV, "web-1", NICS, 5000L ).id() ).isEqualTo( a );
	}

	@Test
	@DisplayName( "control characters never reach a header or a log line, and values are capped" )
	void clean() {
		Info i = ServerIdentity.detect( "bad\r\nname" + "x".repeat( 300 ), "", "id\u0000", NO_ENV, "", NICS, 1L );
		assertThat( i.host() ).doesNotContain( "\n" );
		assertThat( i.host().length() ).isAtMost( 128 );
		assertThat( i.id() ).isEqualTo( "id" );
	}

	@Test
	@DisplayName( "the identity is cached: get never changes until configure or a stale refresh" )
	void cached() {
		ServerIdentity	s		= new ServerIdentity( NO_ENV );
		Info			first	= s.get();
		assertThat( s.get() ).isSameInstanceAs( first );
		assertThat( s.refreshIfStale() ).isFalse();
		assertThat( s.get() ).isSameInstanceAs( first );
		s.configure( "named", "192.0.2.1", "fixed" );
		assertThat( s.get().host() ).isEqualTo( "named" );
		assertThat( s.get().id() ).isEqualTo( "fixed" );
		assertThat( s.get().toMap() ).containsKey( "addresses" );
	}

	@Test
	@DisplayName( "real detection returns something usable on any machine" )
	void real() {
		Info i = ServerIdentity.detect( "", "", "", System::getenv );
		assertThat( i.host() ).isNotEmpty();
		assertThat( i.ip() ).isNotEmpty();
		assertThat( i.id() ).isNotEmpty();
		assertThat( i.addresses() ).isNotNull();
	}

}
