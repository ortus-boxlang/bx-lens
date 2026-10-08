/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Starts a named timer. Stop it with lensStop(), using the label or the returned id.
 * <p>
 * Example: <code>id = lensStart( "price calculation" )</code>
 *
 * @argument.label Name of the timer
 *
 * @return an id for this timer, or the label when the request is not tracked
 */
@BoxBIF
public class LensStart extends BaseLensBIF {

	private static final Key LABEL = Key.of( "label" );

	public LensStart() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String		label	= arguments.getAsString( LABEL );
		LensRequest	req		= request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "timers", true ) ) {
			return label;
		}
		String				id		= label + "#" + UUID.randomUUID().toString().substring( 0, 6 );
		Map<String, Object>	timer	= new LinkedHashMap<>();
		timer.put( "label", label );
		timer.put( "startNs", req.now() );
		req.pendingTimers.put( id, timer );
		return id;
	}

}
