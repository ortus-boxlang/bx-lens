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
 * LensStop( labelOrHash ) — Stop a custom timer started with LensStart().
 */
@BoxBIF
public class LensStop extends BIF {

	private static final Key	KEY_ENABLED			= Key.of( "enabled" );
	private static final Key	KEY_TIMINGS			= Key.of( "timings" );
	private static final Key	KEY_PENDING_TIMINGS	= Key.of( "_pendingTimings" );
	private static final Key	KEY_MAX_TIMINGS		= Key.of( "maxTimings" );
	private static final Key	KEY_LABEL			= Key.of( "label" );
	private static final Key	KEY_START_TICK		= Key.of( "_startTick" );
	private static final Key	KEY_OFFSET			= Key.of( "offset" );

	public LensStop() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "labelOrHash" ) )
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
		if ( !lensData.containsKey( KEY_PENDING_TIMINGS ) ) {
			return null;
		}

		IStruct	settings	= moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
		Integer	maxTimings	= settings.getAsInteger( KEY_MAX_TIMINGS );
		Array	timings		= lensData.getAsArray( KEY_TIMINGS );

		if ( timings.size() >= maxTimings ) {
			return null;
		}

		IStruct	pending			= lensData.getAsStruct( KEY_PENDING_TIMINGS );
		String	labelOrHash		= arguments.getAsString( Key.of( "labelOrHash" ) );
		String	found			= null;

		// Try exact hash first
		if ( pending.containsKey( Key.of( labelOrHash ) ) ) {
			found = labelOrHash;
		} else {
			// Fall back to most-recent by label
			for ( Key k : pending.keySet() ) {
				IStruct entry = ( IStruct ) pending.get( k );
				if ( labelOrHash.equals( entry.getAsString( KEY_LABEL ) ) ) {
					found = k.getName();
				}
			}
		}

		if ( found == null || found.isEmpty() ) {
			return null;
		}

		Key		foundKey	= Key.of( found );
		IStruct	t			= ( IStruct ) pending.get( foundKey );
		long	now			= System.currentTimeMillis();
		long	startTick	= t.getAsLong( KEY_START_TICK );
		long	offset		= t.getAsLong( KEY_OFFSET );

		timings.add( Struct.of(
		    "label", t.getAsString( KEY_LABEL ),
		    "executionTime", now - startTick,
		    "offset", offset,
		    "hash", found
		) );

		pending.remove( foundKey );
		return null;
	}

}
