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

import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Captures log lines written with writeLog(), the log component and trace during the request. Off by default.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class LogCollector extends BaseCollector {

	@Override
	public String id() {
		return "logs";
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@InterceptionPoint
	public void logMessage( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null || !req.reserve( "log", config().collectorInt( id(), "max", 200 ) ) ) {
				return;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "text", new Sanitizer( config() ).text( event.get( Keys.text ) ) );
			m.put( "log", String.valueOf( event.get( Keys.log ) ) );
			m.put( "level", String.valueOf( event.get( Keys.type ) ) );
			m.put( "at", Span.ms( req.now() ) );
			req.logs.add( m );
		} catch ( Throwable t ) {
			fail( "logMessage", t );
		}
	}

}
