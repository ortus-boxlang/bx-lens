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
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records user defined function and method calls as spans. Off by default because it sits on the hottest path in the runtime.
 * <p>
 * Functions that throw never announce <code>postFunctionInvoke</code>; the span is closed when its parent closes.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class FunctionCollector extends BaseCollector {

	@Override
	public String id() {
		return "functions";
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@InterceptionPoint
	public void preFunctionInvoke( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			req.begin( Span.FUNCTION, label( event ), config().collectorInt( id(), "max", 1000 ) );
		} catch ( Throwable t ) {
			fail( "preFunctionInvoke", t );
		}
	}

	@InterceptionPoint
	public void postFunctionInvoke( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Span span = req.open( Span.FUNCTION, label( event ) );
			if ( span != null ) {
				req.end( span );
				double minMs = config().collectorInt( id(), "minMs", 0 );
				if ( minMs > 0 && Span.ms( span.durationNs() ) < minMs ) {
					req.spans.remove( span );
				}
			}
		} catch ( Throwable t ) {
			fail( "postFunctionInvoke", t );
		}
	}

	private String label( IStruct event ) {
		return String.valueOf( event.get( Keys.name ) ) + "()";
	}

}
