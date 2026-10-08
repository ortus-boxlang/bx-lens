/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.IBoxContext;

/**
 * Base class for the Lens BIFs. They are all safe to call when Lens is disabled or the request is not tracked: they do nothing.
 */
public abstract class BaseLensBIF extends BIF {

	protected LensService service() {
		return LensService.getInstance();
	}

	/**
	 * The tracked request for this context, or null.
	 */
	protected LensRequest request( IBoxContext context ) {
		return service().current( context );
	}

	protected Sanitizer sanitizer() {
		return new Sanitizer( service().getConfig() );
	}

}
