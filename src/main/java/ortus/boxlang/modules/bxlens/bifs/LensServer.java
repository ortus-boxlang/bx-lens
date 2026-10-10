/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

/**
 * The identity of this server: <code>host</code>, <code>ip</code>, <code>addresses</code>, <code>id</code> and <code>runtime</code>. The same
 * values every request, error, report and audit line carries, so a record can be traced back to the machine that produced it.
 */
@BoxBIF
public class LensServer extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return BoxData.of( service().getIdentity().get().toMap() );
	}

}
