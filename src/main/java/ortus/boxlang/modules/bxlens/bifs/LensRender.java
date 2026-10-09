/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

/**
 * Places the Lens bar exactly where you call it, instead of before the closing body tag. Use it with <code>inject: false</code>.
 * The bar is filled in when the request ends, so it can show the whole request.
 * <p>
 * Example: <code>#lensRender()#</code> in your layout footer.
 */
@BoxBIF
public class LensRender extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		return req == null ? "" : LensService.MARKER;
	}

}
