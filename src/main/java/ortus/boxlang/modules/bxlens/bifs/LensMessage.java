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

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LensMessage( message [, type] ) — Add a developer message to the current request's debug panel.
 */
@BoxBIF
public class LensMessage extends BaseLensBIF {

	private static final Key KEY_MAX_MESSAGES = Key.of( "maxMessages" );

	public LensMessage() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "message" ) ),
		    new Argument( false, Argument.STRING, Key.of( "type" ), "info" )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequestData data = getLensData( context );
		if ( !isEnabled( data ) )
			return null;

		int maxMessages = getModuleSettings().getAsInteger( KEY_MAX_MESSAGES );
		if ( data.messages.size() >= maxMessages )
			return null;

		LensService svc = getLensService();
		if ( svc != null )
			svc.getStats().totalMessages.incrementAndGet();

		String				message	= arguments.getAsString( Key.of( "message" ) );
		String				type	= arguments.getAsString( Key.of( "type" ) );

		Map<String, Object>	entry	= new LinkedHashMap<>();
		entry.put( "message", message );
		entry.put( "type", type );
		entry.put( "offset", System.currentTimeMillis() - data.startedAt );
		data.messages.add( entry );

		return null;
	}

}
