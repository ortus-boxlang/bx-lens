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
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LensAddException( exception ) — Manually log a caught exception to the debug panel.
 */
@BoxBIF
public class LensAddException extends BaseLensBIF {

	private static final Key KEY_MAX_EXCEPTIONS = Key.of( "maxExceptions" );

	public LensAddException() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, Key.of( "exception" ) )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequestData data = getLensData( context );
		if ( !isEnabled( data ) ) return null;

		int maxExceptions = getModuleSettings().getAsInteger( KEY_MAX_EXCEPTIONS );
		if ( data.exceptions.size() >= maxExceptions ) return null;

		LensService svc = getLensService();
		if ( svc != null ) svc.getStats().totalExceptions.incrementAndGet();

		Object rawEx = arguments.get( Key.of( "exception" ) );
		IStruct ex = rawEx instanceof IStruct ? ( IStruct ) rawEx : null;

		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put( "type", ex != null ? ex.getOrDefault( Key.of( "type" ), "unknown" ) : "unknown" );
		entry.put( "message", ex != null ? ex.getOrDefault( Key.of( "message" ), "" ) : ( rawEx != null ? rawEx.toString() : "" ) );
		entry.put( "detail", ex != null ? ex.getOrDefault( Key.of( "detail" ), "" ) : "" );
		entry.put( "stackTrace", ex != null ? ex.getOrDefault( Key.of( "stackTrace" ), "" ) : "" );
		entry.put( "offset", System.currentTimeMillis() - data.startedAt );
		data.exceptions.add( entry );

		return null;
	}

}
