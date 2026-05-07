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
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LensStop( labelOrHash ) — Stop a custom timer started with LensStart().
 */
@BoxBIF
public class LensStop extends BaseLensBIF {

	private static final Key KEY_MAX_TIMINGS = Key.of( "maxTimings" );

	public LensStop() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.of( "labelOrHash" ) )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequestData data = getLensData( context );
		if ( !isEnabled( data ) ) return null;

		int maxTimings = getModuleSettings().getAsInteger( KEY_MAX_TIMINGS );
		if ( data.timings.size() >= maxTimings ) return null;

		String	labelOrHash	= arguments.getAsString( Key.of( "labelOrHash" ) );
		String	found		= null;

		// Try exact hash first
		if ( data.pendingTimings.containsKey( labelOrHash ) ) {
			found = labelOrHash;
		} else {
			// Fall back to most-recent by label
			for ( String k : data.pendingTimings.keySet() ) {
				Map<String, Object> entry = data.pendingTimings.get( k );
				if ( labelOrHash.equals( entry.get( "label" ) ) ) {
					found = k;
				}
			}
		}

		if ( found == null ) return null;

		Map<String, Object>	t			= data.pendingTimings.remove( found );
		long				now			= System.currentTimeMillis();
		long				startTick	= t.get( "_startTick" ) instanceof Number
		    ? ( ( Number ) t.get( "_startTick" ) ).longValue()
		    : now;
		long				offset		= t.get( "offset" ) instanceof Number
		    ? ( ( Number ) t.get( "offset" ) ).longValue()
		    : 0L;

		Map<String, Object> timing = new LinkedHashMap<>();
		timing.put( "label", t.get( "label" ) );
		timing.put( "executionTime", now - startTick );
		timing.put( "offset", offset );
		timing.put( "hash", found );
		data.timings.add( timing );

		return null;
	}

}
