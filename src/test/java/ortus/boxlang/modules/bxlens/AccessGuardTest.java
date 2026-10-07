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

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AccessGuardTest {

	private AccessGuard guard( Map<String, Object> access ) {
		return new AccessGuard( new LensConfig( Map.of( "access", access ) ) );
	}

	@Test
	@DisplayName( "Default policy allows loopback and private networks only" )
	public void defaultPolicy() {
		AccessGuard g = new AccessGuard( LensConfig.defaults() );
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "::1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "0:0:0:0:0:0:0:1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "192.168.1.20", null, null ) ).isTrue();
		assertThat( g.isAllowed( "10.4.5.6", null, null ) ).isTrue();
		assertThat( g.isAllowed( "172.20.0.3", null, null ) ).isTrue();
		assertThat( g.isAllowed( "fd12:3456::1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "8.8.8.8", null, null ) ).isFalse();
		assertThat( g.isAllowed( "203.0.113.9", null, null ) ).isFalse();
		assertThat( g.isAllowed( "172.32.0.1", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "Private networks can be switched off" )
	public void privateOff() {
		AccessGuard g = guard( Map.of( "allowPrivateNetworks", false, "allowedIPs", List.of( "127.0.0.1" ) ) );
		assertThat( g.isAllowed( "127.0.0.1", null, null ) ).isTrue();
		assertThat( g.isAllowed( "192.168.1.20", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "Exact IPs and CIDR ranges are honored" )
	public void exactAndCidr() {
		AccessGuard g = guard( Map.of( "allowPrivateNetworks", false, "allowedIPs", List.of( "203.0.113.9", "198.51.100.0/24", "2001:db8::/32" ) ) );
		assertThat( g.isAllowed( "203.0.113.9", null, null ) ).isTrue();
		assertThat( g.isAllowed( "203.0.113.10", null, null ) ).isFalse();
		assertThat( g.isAllowed( "198.51.100.77", null, null ) ).isTrue();
		assertThat( g.isAllowed( "198.51.101.1", null, null ) ).isFalse();
		assertThat( g.isAllowed( "2001:db8:1::5", null, null ) ).isTrue();
		assertThat( g.isAllowed( "2001:db9::1", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "A star allows everyone" )
	public void star() {
		assertThat( guard( Map.of( "allowedIPs", List.of( "*" ) ) ).isAllowed( "8.8.8.8", null, null ) ).isTrue();
	}

	@Test
	@DisplayName( "Garbage and host names never trigger a lookup and are denied" )
	public void garbage() {
		AccessGuard g = new AccessGuard( LensConfig.defaults() );
		assertThat( g.isAllowed( null, null, null ) ).isFalse();
		assertThat( g.isAllowed( "", null, null ) ).isFalse();
		assertThat( g.isAllowed( "localhost", null, null ) ).isFalse();
		assertThat( g.isAllowed( "evil.example.com", null, null ) ).isFalse();
	}

	@Test
	@DisplayName( "Allowed hosts restrict the Host header, ignoring the port" )
	public void hosts() {
		AccessGuard g = guard( Map.of( "allowedHosts", List.of( "dev.local" ) ) );
		assertThat( g.isAllowed( "127.0.0.1", "dev.local:8080", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "DEV.LOCAL", null ) ).isTrue();
		assertThat( g.isAllowed( "127.0.0.1", "other.local", null ) ).isFalse();
	}

	@Test
	@DisplayName( "A required header must be present, and match when a value is given" )
	public void requiredHeader() {
		AccessGuard named = guard( Map.of( "requireHeader", "X-Lens" ) );
		assertThat( named.requiredHeader() ).isEqualTo( "X-Lens" );
		assertThat( named.isAllowed( "127.0.0.1", null, "anything" ) ).isTrue();
		assertThat( named.isAllowed( "127.0.0.1", null, null ) ).isFalse();
		AccessGuard valued = guard( Map.of( "requireHeader", "X-Lens=secret" ) );
		assertThat( valued.requiredHeader() ).isEqualTo( "X-Lens" );
		assertThat( valued.isAllowed( "127.0.0.1", null, "secret" ) ).isTrue();
		assertThat( valued.isAllowed( "127.0.0.1", null, "nope" ) ).isFalse();
	}

}
