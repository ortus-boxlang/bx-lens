/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

/**
 * Turns Lens back on for the rest of this request. It cannot override the global settings: access rules and the master switch still apply.
 */
@BoxBIF
public class LensEnable extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = context.getRequestContext() == null ? null
		    : context.getRequestContext().getAttachment( ortus.boxlang.modules.bxlens.util.Keys.requestAttach );
		if ( req != null ) {
			req.enabled = true;
		}
		return null;
	}

}
