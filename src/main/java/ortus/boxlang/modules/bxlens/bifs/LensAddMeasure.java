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
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensAddMeasure( label, executionTime [, offset] ) — Add a pre-computed timing to the debug panel.
 */
@BoxBIF
public class LensAddMeasure extends BaseLensBIF {

	private static final Key KEY_MAX_TIMINGS = Key.of( "maxTimings" );

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
		LensRequestData data = getLensData( context );
		if ( !isEnabled( data ) ) return null;

		int maxTimings = getModuleSettings().getAsInteger( KEY_MAX_TIMINGS );
		if ( data.timings.size() >= maxTimings ) return null;

		String	label			= arguments.getAsString( Key.of( "label" ) );
		long	executionTime	= arguments.getAsLong( Key.of( "executionTime" ) );
		long	offset			= arguments.getAsLong( Key.of( "offset" ) );

		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put( "label", label );
		entry.put( "executionTime", executionTime );
		entry.put( "offset", offset > 0 ? offset : System.currentTimeMillis() - data.startedAt );
		entry.put( "hash", UUID.randomUUID().toString() );
		data.timings.add( entry );

		return null;
	}

}
