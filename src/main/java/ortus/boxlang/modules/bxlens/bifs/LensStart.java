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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensStart( label ) — Start a named custom timer.
 */
@BoxBIF
public class LensStart extends BaseLensBIF {

	public LensStart() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "label" ) )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String			label	= arguments.getAsString( Key.of( "label" ) );

		LensRequestData	data	= getLensData( context );
		if ( !isEnabled( data ) )
			return label;

		long				now		= System.currentTimeMillis();
		String				hash	= label + "_" + UUID.randomUUID().toString();
		Map<String, Object>	entry	= new HashMap<>();
		entry.put( "label", label );
		entry.put( "_startTick", now );
		entry.put( "offset", now - data.startedAt );
		data.pendingTimings.put( hash, entry );

		return hash;
	}

}
