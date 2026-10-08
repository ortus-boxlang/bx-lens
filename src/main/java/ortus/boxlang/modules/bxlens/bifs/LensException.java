/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.interceptors.collectors.ExceptionCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records an exception you caught, so it shows in the Exceptions panel and Issues even though the request carried on.
 * <p>
 * Example: <code>try { risky() } catch ( any e ) { lensException( e ); }</code>
 *
 * @argument.exception The caught exception
 */
@BoxBIF
public class LensException extends BaseLensBIF {

	private static final Key EXCEPTION = Key.of( "exception" );

	public LensException() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, EXCEPTION )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "exceptions", true ) ) {
			return null;
		}
		Object		raw	= arguments.get( EXCEPTION );
		Throwable	t;
		if ( raw instanceof Throwable th ) {
			t = th;
		} else if ( raw instanceof IStruct s ) {
			t = new RuntimeException( String.valueOf( s.getOrDefault( Key.of( "message" ), "Exception" ) ) );
		} else {
			t = new RuntimeException( String.valueOf( raw ) );
		}
		ExceptionCollector.record( req, t, "manual", service().getConfig() );
		return null;
	}

}
