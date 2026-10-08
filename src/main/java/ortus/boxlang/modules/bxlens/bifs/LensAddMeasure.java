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
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Adds a timer after the fact, from start and end times in milliseconds since the request started.
 * <p>
 * Example: <code>lensAddMeasure( "warmup", 12.5, 48 )</code>
 *
 * @argument.label Name of the measure
 *
 * @argument.startMs Start, in milliseconds from the request start
 *
 * @argument.endMs End, in milliseconds from the request start
 */
@BoxBIF
public class LensAddMeasure extends BaseLensBIF {

	private static final Key	LABEL	= Key.of( "label" );
	private static final Key	START	= Key.of( "startMs" );
	private static final Key	END		= Key.of( "endMs" );

	public LensAddMeasure() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL ),
		    new Argument( true, Argument.NUMERIC, START ),
		    new Argument( true, Argument.NUMERIC, END )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req != null ) {
			long	s	= Math.round( ( ( Number ) arguments.get( START ) ).doubleValue() * 1_000_000 );
			long	e	= Math.round( ( ( Number ) arguments.get( END ) ).doubleValue() * 1_000_000 );
			LensStop.record( req, arguments.getAsString( LABEL ), s, e );
		}
		return null;
	}

}
