/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.BaseIntegrationTest;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Scans the real LensyTools class with the bx-ai tool registry and reads the schemas the model would be shown.
 */
public class AgentToolsIntegrationTest extends BaseIntegrationTest {

	@Test
	@DisplayName( "the @AITool class gives one tool per catalog entry, with the arguments, types, required flags and hints from the comments" )
	@SuppressWarnings( "unchecked" )
	void schemas() {
		String path = moduleRecord.invocationPath;
		loadBxAi();
		runtime.executeSource(
		    """
		    registry = aiToolRegistry();
		    tools = registry.scanClass( new %s.models.ops.LensyTools( { call : ( n, a ) => n } ), "lens-test-scan" );
		    registry.unregisterByModule( "lens-test-scan" );
		    schemas = tools.map( t => t.getSchema() );
		    left = registry.getKeys().filter( k => k contains "lens-test-scan" ).len();
		    """.formatted( path ),
		    context );
		List<Object> schemas = ( List<Object> ) variables.get( Key.of( "schemas" ) );
		assertThat( schemas ).hasSize( Tools.all().size() );
		assertThat( ( ( Number ) variables.get( Key.of( "left" ) ) ).intValue() ).isEqualTo( 0 );
		List<String> names = new ArrayList<>();
		for ( Object o : schemas ) {
			Map<Object, Object>	schema	= ( Map<Object, Object> ) o;
			Map<Object, Object>	fn		= ( Map<Object, Object> ) get( schema, "function" );
			String				name	= String.valueOf( get( fn, "name" ) );
			names.add( name );
			Tools.Tool tool = Tools.get( name );
			assertThat( tool ).isNotNull();
			assertThat( String.valueOf( get( schema, "type" ) ) ).isEqualTo( "function" );
			assertThat( String.valueOf( get( fn, "description" ) ).length() ).isGreaterThan( 40 );
			Map<Object, Object>	params	= ( Map<Object, Object> ) get( fn, "parameters" );
			Map<Object, Object>	props	= ( Map<Object, Object> ) get( params, "properties" );
			List<Object>		req		= ( List<Object> ) get( params, "required" );
			assertThat( props.size() ).isEqualTo( tool.params().size() );
			for ( Tools.Param p : tool.params() ) {
				Map<Object, Object> prop = ( Map<Object, Object> ) get( props, p.name() );
				assertThat( prop ).isNotNull();
				assertThat( String.valueOf( get( prop, "type" ) ) )
				    .isEqualTo( p.type().equals( "int" ) ? "number" : p.type().equals( "bool" ) ? "boolean" : "string" );
				// The hint of the @name comment line, not just the name repeated
				assertThat( String.valueOf( get( prop, "description" ) ) ).isNotEqualTo( p.name() );
				assertThat( req.stream().map( String::valueOf ).anyMatch( p.name()::equals ) ).isEqualTo( p.required() );
			}
		}
		assertThat( names ).containsExactlyElementsIn( Tools.all().stream().map( Tools.Tool::name ).toList() );
	}

	@Test
	@DisplayName( "MCP tools become agent tools with their own schema, calls go to the Toolbox, and a rebuilt agent keeps the memory" )
	@SuppressWarnings( "unchecked" )
	void mcpTools() {
		String path = moduleRecord.invocationPath;
		loadBxAi();
		runtime.executeSource(
		    """
		    seen = [];
		    toolbox = { callMcp : ( w, a ) => { seen.append( { wire : w, args : a } ); return { text : () => "from the toolbox" }; }, call : ( n, a ) => n };
		    settings = { provider : "ollama", model : "", baseUrl : "", apiKey : "", temperature : 0.2, timeoutSeconds : 30, maxToolCalls : 8, memoryMessages : 20, server : "test" };
		    specs = [ { name : "acme__lookup", description : "[Acme] Look it up",
		        schema : { properties : { "q" : { "type" : "string", "description" : "What" } }, required : [ "q" ] } } ];
		    first = new %s.models.ops.Lensy( toolbox, settings, [ "overview" ], specs );
		    names = first.toolNames();
		    schema = first.getTool( "acme__lookup" ).getSchema();
		    answer = first.getTool( "acme__lookup" ).invoke( { q : "hi" }, nullValue() );
		    second = new %s.models.ops.Lensy( toolbox, settings, [ "overview" ], [], first.getMemory() );
		    m1 = first.getMemory(); m2 = second.getMemory();
		    secondNames = second.toolNames();
		    """.formatted(
		        path, path ),
		    context );
		assertThat( ( List<Object> ) variables.get( Key.of( "names" ) ) ).containsExactly( "overview", "acme__lookup" );
		Map<Object, Object> fn = ( Map<Object, Object> ) get( ( Map<Object, Object> ) variables.get( Key.of( "schema" ) ), "function" );
		assertThat( String.valueOf( get( fn, "name" ) ) ).isEqualTo( "acme__lookup" );
		Map<Object, Object> params = ( Map<Object, Object> ) get( fn, "parameters" );
		assertThat( ( ( Map<Object, Object> ) get( params, "properties" ) ).keySet().stream().map( String::valueOf ).toList() ).containsExactly( "q" );
		assertThat( String.valueOf( variables.get( Key.of( "answer" ) ) ) ).isEqualTo( "from the toolbox" );
		List<Object> seen = ( List<Object> ) variables.get( Key.of( "seen" ) );
		assertThat( seen ).hasSize( 1 );
		assertThat( String.valueOf( get( ( Map<Object, Object> ) seen.get( 0 ), "wire" ) ) ).isEqualTo( "acme__lookup" );
		assertThat( variables.get( Key.of( "m2" ) ) ).isSameInstanceAs( variables.get( Key.of( "m1" ) ) );
		assertThat( ( List<Object> ) variables.get( Key.of( "secondNames" ) ) ).containsExactly( "overview" );
	}

	/** The nested bx-ai ships inside the module. The test runtime loads the module by hand, so it loads bx-ai the same way. */
	private void loadBxAi() {
		Key name = Key.of( "bxai" );
		if ( !moduleService.hasModule( name ) ) {
			var record = new ortus.boxlang.runtime.modules.ModuleRecord(
			    java.nio.file.Paths.get( "./build/modules/bx-lens/modules/bxai" ).toAbsolutePath().toString() );
			moduleService.getRegistry().put( name, record );
			record.loadDescriptor( runtime.getRuntimeContext() ).register( runtime.getRuntimeContext() ).activate( runtime.getRuntimeContext() );
		}
	}

	private static Object get( Map<Object, Object> m, String key ) {
		for ( Map.Entry<Object, Object> e : m.entrySet() ) {
			if ( String.valueOf( e.getKey() ).equalsIgnoreCase( key ) ) {
				return e.getValue();
			}
		}
		return null;
	}

}
