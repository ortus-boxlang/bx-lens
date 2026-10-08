/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ClientIpTest {

	private final LensConfig cfg = LensConfig.defaults();

	@Test
	@DisplayName( "a private peer is a trusted proxy, so the header names the client" )
	void trustedProxy() {
		assertThat( ClientIp.resolve( cfg, "10.0.0.5", "203.0.113.9" ) ).isEqualTo( "203.0.113.9" );
		assertThat( ClientIp.resolve( cfg, "127.0.0.1", "203.0.113.9, 10.0.0.7" ) ).isEqualTo( "203.0.113.9" );
	}

	@Test
	@DisplayName( "a public peer cannot make the header count" )
	void publicPeer() {
		assertThat( ClientIp.resolve( cfg, "198.51.100.4", "127.0.0.1" ) ).isEqualTo( "198.51.100.4" );
		assertThat( ClientIp.resolve( cfg, "198.51.100.4", "203.0.113.9" ) ).isEqualTo( "198.51.100.4" );
	}

	@Test
	@DisplayName( "the header cannot turn a remote peer into a loopback caller" )
	void noLoopbackClaim() {
		assertThat( ClientIp.resolve( cfg, "10.0.0.5", "127.0.0.1" ) ).isEqualTo( "10.0.0.5" );
	}

	@Test
	@DisplayName( "the rightmost address that is not a trusted proxy is the client, so a forged left entry is ignored" )
	void forgedEntry() {
		assertThat( ClientIp.resolve( cfg, "10.0.0.5", "1.2.3.4, 203.0.113.9, 10.0.0.7" ) ).isEqualTo( "203.0.113.9" );
	}

	@Test
	@DisplayName( "it can be turned off, or limited to listed proxies" )
	void settings() {
		LensConfig off = new LensConfig( Map.of( "access", Map.of( "trustProxyHeader", false ) ) );
		assertThat( ClientIp.resolve( off, "10.0.0.5", "203.0.113.9" ) ).isEqualTo( "10.0.0.5" );
		LensConfig listed = new LensConfig( Map.of( "access", Map.of( "proxyPeers", "192.168.1.1" ) ) );
		assertThat( ClientIp.resolve( listed, "10.0.0.5", "203.0.113.9" ) ).isEqualTo( "10.0.0.5" );
		assertThat( ClientIp.resolve( listed, "192.168.1.1", "203.0.113.9" ) ).isEqualTo( "203.0.113.9" );
	}

	@Test
	@DisplayName( "ports and IPv6 brackets are handled" )
	void formats() {
		assertThat( ClientIp.resolve( cfg, "10.0.0.5", "203.0.113.9:5521" ) ).isEqualTo( "203.0.113.9" );
		assertThat( ClientIp.resolve( cfg, "10.0.0.5", "[2001:db8::1]:443" ) ).isEqualTo( "2001:db8::1" );
	}

}
