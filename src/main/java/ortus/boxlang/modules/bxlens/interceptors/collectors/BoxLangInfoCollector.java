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

/**
 * Captures BoxLang/Java/OS server info at request start.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class BoxLangInfoCollector extends BaseCollector {

	@Override
	public String getName() {
		return "boxlangInfo";
	}

	@InterceptionPoint
	public void onRequestStart( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;

			Map<String, Object> info = new LinkedHashMap<>();
			info.put( "javaVersion", System.getProperty( "java.version", "" ) );
			info.put( "osName", System.getProperty( "os.name", "" ) );
			info.put( "osArch", System.getProperty( "os.arch", "" ) );

			// Try to get BoxLang version from server scope
			IBoxContext ctx = getCtx( event );
			if ( ctx != null ) {
				try {
					IStruct serverScope = ( IStruct ) ctx.getScopeNearby( Key.of( "server" ) );
					if ( serverScope != null ) {
						IStruct bxInfo = serverScope.getAsStruct( Key.of( "boxlang" ) );
						if ( bxInfo != null ) {
							info.put( "version", bxInfo.getOrDefault( Key.of( "version" ), "" ) );
						}
					}
				} catch ( Exception ignored ) {
				}
			}

			// Record in appEvents for access at render time
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put( "type", "serverInfo" );
			entry.put( "info", info );
			entry.put( "offset", 0 );
			data.appEvents.add( entry );
		} catch ( Exception e ) {
			// Fail silently
		}
	}

}
