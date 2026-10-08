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
 * Query statistics across requests: one entry per distinct statement with runs, failures, slow runs, average, maximum and total milliseconds.
 * The argument sort is slowest (default), total, count or failures. The argument limit caps the number returned (default 20).
 */
@BoxBIF
public class LensQueries extends BaseLensBIF {

	private static final Key	LIMIT	= Key.of( "limit" );
	private static final Key	SORT	= Key.of( "sort" );

	public LensQueries() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, Argument.INTEGER, LIMIT, 20 ),
		    new Argument( false, Argument.STRING, SORT, "slowest" )
		};
	}

	@Override
	@SuppressWarnings( "unchecked" )
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		int												limit	= Math.max( 1, IntegerCaster.cast( arguments.get( LIMIT ) ) );
		String											key		= switch ( arguments.getAsString( SORT ).toLowerCase() ) {
																	case "total" -> "totalMs";
																	case "count" -> "count";
																	case "failures" -> "failures";
																	default -> "maxMs";
																};
		java.util.List<java.util.Map<String, Object>>	rows	= new java.util.ArrayList<>(
		    ( java.util.List<java.util.Map<String, Object>> ) service().getQueryStats().snapshot().get( "statements" ) );
		rows.sort( ( a, b ) -> Double.compare( ( ( Number ) b.get( key ) ).doubleValue(), ( ( Number ) a.get( key ) ).doubleValue() ) );
		return BoxData.of( rows.size() > limit ? rows.subList( 0, limit ) : rows );
	}

}
