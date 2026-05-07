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
 * Collects SQL query execution data.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class QueryCollector extends BaseCollector {

	@Override
	public String getName() {
		return "queries";
	}

	@InterceptionPoint
	public void postQueryExecute( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled ) return;
			if ( data.queries.size() >= getMaxSetting( "maxQueries", 100 ) ) return;

			LensService svc = getLensService();
			if ( svc != null ) svc.getStats().totalQueries.incrementAndGet();

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "sql", event.getOrDefault( Key.of( "sql" ), "" ) );
			entry.put( "executionTime", event.getOrDefault( Key.of( "executionTime" ), 0L ) );
			entry.put( "recordCount", event.getOrDefault( Key.of( "recordCount" ), 0 ) );
			entry.put( "params", event.getOrDefault( Key.of( "params" ), "" ) );
			entry.put( "offset", System.currentTimeMillis() - data.startedAt );
			data.queries.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
