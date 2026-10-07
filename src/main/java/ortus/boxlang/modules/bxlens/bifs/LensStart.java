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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Starts a named timer. Stop it with lensStop(), using the label or the returned id.
 * <p>
 * Example: <code>id = lensStart( "price calculation" )</code>
 *
 * @argument.label Name of the timer
 *
 * @return an id for this timer, or the label when the request is not tracked
 */
@BoxBIF
public class LensStart extends BaseLensBIF {

	private static final Key LABEL = Key.of( "label" );

	public LensStart() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String		label	= arguments.getAsString( LABEL );
		LensRequest	req		= request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "timers", true ) ) {
			return label;
		}
		String				id		= label + "#" + UUID.randomUUID().toString().substring( 0, 6 );
		Map<String, Object>	timer	= new LinkedHashMap<>();
		timer.put( "label", label );
		timer.put( "startNs", req.now() );
		req.pendingTimers.put( id, timer );
		return id;
	}

}
