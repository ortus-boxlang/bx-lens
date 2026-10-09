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
 * The error groups Lens keeps, newest first: type, message, count, first and last seen, the most affected URLs. The argument limit caps how
 * many groups are returned (default 20).
 */
@BoxBIF
public class LensErrors extends BaseLensBIF {

	private static final Key LIMIT = Key.of( "limit" );

	public LensErrors() {
		super();
		declaredArguments = new Argument[] { new Argument( false, Argument.INTEGER, LIMIT, 20 ) };
	}

	@Override
	@SuppressWarnings( "unchecked" )
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		int						limit	= Math.max( 1, IntegerCaster.cast( arguments.get( LIMIT ) ) );
		java.util.List<Object>	groups	= ( java.util.List<Object> ) service().getErrors().list().get( "groups" );
		return BoxData.of( groups.size() > limit ? groups.subList( 0, limit ) : groups );
	}

}
