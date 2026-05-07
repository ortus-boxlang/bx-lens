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
 * LensMessage( message [, type] ) — Add a developer message to the current request's debug panel.
 */
@BoxBIF
public class LensMessage extends BIF {

	private static final Key	KEY_MESSAGES		= Key.of( "messages" );
	private static final Key	KEY_ENABLED			= Key.of( "enabled" );
	private static final Key	KEY_STARTED_AT		= Key.of( "startedAt" );
	private static final Key	KEY_MAX_MESSAGES	= Key.of( "maxMessages" );

	public LensMessage() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "message" ) ),
		    new Argument( false, Argument.STRING, Key.of( "type" ), "info" )
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

		IStruct settings = moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
		Integer maxMessages = settings.getAsInteger( KEY_MAX_MESSAGES );
		Array messages = lensData.getAsArray( KEY_MESSAGES );

		if ( messages.size() >= maxMessages ) {
			return null;
		}

		KeyDictionary.getLensService( settings ).getStats().totalMessages.incrementAndGet();

		String message = arguments.getAsString( Key.of( "message" ) );
		String type = arguments.getAsString( Key.of( "type" ) );
		long startedAt = lensData.getAsLong( KEY_STARTED_AT );

		messages.add( Struct.of(
		    "message", message,
		    "type", type,
		    "offset", System.currentTimeMillis() - startedAt
		) );

		return null;
	}

}
