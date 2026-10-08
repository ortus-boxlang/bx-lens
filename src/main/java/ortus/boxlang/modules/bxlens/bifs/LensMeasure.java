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
 * Times a closure and returns its result. Works even when the request is not tracked, in which case it just runs the closure.
 * <p>
 * Example: <code>orders = lensMeasure( "load orders", () => orderService.list() )</code>
 *
 * @argument.label Name of the measure
 *
 * @argument.callable The function to time
 */
@BoxBIF
public class LensMeasure extends BaseLensBIF {

	private static final Key	LABEL		= Key.of( "label" );
	private static final Key	CALLABLE	= Key.of( "callable" );

	public LensMeasure() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL ),
		    new Argument( true, Argument.FUNCTION, CALLABLE )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest	req		= request( context );
		long		start	= req == null ? 0 : req.now();
		try {
			return context.invokeFunction( arguments.get( CALLABLE ), new Object[] {} );
		} finally {
			if ( req != null ) {
				LensStop.record( req, arguments.getAsString( LABEL ), start, req.now() );
			}
		}
	}

}
