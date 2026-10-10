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
 * Scans the real LensTools class with the bx-ai tool registry and reads the schemas the model would be shown.
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
		    tools = registry.scanClass( new %s.models.ops.LensTools( { call : ( n, a ) => n } ), "lens-test-scan" );
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
