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

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensThreadDump( [threadId] ) — Get a formatted thread dump from the JVM.
 * If threadId is omitted, dumps all threads.
 */
@BoxBIF
public class LensThreadDump extends BaseLensBIF {

	public LensThreadDump() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, Argument.LONG, Key.of( "threadId" ), null )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensService svc = getLensService();
		if ( svc == null )
			return "LensService is not available";

		Long threadId = arguments.getAsLong( Key.of( "threadId" ) );
		if ( threadId != null ) {
			return svc.getThreadDump( threadId );
		}
		return svc.getThreadDump();
	}

}
