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

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Sends any value to the Messages panel as a collapsible tree, instead of dumping it into the page.
 * <p>
 * Example: <code>lensDump( order, "order after pricing" )</code>
 *
 * @argument.value The value to inspect
 *
 * @argument.label Optional title
 */
@BoxBIF
public class LensDump extends BaseLensBIF {

	private static final Key	VALUE	= Key.of( "value" );
	private static final Key	LABEL	= Key.of( "label" );

	public LensDump() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, VALUE ),
		    new Argument( false, Argument.STRING, LABEL, "" )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "messages", true )
		    || !req.reserve( "message", service().getConfig().collectorInt( "messages", "max", 200 ) ) ) {
			return null;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "level", "dump" );
		m.put( "text", arguments.getAsString( LABEL ) );
		m.put( "data", sanitizer().clean( arguments.get( VALUE ) ) );
		m.put( "at", Span.ms( req.now() ) );
		req.messages.add( m );
		return null;
	}

}
