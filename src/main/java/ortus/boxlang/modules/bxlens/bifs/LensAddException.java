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
 * LensAddException( exception ) — Manually log a caught exception to the debug panel.
 */
@BoxBIF
public class LensAddException extends BIF {

	private static final Key	KEY_EXCEPTIONS		= Key.of( "exceptions" );
	private static final Key	KEY_ENABLED			= Key.of( "enabled" );
	private static final Key	KEY_STARTED_AT		= Key.of( "startedAt" );
	private static final Key	KEY_MAX_EXCEPTIONS	= Key.of( "maxExceptions" );

	public LensAddException() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, Key.of( "exception" ) )
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

		IStruct	settings		= moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
		Integer	maxExceptions	= settings.getAsInteger( KEY_MAX_EXCEPTIONS );
		Array	exceptions		= lensData.getAsArray( KEY_EXCEPTIONS );

		if ( exceptions.size() >= maxExceptions ) {
			return null;
		}

		KeyDictionary.getLensService( settings ).getStats().totalExceptions.incrementAndGet();

		Object rawEx = arguments.get( Key.of( "exception" ) );
		IStruct ex = rawEx instanceof IStruct ? ( IStruct ) rawEx : Struct.of();

		long startedAt = lensData.getAsLong( KEY_STARTED_AT );

		exceptions.add( Struct.of(
		    "type", ex.getOrDefault( Key.of( "type" ), "unknown" ),
		    "message", ex.getOrDefault( Key.of( "message" ), "" ),
		    "detail", ex.getOrDefault( Key.of( "detail" ), "" ),
		    "stackTrace", ex.getOrDefault( Key.of( "stackTrace" ), "" ),
		    "offset", System.currentTimeMillis() - startedAt
		) );

		return null;
	}

}
