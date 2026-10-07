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
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Times a closure and returns its result. Works even when the request is not tracked, in which case it just runs the closure.
 * <p>
 * Example: <code>orders = lensMeasure( "load orders", () => orderService.list() )</code>
 *
 * @argument.label Name of the measure
 *
 * @argument.callable The function to time
 */
@BoxBIF
public class LensMeasure extends BaseLensBIF {

	private static final Key	LABEL		= Key.of( "label" );
	private static final Key	CALLABLE	= Key.of( "callable" );

	public LensMeasure() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL ),
		    new Argument( true, Argument.FUNCTION, CALLABLE )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest	req		= request( context );
		long		start	= req == null ? 0 : req.now();
		try {
			return context.invokeFunction( arguments.get( CALLABLE ), new Object[] {} );
		} finally {
			if ( req != null ) {
				LensStop.record( req, arguments.getAsString( LABEL ), start, req.now() );
			}
		}
	}

}
