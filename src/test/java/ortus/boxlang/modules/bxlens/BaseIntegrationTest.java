/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.services.ModuleService;

/**
 * Boots a runtime and loads the packaged module from build/modules/bx-lens, so run <code>./gradlew shadowJar test</code>.
 */
public abstract class BaseIntegrationTest {

	protected static BoxRuntime				runtime;
	protected static ModuleService			moduleService;
	protected static ModuleRecord			moduleRecord;
	protected static Key					result		= new Key( "result" );
	protected static Key					moduleName	= Keys.moduleName;
	protected ScriptingRequestBoxContext	context;
	protected IScope						variables;

	@BeforeAll
	public static void setup() {
		runtime			= BoxRuntime.getInstance( true, Path.of( "src/test/resources/boxlang.json" ).toString() );
		moduleService	= runtime.getModuleService();
		loadModule( runtime.getRuntimeContext() );
	}

	@BeforeEach
	public void setupEach() {
		context		= new ScriptingRequestBoxContext();
		variables	= context.getScopeNearby( VariablesScope.name );
	}

	protected static void loadModule( IBoxContext context ) {
		if ( !runtime.getModuleService().hasModule( moduleName ) ) {
			String physicalPath = Paths.get( "./build/modules/bx-lens" ).toAbsolutePath().toString();
			moduleRecord = new ModuleRecord( physicalPath );
			moduleService.getRegistry().put( moduleName, moduleRecord );
			moduleRecord.loadDescriptor( context ).register( context ).activate( context );
		} else {
			moduleRecord = moduleService.getModuleRecord( moduleName );
		}
	}

}
