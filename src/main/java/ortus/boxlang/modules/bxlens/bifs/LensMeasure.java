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

import java.util.UUID;

import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.FunctionBoxContext;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.Function;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensMeasure( labelOrClosure [, closure] ) — Time a closure and record it as a custom timing.
 */
@BoxBIF
public class LensMeasure extends BIF {

	private static final Key	KEY_ENABLED		= Key.of( "enabled" );
	private static final Key	KEY_TIMINGS		= Key.of( "timings" );
	private static final Key	KEY_STARTED_AT	= Key.of( "startedAt" );
	private static final Key	KEY_MAX_TIMINGS	= Key.of( "maxTimings" );

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
			// LensMeasure( () -> { ... } )
			fn		= ( Function ) labelOrClosureArg;
			label	= "anonymous";
		} else {
			// LensMeasure( "label", () -> { ... } )
			label	= labelOrClosureArg != null ? labelOrClosureArg.toString() : "anonymous";
			fn		= ( Function ) closureArg;
		}

		long	start	= System.currentTimeMillis();
		Object	result	= fn.invoke( new FunctionBoxContext( context, fn ) );
		long	elapsed	= System.currentTimeMillis() - start;

		IBoxContext requestCtx = context.getRequestContext();
		if ( requestCtx == null ) {
			requestCtx = context;
		}
		IStruct lensData = requestCtx.getAttachment( KeyDictionary.lensData );
		if ( lensData != null && Boolean.TRUE.equals( lensData.getAsBoolean( KEY_ENABLED ) ) ) {
			IStruct	settings	= moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
			Integer	maxTimings	= settings.getAsInteger( KEY_MAX_TIMINGS );
			Array	timings		= lensData.getAsArray( KEY_TIMINGS );

			if ( timings.size() < maxTimings ) {
				long startedAt = lensData.getAsLong( KEY_STARTED_AT );
				timings.add( Struct.of(
				    "label", label,
				    "executionTime", elapsed,
				    "offset", start - startedAt,
				    "hash", UUID.randomUUID().toString()
				) );
			}
		}

		return result;
	}

}
