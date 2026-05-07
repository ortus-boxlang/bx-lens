/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.FunctionBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.Function;

/**
 * LensMeasure( labelOrClosure [, closure] ) — Time a closure and record it as a custom timing.
 */
@BoxBIF
public class LensMeasure extends BaseLensBIF {

	private static final Key KEY_MAX_TIMINGS = Key.of( "maxTimings" );

	public LensMeasure() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, Key.of( "labelOrClosure" ) ),
		    new Argument( false, Argument.ANY, Key.of( "closure" ), null )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Object	labelOrClosureArg	= arguments.get( Key.of( "labelOrClosure" ) );
		Object	closureArg			= arguments.get( Key.of( "closure" ) );

		String		label;
		Function	fn;

		if ( labelOrClosureArg instanceof Function ) {
			fn		= ( Function ) labelOrClosureArg;
			label	= "anonymous";
		} else {
			label	= labelOrClosureArg != null ? labelOrClosureArg.toString() : "anonymous";
			fn		= ( Function ) closureArg;
		}

		long	start	= System.currentTimeMillis();
		Object	result	= fn.invoke( new FunctionBoxContext( context, fn ) );
		long	elapsed	= System.currentTimeMillis() - start;

		LensRequestData data = getLensData( context );
		if ( isEnabled( data ) ) {
			int maxTimings = getModuleSettings().getAsInteger( KEY_MAX_TIMINGS );
			if ( data.timings.size() < maxTimings ) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put( "label", label );
				entry.put( "executionTime", elapsed );
				entry.put( "offset", start - data.startedAt );
				entry.put( "hash", UUID.randomUUID().toString() );
				data.timings.add( entry );
			}
		}

		return result;
	}

}
