/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AccessGuardTest {

	private AccessGuard bar( Map<String, Object> settings, boolean allowAll ) {
		return new AccessGuard( new LensConfig( settings ), "bar.access", allowAll );
	}

	private AccessGuard rule( Object access ) {
		return bar( Map.of( "bar", Map.of( "access", access ) ), true );
	}

	@Test
	@DisplayName( "The default rule is loopback only" )
	public void defaultIsLocal() {
		AccessGuard g = new AccessGuard( LensConfig.defaults(), "bar.access", false );
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "::1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "0:0:0:0:0:0:0:1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "192.168.1.20", null, null ) ).isFalse();
		assertThat( g.isAllowed( "10.4.5.6", null, null ) ).isFalse();
		assertThat( g.isAllowed( "8.8.8.8", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "The private keyword allows private networks too" )
	public void privateKeyword() {
		AccessGuard g = rule( List.of( "private" ) );
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "192.168.1.20", null, null ) ).isTrue();
		assertThat( g.isAllowed( "10.4.5.6", null, null ) ).isTrue();
		assertThat( g.isAllowed( "172.20.0.3", null, null ) ).isTrue();
		assertThat( g.isAllowed( "fd12:3456::1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "172.32.0.1", null, null ) ).isFalse();
		assertThat( g.isAllowed( "203.0.113.9", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "Exact IPs and CIDR ranges are honored" )
	public void exactAndCidr() {
		AccessGuard g = rule( List.of( "203.0.113.9", "198.51.100.0/24", "2001:db8::/32" ) );
		assertThat( g.isAllowed( "203.0.113.9", null, null ) ).isTrue();
		assertThat( g.isAllowed( "203.0.113.10", null, null ) ).isFalse();
		assertThat( g.isAllowed( "198.51.100.77", null, null ) ).isTrue();
		assertThat( g.isAllowed( "198.51.101.1", null, null ) ).isFalse();
		assertThat( g.isAllowed( "2001:db8:1::5", null, null ) ).isTrue();
		assertThat( g.isAllowed( "2001:db9::1", null, null ) ).isFalse();
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "All lets everyone in when it is permitted" )
	public void allPermitted() {
		AccessGuard g = rule( "all" );
		assertThat( g.isOpenToAll() ).isTrue();
		assertThat( g.isDowngraded() ).isFalse();
		assertThat( g.isAllowed( "8.8.8.8", null, null ) ).isTrue();
	}

	@Test
	@DisplayName( "All without the second key falls back to loopback and says so" )
	public void allDowngraded() {
		AccessGuard g = bar( Map.of( "bar", Map.of( "access", "all" ) ), false );
		assertThat( g.isDowngraded() ).isTrue();
		assertThat( g.isOpenToAll() ).isFalse();
		assertThat( g.isAllowed( "8.8.8.8", null, null ) ).isFalse();
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isTrue();
	}

	@Test
	@DisplayName( "Garbage and host names never trigger a lookup and are denied" )
	public void garbage() {
		AccessGuard g = new AccessGuard( LensConfig.defaults(), "bar.access", false );
		assertThat( g.isAllowed( null, null, null ) ).isFalse();
		assertThat( g.isAllowed( "", null, null ) ).isFalse();
		assertThat( g.isAllowed( "localhost", null, null ) ).isFalse();
		assertThat( g.isAllowed( "evil.example.com", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "Allowed hosts restrict the Host header, ignoring the port" )
	public void hosts() {
		AccessGuard g = bar( Map.of( "access", Map.of( "allowedHosts", List.of( "dev.local" ) ) ), false );
		assertThat( g.isAllowed( "127.0.0.1", "dev.local:8080", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "DEV.LOCAL", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "other.local", null ) ).isFalse();
	}

	@Test
	@DisplayName( "A required header must be present, and match when a value is given" )
	public void requiredHeader() {
		AccessGuard named = bar( Map.of( "access", Map.of( "requireHeader", "X-Lens" ) ), false );
		assertThat( named.requiredHeader() ).isEqualTo( "X-Lens" );
		assertThat( named.isAllowed( "127.0.0.1", null, "anything" ) ).isTrue();
		assertThat( named.isAllowed( "127.0.0.1", null, null ) ).isFalse();
		AccessGuard valued = bar( Map.of( "access", Map.of( "requireHeader", "X-Lens=secret" ) ), false );
		assertThat( valued.requiredHeader() ).isEqualTo( "X-Lens" );
		assertThat( valued.isAllowed( "127.0.0.1", null, "secret" ) ).isTrue();
		assertThat( valued.isAllowed( "127.0.0.1", null, "nope" ) ).isFalse();
	}

	@Test
	@DisplayName( "A local only guard accepts only loopback Host names, which stops DNS rebinding" )
	public void rebinding() {
		AccessGuard g = new AccessGuard( LensConfig.defaults(), "bar.access", false );
		assertThat( g.isAllowed( "127.0.0.1", "localhost:8080", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "127.0.0.1", null ) ).isTrue();
		assertThat( g.isAllowed( "::1", "[::1]:8080", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "LOCALHOST", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "evil.example.com", null ) ).isFalse();
		assertThat( g.isAllowed( "127.0.0.1", "evil.example.com:80", null ) ).isFalse();
		assertThat( g.isAllowed( "127.0.0.1", "localhost.evil.com", null ) ).isFalse();
	}

	@Test
	@DisplayName( "access.allowedHosts widens a local guard, and other rules are not limited to loopback names" )
	public void widen() {
		AccessGuard widened = bar( Map.of( "access", Map.of( "allowedHosts", List.of( "dev.local" ) ) ), false );
		assertThat( widened.isAllowed( "127.0.0.1", "dev.local", null ) ).isTrue();
		assertThat( widened.isAllowed( "127.0.0.1", "localhost", null ) ).isFalse();
		AccessGuard lan = rule( List.of( "private" ) );
		assertThat( lan.isAllowed( "192.168.1.5", "intranet.corp", null ) ).isTrue();
	}

}
