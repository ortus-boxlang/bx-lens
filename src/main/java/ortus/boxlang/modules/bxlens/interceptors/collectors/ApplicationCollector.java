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
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles request lifecycle and application events.
 * Always registered — initializes/finalizes LensRequestData and tracks app lifecycle.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ApplicationCollector extends BaseCollector {

	@Override
	public String getName() {
		return "application";
	}

	@InterceptionPoint
	public void onRequestStart( IStruct event ) {
		try {
			IBoxContext	ctx			= getCtx( event );
			if ( ctx == null ) return;

			LensService	svc			= getLensService();
			if ( svc == null ) return;

			IStruct		settings	= getModuleSettings();
			boolean		enabled		= Boolean.TRUE.equals( settings.getOrDefault( Key.of( "enabled" ), Boolean.TRUE ) );
			String		reqId		= svc.startRequest();

			LensRequestData data = LensRequestData.getOrCreate( ctx, reqId, enabled );
			if ( data == null ) return;

			// Capture HTTP method/URL safely (not available in CLI context)
			try {
				Object exchange = ctx.getClass().getMethod( "getHTTPExchange" ).invoke( ctx );
				if ( exchange != null ) {
					Object method	= exchange.getClass().getMethod( "getRequestMethod" ).invoke( exchange );
					Object url		= exchange.getClass().getMethod( "getRequestURL" ).invoke( exchange );
					if ( method != null ) data.method = method.toString();
					if ( url != null ) data.url = url.toString();
				}
			} catch ( Exception ignored ) {}
		} catch ( Exception e ) {
			// Fail silently - never break a request due to lens
		}
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null ) return;

			long		now			= System.currentTimeMillis();
			data.endedAt = now;
			long		duration	= now - data.startedAt;

			LensService svc = getLensService();
			if ( svc != null ) {
				svc.endRequest( duration );
				data.globalStats = svc.getStats().snapshot();
			}
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void onSessionStart( IStruct event ) {
		try {
			LensService svc = getLensService();
			if ( svc != null ) svc.getStats().activeSessions.incrementAndGet();
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void onSessionEnd( IStruct event ) {
		try {
			LensService svc = getLensService();
			if ( svc != null ) svc.getStats().activeSessions.decrementAndGet();
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void onApplicationStart( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled ) return;

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "type", "applicationStart" );
			entry.put( "name", event.getOrDefault( Key.of( "applicationName" ), "" ) );
			entry.put( "offset", System.currentTimeMillis() - data.startedAt );
			data.appEvents.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void onApplicationEnd( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled ) return;

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "type", "applicationEnd" );
			entry.put( "name", event.getOrDefault( Key.of( "applicationName" ), "" ) );
			entry.put( "offset", System.currentTimeMillis() - data.startedAt );
			data.appEvents.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
