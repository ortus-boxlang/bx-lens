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

/**
 * Tracks template invocations for timeline visualization.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class TimelineCollector extends BaseCollector {

	@Override
	public String getName() {
		return "timeline";
	}

	@InterceptionPoint
	public void preTemplateInvoke( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;

			long	now		= System.currentTimeMillis();

			// Nesting depth = number of currently pending template entries
			int		depth	= 0;
			for ( Map<String, Object> t : data.templates ) {
				if ( Boolean.TRUE.equals( t.get( "_pending" ) ) )
					depth++;
			}

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "templatePath", event.getOrDefault( Key.of( "templatePath" ), "" ) );
			entry.put( "executionTime", 0L );
			entry.put( "offset", now - data.startedAt );
			entry.put( "depth", depth );
			entry.put( "_pending", Boolean.TRUE );
			entry.put( "_startTick", now );
			data.templates.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	@InterceptionPoint
	public void postTemplateInvoke( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;
			if ( data.templates.size() >= getMaxSetting( "maxTemplates", 200 ) )
				return;

			String	path		= event.getOrDefault( Key.of( "templatePath" ), "" ).toString();
			long	now			= System.currentTimeMillis();
			Object	rawExecTime	= event.get( Key.of( "executionTime" ) );

			// Walk backwards to find the most-recent pending entry for this path
			for ( int i = data.templates.size() - 1; i >= 0; i-- ) {
				Map<String, Object> t = data.templates.get( i );
				if ( Boolean.TRUE.equals( t.get( "_pending" ) )
				    && path.equals( t.get( "templatePath" ) ) ) {
					long startTick = t.get( "_startTick" ) instanceof Number
					    ? ( ( Number ) t.get( "_startTick" ) ).longValue()
					    : now;
					t.put( "executionTime", rawExecTime instanceof Number
					    ? ( ( Number ) rawExecTime ).longValue()
					    : now - startTick );
					t.put( "_pending", Boolean.FALSE );
					t.remove( "_startTick" );
					break;
				}
			}
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
