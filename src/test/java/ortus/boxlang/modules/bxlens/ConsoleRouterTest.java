/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConsoleRouterTest {

	@Test
	@DisplayName( "the admin only routes cover logs, threads, environment, system, files and secrets" )
	void adminOnly() {
		for ( String r : new String[] { "logfiles", "logfiles/read", "logfiles/download", "threads", "threads/dump", "environment", "system", "bundle",
		    "heapdump",
		    "heapdump/download", "cachevalue/x" } ) {
			assertWithMessage( r ).that( ConsoleRouter.ADMIN_ONLY.stream().anyMatch( r::startsWith ) ).isTrue();
		}
		for ( String r : new String[] { "overview", "requests", "requests/abc", "inflight", "queries", "errors", "reports", "executors", "tasks", "datasources",
		    "caches",
		    "caches/x/keys", "modules", "settings", "state" } ) {
			assertWithMessage( r ).that( ConsoleRouter.ADMIN_ONLY.stream().anyMatch( r::startsWith ) ).isFalse();
		}
	}

	@Test
	@DisplayName( "every MCP route is for the admin role, reading included" )
	void mcpRoutesAreAdminOnly() {
		for ( String r : new String[] { "ai/mcp", "ai/mcp/boxlang/enable", "ai/mcp/boxlang/test", "ai/mcp/acme" } ) {
			assertWithMessage( r ).that( ConsoleRouter.ADMIN_ONLY.stream().anyMatch( r::startsWith ) ).isTrue();
		}
		assertThat( ConsoleRouter.VIEWER_POST ).containsExactly( "agent/chat", "agent/reset", "agent/approve" );
	}

	@Test
	@DisplayName( "the pages and settings a viewer does not get" )
	void viewer() {
		assertThat( ConsoleRouter.ADMIN_PAGES ).containsExactly( "logfiles", "environment", "system", "threads", "ai" );
		assertThat( ConsoleRouter.ADMIN_SETTINGS ).containsExactly( "console.access", "access.proxypeers" );
	}

}
