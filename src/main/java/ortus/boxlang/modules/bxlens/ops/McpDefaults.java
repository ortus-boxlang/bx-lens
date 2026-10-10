/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.ArrayList;
import java.util.List;

/**
 * The documentation servers of the Ortus projects (GitBook MCP endpoints). They are listed, all disabled, until an admin turns one on.
 */
public final class McpDefaults {

	/** id, name and address of each builtin server. */
	private static final String[][] BUILTIN = { { "boxlang", "BoxLang docs", "https://boxlang.ortusbooks.com/~gitbook/mcp" },
	    { "coldbox", "ColdBox docs", "https://coldbox.ortusbooks.com/~gitbook/mcp" },
	    { "commandbox", "CommandBox docs", "https://commandbox.ortusbooks.com/~gitbook/mcp" },
	    { "testbox", "TestBox docs", "https://testbox.ortusbooks.com/~gitbook/mcp" },
	    { "wirebox", "WireBox docs", "https://wirebox.ortusbooks.com/~gitbook/mcp" },
	    { "logbox", "LogBox docs", "https://logbox.ortusbooks.com/~gitbook/mcp" },
	    { "cachebox", "CacheBox docs", "https://cachebox.ortusbooks.com/~gitbook/mcp" },
	    { "contentbox", "ContentBox docs", "https://contentbox.ortusbooks.com/~gitbook/mcp" },
	    { "qb", "qb docs", "https://qb.ortusbooks.com/~gitbook/mcp" },
	    { "quick", "Quick docs", "https://quick.ortusbooks.com/~gitbook/mcp" },
	    { "cbauth", "cbauth docs", "https://cbauth.ortusbooks.com/~gitbook/mcp" } };

	private McpDefaults() {
	}

	/**
	 * The builtin list, all disabled, every tool allowed.
	 *
	 * @param base empty for the real addresses; else the address of a stand-in (the test harness) that serves each one at base/id
	 */
	public static List<McpServer> servers( String base ) {
		List<McpServer>	out		= new ArrayList<>();
		String			root	= base == null ? "" : base.trim();
		while ( root.endsWith( "/" ) ) {
			root = root.substring( 0, root.length() - 1 );
		}
		for ( String[] b : BUILTIN ) {
			out.add( new McpServer( b[ 0 ], b[ 1 ], root.isEmpty() ? b[ 2 ] : root + "/" + b[ 0 ], true, false, false, List.of( McpServer.ALL ) ) );
		}
		return out;
	}

	public static boolean isBuiltinId( String id ) {
		for ( String[] b : BUILTIN ) {
			if ( b[ 0 ].equals( id ) ) {
				return true;
			}
		}
		return false;
	}

}
