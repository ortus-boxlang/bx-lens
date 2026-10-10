/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules for the address of an MCP server. Names are resolved by a fake resolver, so no test needs a network.
 */
public class McpUrlsTest {

	/** Known names map to addresses; anything else must be an address literal. */
	private static final Map<String, String> NAMES = Map.of( "docs.example.com", "93.184.216.34", "intranet.example.com", "10.1.2.3", "lan.example.com",
	    "192.168.1.20", "meta.example.com", "169.254.169.254", "rebind.example.com", "127.0.0.1", "ula.example.com", "fd12:3456::1", "cgnat.example.com",
	    "100.64.1.1", "v6.example.com", "2606:4700::1111", "linklocal6.example.com", "fe80::1" );

	private static final McpUrls.Resolver RESOLVER = host -> {
		String ip = NAMES.get( host );
		if ( ip != null ) {
			return new InetAddress[] { InetAddress.getByName( ip ) };
		}
		if ( host.indexOf( '.' ) > 0 && host.chars().allMatch( c -> c >= '0' && c <= '9' || c == '.' ) || host.indexOf( ':' ) >= 0 ) {
			return new InetAddress[] { InetAddress.getByName( host ) };
		}
		throw new UnknownHostException( host );
	};

	private static String why( String url ) {
		return assertThrows( IllegalArgumentException.class, () -> McpUrls.check( url, RESOLVER ) ).getMessage();
	}

	@Test
	@DisplayName( "a public https address is accepted and returned trimmed" )
	void publicHttps() {
		assertThat( McpUrls.check( "  https://docs.example.com/mcp  ", RESOLVER ) ).isEqualTo( "https://docs.example.com/mcp" );
		assertThat( McpUrls.check( "https://docs.example.com:8443/a/b?x=1", RESOLVER ) ).isEqualTo( "https://docs.example.com:8443/a/b?x=1" );
		assertThat( McpUrls.check( "https://v6.example.com/mcp", RESOLVER ) ).isEqualTo( "https://v6.example.com/mcp" );
		assertThat( McpUrls.check( "https://93.184.216.34/mcp", RESOLVER ) ).isEqualTo( "https://93.184.216.34/mcp" );
	}

	@Test
	@DisplayName( "plain http is accepted for loopback only" )
	void httpOnlyForLoopback() {
		assertThat( McpUrls.check( "http://localhost:11435/mcp", RESOLVER ) ).isNotEmpty();
		assertThat( McpUrls.check( "http://127.0.0.1:11435/mcp", RESOLVER ) ).isNotEmpty();
		assertThat( McpUrls.check( "http://127.5.5.5/mcp", RESOLVER ) ).isNotEmpty();
		assertThat( McpUrls.check( "http://[::1]:9/mcp", RESOLVER ) ).isNotEmpty();
		assertThat( McpUrls.check( "https://localhost/mcp", RESOLVER ) ).isNotEmpty();
		assertThat( why( "http://docs.example.com/mcp" ) ).contains( "https" );
		assertThat( why( "http://93.184.216.34/mcp" ) ).contains( "https" );
		assertThat( why( "http://127.0.0.1.evil.example.com/mcp" ) ).contains( "https" );
		assertThat( why( "http://localhost.evil.example.com/mcp" ) ).contains( "https" );
	}

	@Test
	@DisplayName( "other schemes, empty, long, spaced, credentials, fragments and a missing host are refused" )
	void shape() {
		assertThat( why( "" ) ).contains( "empty" );
		assertThat( why( "   " ) ).contains( "empty" );
		assertThat( why( "ftp://docs.example.com/x" ) ).contains( "https" );
		assertThat( why( "file:///etc/passwd" ) ).contains( "https" );
		assertThat( why( "docs.example.com/mcp" ) ).contains( "https" );
		assertThat( why( "javascript:alert(1)" ) ).contains( "https" );
		assertThat( why( "https://user:pw@docs.example.com/mcp" ) ).contains( "user name or password" );
		assertThat( why( "https://user@docs.example.com/mcp" ) ).contains( "user name or password" );
		assertThat( why( "https://docs.example.com/mcp#frag" ) ).contains( "fragment" );
		assertThat( why( "https://docs.example.com/a b" ) ).contains( "space" );
		assertThat( why( "https://docs.example.com/a\u0007b" ) ).contains( "control" );
		assertThat( why( "https://docs.example.com/a\\b" ) ).contains( "control" );
		assertThat( why( "https:///mcp" ) ).isNotEmpty();
		assertThat( why( "https://docs.example.com:0/mcp" ) ).contains( "port" );
		assertThat( why( "https://docs.example.com:70000/mcp" ) ).isNotEmpty();
		assertThat( why( "https://docs.example.com/" + "a".repeat( 500 ) ) ).contains( "500" );
	}

	@Test
	@DisplayName( "private, loopback, link-local, metadata, ULA and carrier-grade addresses are refused, typed or resolved" )
	void notPublic() {
		for ( String ip : new String[] { "10.0.0.5", "10.255.255.255", "172.16.0.1", "172.31.255.1", "192.168.0.1", "169.254.169.254", "169.254.1.1", "0.0.0.0",
		    "100.64.0.1", "100.127.255.1", "198.18.0.1", "224.0.0.1", "255.255.255.255", "192.0.0.8" } ) {
			assertThat( why( "https://" + ip + "/mcp" ) ).contains( "Only public" );
		}
		for ( String v6 : new String[] { "[fc00::1]", "[fd12:3456::1]", "[fe80::1]", "[::]", "[ff02::1]", "[::ffff:10.0.0.1]", "[::ffff:169.254.169.254]",
		    "[::ffff:127.0.0.1]" } ) {
			assertThat( why( "https://" + v6 + "/mcp" ) ).contains( "Only public" );
		}
		for ( String name : new String[] { "intranet", "lan", "meta", "rebind", "ula", "cgnat", "linklocal6" } ) {
			assertThat( why( "https://" + name + ".example.com/mcp" ) ).contains( "Only public" );
		}
	}

	@Test
	@DisplayName( "an address that does not resolve is refused" )
	void unknownHost() {
		assertThat( why( "https://nope.example.com/mcp" ) ).contains( "cannot be resolved" );
	}

	@Test
	@DisplayName( "tricks for writing 127.0.0.1 are resolved and refused, not taken for loopback" )
	void loopbackTricks() {
		assertThat( McpUrls.isLoopbackLiteral( "127.0.0.1" ) ).isTrue();
		assertThat( McpUrls.isLoopbackLiteral( "LOCALHOST" ) ).isTrue();
		assertThat( McpUrls.isLoopbackLiteral( "[::1]" ) ).isTrue();
		assertThat( McpUrls.isLoopbackLiteral( "127.0.0.1.evil.com" ) ).isFalse();
		assertThat( McpUrls.isLoopbackLiteral( "2130706433" ) ).isFalse();
		assertThat( McpUrls.isLoopbackLiteral( "0x7f.0.0.1" ) ).isFalse();
		assertThat( McpUrls.isLoopbackLiteral( "127.0.0" ) ).isFalse();
		assertThat( McpUrls.isLoopbackLiteral( "127.0.0.256" ) ).isFalse();
		// A name that resolves to loopback, over https, is refused: only the literal localhost is a loopback host
		assertThat( why( "https://rebind.example.com/mcp" ) ).contains( "Only public" );
		// Decimal form: the resolver turns it into 127.0.0.1 and the address is refused
		assertThat( why( "https://2130706433/mcp" ) ).isNotEmpty();
	}

	@Test
	@DisplayName( "reading the saved list only looks at the form of the address and never resolves" )
	void noResolveForTheSavedList() {
		McpUrls.Resolver boom = host -> {
			throw new AssertionError( "must not resolve " + host );
		};
		assertThat( McpUrls.check( "https://docs.example.com/mcp", McpUrls.NO_RESOLVE ) ).isNotEmpty();
		assertThrows( AssertionError.class, () -> McpUrls.check( "https://docs.example.com/mcp", boom ) );
		assertThrows( IllegalArgumentException.class, () -> McpUrls.check( "http://docs.example.com/mcp", McpUrls.NO_RESOLVE ) );
		assertThrows( IllegalArgumentException.class, () -> McpUrls.check( "https://u:p@docs.example.com/mcp", McpUrls.NO_RESOLVE ) );
	}

}
