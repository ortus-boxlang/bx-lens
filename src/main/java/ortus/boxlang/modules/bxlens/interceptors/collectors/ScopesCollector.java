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
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Takes a redacted, size-capped snapshot of selected scopes when the request ends.
 * url and form are on by default. session, request, application, cookie and variables are opt-in.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ScopesCollector extends BaseCollector {

	private static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();
	static {
		DEFAULTS.put( "url", true );
		DEFAULTS.put( "form", true );
		DEFAULTS.put( "cookie", false );
		DEFAULTS.put( "session", false );
		DEFAULTS.put( "request", false );
		DEFAULTS.put( "application", false );
		DEFAULTS.put( "variables", false );
	}

	@Override
	public String id() {
		return "scopes";
	}

	@Override
	public void onRequestFinish( LensRequest req ) {
		if ( req.requestContext == null ) {
			return;
		}
		Sanitizer			clean	= new Sanitizer( config() );
		Map<String, Object>	out		= new LinkedHashMap<>();
		for ( Map.Entry<String, Boolean> e : DEFAULTS.entrySet() ) {
			if ( !config().collectorBool( id(), e.getKey(), e.getValue() ) ) {
				continue;
			}
			try {
				IScope scope = req.requestContext.getScopeNearby( Key.of( e.getKey() ) );
				if ( scope != null ) {
					out.put( e.getKey(), clean.clean( scope ) );
				}
			} catch ( Throwable t ) {
				// Scope not available for this request (for example no session management)
			}
		}
		req.data.put( "scopes", out );
	}

}
