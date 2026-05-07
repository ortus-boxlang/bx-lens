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
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects user-defined function call data. Disabled by default (expensive).
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class FunctionCallCollector extends BaseCollector {

	// Track in-flight function calls per thread
	private final ConcurrentHashMap<Long, Map<String, Object>> pendingCalls = new ConcurrentHashMap<>();

	@Override
	public String getName() {
		return "functionCalls";
	}

	@InterceptionPoint
	public void preFunctionInvoke( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled ) return;
			if ( data.functionCalls.size() >= getMaxSetting( "maxFunctionCalls", 500 ) ) return;

			String functionName = event.getOrDefault( Key.of( "functionName" ), event.getOrDefault( Key.of( "name" ), "unknown" ) ).toString();

			long				now		= System.currentTimeMillis();
			Map<String, Object>	entry	= new LinkedHashMap<>();
			entry.put( "functionName", functionName );
			entry.put( "_startTick", now );
			entry.put( "offset", now - data.startedAt );
			pendingCalls.put( Thread.currentThread().getId(), entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void postFunctionInvoke( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled ) return;
			if ( data.functionCalls.size() >= getMaxSetting( "maxFunctionCalls", 500 ) ) return;

			Map<String, Object> pending = pendingCalls.remove( Thread.currentThread().getId() );
			if ( pending == null ) return;

			long now		= System.currentTimeMillis();
			long startTick	= pending.get( "_startTick" ) instanceof Number
			    ? ( ( Number ) pending.get( "_startTick" ) ).longValue()
			    : now;

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "functionName", pending.get( "functionName" ) );
			entry.put( "executionTime", now - startTick );
			entry.put( "offset", pending.get( "offset" ) );
			data.functionCalls.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
