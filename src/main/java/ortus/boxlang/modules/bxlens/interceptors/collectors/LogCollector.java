/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
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
	public boolean heavy() {
		return true;
	}

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
