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
 * Stops a timer started with lensStart(). Pass the id it returned, or the label to stop the most recent timer with that label.
 * <p>
 * Example: <code>lensStop( id )</code>
 *
 * @argument.labelOrId The id returned by lensStart() or the timer label
 */
@BoxBIF
public class LensStop extends BaseLensBIF {

	private static final Key LABEL_OR_ID = Key.of( "labelOrId" );

	public LensStop() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, LABEL_OR_ID )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req == null ) {
			return null;
		}
		String				key		= arguments.getAsString( LABEL_OR_ID );
		Map<String, Object>	timer	= req.pendingTimers.remove( key );
		if ( timer == null ) {
			// Fall back to the newest pending timer with this label
			String found = null;
			for ( Map.Entry<String, Map<String, Object>> e : req.pendingTimers.entrySet() ) {
				if ( key.equals( e.getValue().get( "label" ) ) ) {
					found = e.getKey();
				}
			}
			timer = found == null ? null : req.pendingTimers.remove( found );
		}
		if ( timer == null ) {
			return null;
		}
		long	start	= ( ( Number ) timer.get( "startNs" ) ).longValue();
		long	end		= req.now();
		record( req, String.valueOf( timer.get( "label" ) ), start, end );
		return null;
	}

	/**
	 * Record a finished timer as a timer entry and a span.
	 */
	static void record( LensRequest req, String label, long startNs, long endNs ) {
		if ( !hasRoom( req ) ) {
			return;
		}
		Map<String, Object> t = new LinkedHashMap<>();
		t.put( "label", label );
		t.put( "start", Span.ms( startNs ) );
		t.put( "dur", Span.ms( endNs - startNs ) );
		req.timers.add( t );
		req.addClosed( Span.TIMER, label, startNs, endNs, 200 );
	}

	private static boolean hasRoom( LensRequest req ) {
		return req.reserve( "timer", ortus.boxlang.modules.bxlens.LensService.getInstance().getConfig().collectorInt( "timers", "max", 200 ) );
	}

}
