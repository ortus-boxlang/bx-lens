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
 * The id Lens gave this request, the same one that is sent in the response header and shown in the bar and the console. An empty string
 * when the request is not tracked.
 */
@BoxBIF
public class LensRequestId extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		return req == null ? "" : req.id;
	}

}
