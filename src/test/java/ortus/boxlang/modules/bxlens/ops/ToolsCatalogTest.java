/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps three lists of the tools in step: the Java catalog, the BoxLang class the model reads, and the table in the docs.
 */
public class ToolsCatalogTest {

	private static final Path	TOOLS_BX	= Path.of( "src/main/bx/models/ops/LensTools.bx" );
	private static final Path	DOCS		= Path.of( "docs/reference/ai-tools.md" );

	/** The @AITool methods of LensTools.bx with their argument names and whether each is required. */
	private static Map<String, Map<String, Boolean>> boxlangTools() throws Exception {
		Map<String, Map<String, Boolean>>	out		= new LinkedHashMap<>();
		List<String>						lines	= Files.readAllLines( TOOLS_BX );
		for ( int i = 0; i < lines.size(); i++ ) {
			if ( lines.get( i ).trim().equals( "@AITool" ) ) {
				String					sig		= lines.get( i + 1 ).trim();
				String					name	= sig.substring( sig.indexOf( "function " ) + 9, sig.indexOf( '(' ) ).trim();
				String					args	= sig.substring( sig.indexOf( '(' ) + 1, sig.lastIndexOf( ')' ) ).trim();
				Map<String, Boolean>	params	= new LinkedHashMap<>();
				if ( !args.isEmpty() ) {
					for ( String a : args.split( "," ) ) {
						String[]	parts		= a.trim().split( " " );
						boolean		required	= parts[ 0 ].equals( "required" );
						params.put( parts[ required ? 2 : 1 ], required );
					}
				}
				out.put( name, params );
			}
		}
		return out;
	}

	@Test
	@DisplayName( "LensTools.bx has exactly the tools of the catalog, with the same arguments and required flags" )
	void boxlangAgreesWithCatalog() throws Exception {
		Map<String, Map<String, Boolean>> bx = boxlangTools();
		assertThat( new ArrayList<>( bx.keySet() ) ).containsExactlyElementsIn( Tools.all().stream().map( Tools.Tool::name ).toList() ).inOrder();
		for ( Tools.Tool t : Tools.all() ) {
			Map<String, Boolean> params = bx.get( t.name() );
			assertThat( new ArrayList<>( params.keySet() ) ).containsExactlyElementsIn( t.params().stream().map( Tools.Param::name ).toList() ).inOrder();
			for ( Tools.Param p : t.params() ) {
				assertThat( params.get( p.name() ) ).isEqualTo( p.required() );
			}
		}
	}

	@Test
	@DisplayName( "every tool is in the table of docs/reference/ai-tools.md with the right role and kind" )
	void docsAgreeWithCatalog() throws Exception {
		String doc = Files.readString( DOCS );
		for ( Tools.Tool t : Tools.all() ) {
			String row = null;
			for ( String line : doc.split( "\n" ) ) {
				if ( line.startsWith( "| `" + t.name() + "` |" ) ) {
					row = line;
				}
			}
			assertThat( row ).isNotNull();
			String[] cells = row.split( "\\|" );
			assertThat( cells[ 3 ].trim() ).isEqualTo( t.admin() ? "admin" : "viewer and admin" );
			assertThat( cells[ 4 ].trim() ).isEqualTo( t.act() ? "ACT" : "LOOK" );
		}
	}

	@Test
	@DisplayName( "only the ACT tools change anything and all of them are admin only" )
	void actTools() {
		List<String> acts = Tools.all().stream().filter( Tools.Tool::act ).map( Tools.Tool::name ).toList();
		assertThat( acts ).containsExactly( "taskAction", "cacheAction", "runGc", "setIntegration", "changeSetting" );
		for ( Tools.Tool t : Tools.all() ) {
			if ( t.act() ) {
				assertThat( t.admin() ).isTrue();
			}
		}
	}

	@Test
	@DisplayName( "there is no restart, shutdown, heap dump or file tool" )
	void noDangerousTools() {
		for ( Tools.Tool t : Tools.all() ) {
			String n = t.name().toLowerCase();
			assertThat( n ).doesNotContain( "restart" );
			assertThat( n ).doesNotContain( "shutdown" );
			assertThat( n ).doesNotContain( "heap" );
			assertThat( n ).doesNotContain( "file" );
		}
	}

	@Test
	@DisplayName( "the tool count stays manageable" )
	void count() {
		assertThat( Tools.all().size() ).isAtMost( 40 );
	}

}
