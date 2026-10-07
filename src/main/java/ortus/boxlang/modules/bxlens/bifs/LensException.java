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

import ortus.boxlang.modules.bxlens.interceptors.collectors.ExceptionCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records an exception you caught, so it shows in the Exceptions panel and Issues even though the request carried on.
 * <p>
 * Example: <code>try { risky() } catch ( any e ) { lensException( e ); }</code>
 *
 * @argument.exception The caught exception
 */
@BoxBIF
public class LensException extends BaseLensBIF {

	private static final Key EXCEPTION = Key.of( "exception" );

	public LensException() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, EXCEPTION )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "exceptions", true ) ) {
			return null;
		}
		Object		raw	= arguments.get( EXCEPTION );
		Throwable	t;
		if ( raw instanceof Throwable th ) {
			t = th;
		} else if ( raw instanceof IStruct s ) {
			t = new RuntimeException( String.valueOf( s.getOrDefault( Key.of( "message" ), "Exception" ) ) );
		} else {
			t = new RuntimeException( String.valueOf( raw ) );
		}
		ExceptionCollector.record( req, t, "manual", service().getConfig() );
		return null;
	}

}
