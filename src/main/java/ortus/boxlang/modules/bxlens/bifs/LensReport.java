/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Totals for this server since it started (and since first install with the disk store): requests, errors, status classes, latency
 * percentiles, the slowest, busiest and most failing URLs and a minute series. Returns an empty struct when Lens is off.
 */
@BoxBIF
public class LensReport extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return BoxData.of( service().getReports().snapshot( service().diskStoreOn() ) );
	}

}
