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
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects BIF (Built-In Function) call data. Disabled by default (expensive).
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class BifCallCollector extends BaseCollector {

	// Track in-flight BIF calls per thread
	private final ConcurrentHashMap<Long, Map<String, Object>> pendingBifCalls = new ConcurrentHashMap<>();

	@Override
	public String getName() {
		return "bifCalls";
	}

	@InterceptionPoint
	public void onBIFInvocation( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;
			if ( data.bifCalls.size() >= getMaxSetting( "maxBifCalls", 500 ) )
				return;

			String bifName = event.getOrDefault( Key.of( "functionName" ), event.getOrDefault( Key.of( "name" ), "unknown" ) ).toString();

			// Skip self-referential Lens BIF calls
			if ( bifName.toLowerCase().startsWith( "lens" ) )
				return;

			long				now		= System.currentTimeMillis();
			Map<String, Object>	entry	= new LinkedHashMap<>();
			entry.put( "bifName", bifName );
			entry.put( "_startTick", now );
			entry.put( "offset", now - data.startedAt );
			pendingBifCalls.put( Thread.currentThread().getId(), entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void postBIFInvocation( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;
			if ( data.bifCalls.size() >= getMaxSetting( "maxBifCalls", 500 ) )
				return;

			Map<String, Object> pending = pendingBifCalls.remove( Thread.currentThread().getId() );
			if ( pending == null )
				return;

			long				now			= System.currentTimeMillis();
			long				startTick	= pending.get( "_startTick" ) instanceof Number
			    ? ( ( Number ) pending.get( "_startTick" ) ).longValue()
			    : now;

			Map<String, Object>	entry		= new LinkedHashMap<>();
			entry.put( "bifName", pending.get( "bifName" ) );
			entry.put( "executionTime", now - startTick );
			entry.put( "offset", pending.get( "offset" ) );
			data.bifCalls.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
