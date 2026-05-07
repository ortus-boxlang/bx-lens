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
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensAddMeasure( label, executionTime [, offset] ) — Add a pre-computed timing to the debug panel.
 */
@BoxBIF
public class LensAddMeasure extends BIF {

	private static final Key	KEY_ENABLED		= Key.of( "enabled" );
	private static final Key	KEY_TIMINGS		= Key.of( "timings" );
	private static final Key	KEY_STARTED_AT	= Key.of( "startedAt" );
	private static final Key	KEY_MAX_TIMINGS	= Key.of( "maxTimings" );

	public LensAddMeasure() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "label" ) ),
		    new Argument( true, Argument.NUMERIC, Key.of( "executionTime" ) ),
		    new Argument( false, Argument.NUMERIC, Key.of( "offset" ), 0 )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IBoxContext requestCtx = context.getRequestContext();
		if ( requestCtx == null ) {
			requestCtx = context;
		}
		IStruct lensData = requestCtx.getAttachment( KeyDictionary.lensData );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( KEY_ENABLED ) ) ) {
			return null;
		}

		IStruct	settings	= moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
		Integer	maxTimings	= settings.getAsInteger( KEY_MAX_TIMINGS );
		Array	timings		= lensData.getAsArray( KEY_TIMINGS );

		if ( timings.size() >= maxTimings ) {
			return null;
		}

		String	label			= arguments.getAsString( Key.of( "label" ) );
		long	executionTime	= arguments.getAsLong( Key.of( "executionTime" ) );
		long	offset			= arguments.getAsLong( Key.of( "offset" ) );
		long	startedAt		= lensData.getAsLong( KEY_STARTED_AT );

		timings.add( Struct.of(
		    "label", label,
		    "executionTime", executionTime,
		    "offset", offset > 0 ? offset : System.currentTimeMillis() - startedAt,
		    "hash", UUID.randomUUID().toString()
		) );

		return null;
	}

}
