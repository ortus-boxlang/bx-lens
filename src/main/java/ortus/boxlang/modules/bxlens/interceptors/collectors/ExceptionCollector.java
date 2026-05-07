/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects exception data from BoxLang error events.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ExceptionCollector extends BaseCollector {

	@Override
	public String getName() {
		return "exceptions";
	}

	@InterceptionPoint
	public void onException( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;
			if ( data.exceptions.size() >= getMaxSetting( "maxExceptions", 50 ) )
				return;

			Object rawEx = event.get( Key.of( "exception" ) );
			if ( rawEx == null )
				return;

			IStruct		ex	= rawEx instanceof IStruct ? ( IStruct ) rawEx : null;

			LensService	svc	= getLensService();
			if ( svc != null )
				svc.getStats().totalExceptions.incrementAndGet();

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "type", ex != null ? ex.getOrDefault( Key.of( "type" ), "unknown" ) : "unknown" );
			entry.put( "message", ex != null ? ex.getOrDefault( Key.of( "message" ), "" ) : rawEx.toString() );
			entry.put( "detail", ex != null ? ex.getOrDefault( Key.of( "detail" ), "" ) : "" );
			entry.put( "stackTrace", ex != null ? ex.getOrDefault( Key.of( "stackTrace" ), "" ) : "" );
			entry.put( "offset", System.currentTimeMillis() - data.startedAt );
			data.exceptions.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
