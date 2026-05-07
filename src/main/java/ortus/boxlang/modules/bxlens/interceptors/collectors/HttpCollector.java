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
 * Collects outgoing HTTP call data.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class HttpCollector extends BaseCollector {

	@Override
	public String getName() {
		return "http";
	}

	@InterceptionPoint
	public void onHTTPRequest( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;

			long				now		= System.currentTimeMillis();
			Map<String, Object>	entry	= new LinkedHashMap<>();
			entry.put( "method", event.getOrDefault( Key.of( "method" ), "GET" ) );
			entry.put( "url", event.getOrDefault( Key.of( "url" ), "" ) );
			entry.put( "statusCode", 0 );
			entry.put( "executionTime", 0L );
			entry.put( "requestSize", event.getOrDefault( Key.of( "bodyLength" ), 0 ) );
			entry.put( "responseSize", 0 );
			entry.put( "offset", now - data.startedAt );
			entry.put( "_pending", Boolean.TRUE );
			entry.put( "_startTick", now );
			data.httpCalls.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void onHTTPResponse( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;

			LensService svc = getLensService();
			if ( svc != null )
				svc.getStats().totalHttpCalls.incrementAndGet();

			String	url			= ( String ) event.getOrDefault( Key.of( "url" ), "" );
			long	now			= System.currentTimeMillis();
			Object	rawExecTime	= event.get( Key.of( "executionTime" ) );

			for ( int i = data.httpCalls.size() - 1; i >= 0; i-- ) {
				Map<String, Object> h = data.httpCalls.get( i );
				if ( Boolean.TRUE.equals( h.get( "_pending" ) )
				    && url.equals( h.get( "url" ) ) ) {
					h.put( "statusCode", event.getOrDefault( Key.of( "statusCode" ), 0 ) );
					h.put( "responseSize", event.getOrDefault( Key.of( "responseSize" ), 0 ) );
					long startTick = h.get( "_startTick" ) instanceof Number
					    ? ( ( Number ) h.get( "_startTick" ) ).longValue()
					    : now;
					h.put( "executionTime", rawExecTime instanceof Number
					    ? ( ( Number ) rawExecTime ).longValue()
					    : now - startTick );
					h.put( "_pending", Boolean.FALSE );
					h.remove( "_startTick" );
					break;
				}
			}
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
