package ortus.boxlang.modules.bxlens;

import org.junit.jupiter.api.BeforeAll;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.ModuleService;

public abstract class BaseIntegrationTest {

	protected static BoxRuntime		runtime;
	protected static ModuleService	moduleService;
	protected static Key			MODULE_NAME	= Key.of( "bxLens" );

	@BeforeAll
	public static void setupRuntime() {
		runtime			= BoxRuntime.getInstance( true );
		moduleService	= runtime.getModuleService();
		// Module is built to build/modules/bxLens by createModuleStructure task
		moduleService.addModulePath( "build/modules" );
		moduleService.registerAll();
		moduleService.activateAll();
	}

	protected IBoxContext getContext() {
		return new ScriptingRequestBoxContext( runtime.getRuntimeContext() );
	}
}
