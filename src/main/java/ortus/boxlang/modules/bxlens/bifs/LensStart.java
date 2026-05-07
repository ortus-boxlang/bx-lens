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
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensStart( label ) — Start a named custom timer.
 */
@BoxBIF
public class LensStart extends BIF {

	private static final Key	KEY_ENABLED			= Key.of( "enabled" );
	private static final Key	KEY_STARTED_AT		= Key.of( "startedAt" );
	private static final Key	KEY_PENDING_TIMINGS	= Key.of( "_pendingTimings" );

	public LensStart() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "label" ) )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		IBoxContext requestCtx = context.getRequestContext();
		if ( requestCtx == null ) {
			requestCtx = context;
		}
		IStruct lensData = requestCtx.getAttachment( KeyDictionary.lensData );
		String label = arguments.getAsString( Key.of( "label" ) );

		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( KEY_ENABLED ) ) ) {
			return label;
		}

		// Ensure _pendingTimings exists
		if ( !lensData.containsKey( KEY_PENDING_TIMINGS ) || lensData.get( KEY_PENDING_TIMINGS ) == null ) {
			lensData.put( KEY_PENDING_TIMINGS, Struct.of() );
		}

		IStruct	pendingTimings	= lensData.getAsStruct( KEY_PENDING_TIMINGS );
		long	now				= System.currentTimeMillis();
		long	startedAt		= lensData.getAsLong( KEY_STARTED_AT );
		String	hash			= label + "_" + UUID.randomUUID().toString();

		pendingTimings.put( hash, Struct.of(
		    "label", label,
		    "_startTick", now,
		    "offset", now - startedAt
		) );

		return hash;
	}

}
