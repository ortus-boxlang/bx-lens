/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.ConsoleRouter;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

/**
 * Serves the standalone console. Called by the module's public <code>index.bxm</code>; applications do not call it.
 */
@BoxBIF
public class LensConsole extends BIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		new ConsoleRouter( LensService.getInstance() ).handle( context );
		return null;
	}

}
