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
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Captures scope snapshots at request end.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ScopesCollector extends BaseCollector {

	@Override
	public String getName() {
		return "scopes";
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct event ) {
		try {
			LensRequestData data = getLensData( event );
			if ( data == null || !data.enabled )
				return;

			IBoxContext ctx = getCtx( event );
			if ( ctx == null )
				return;

			IStruct	settings		= getModuleSettings();
			Object	rawCollectors	= settings.getOrDefault( Key.of( "collectors" ), null );
			IStruct	scopeSettings	= null;

			if ( rawCollectors instanceof IStruct ) {
				Object rawScopes = ( ( IStruct ) rawCollectors ).getOrDefault( Key.of( "scopes" ), null );
				if ( rawScopes instanceof IStruct ) {
					scopeSettings = ( IStruct ) rawScopes;
				}
			}

			boolean	captureForm		= scopeSettings == null || Boolean.TRUE.equals( scopeSettings.getOrDefault( Key.of( "form" ), Boolean.TRUE ) );
			boolean	captureSession	= scopeSettings != null && Boolean.TRUE.equals( scopeSettings.getOrDefault( Key.of( "session" ), Boolean.FALSE ) );
			boolean	captureRequest	= scopeSettings != null && Boolean.TRUE.equals( scopeSettings.getOrDefault( Key.of( "request" ), Boolean.FALSE ) );

			if ( captureForm ) {
				data.formScope = captureScope( ctx, Key.of( "form" ) );
			}
			if ( captureSession ) {
				data.sessionScope = captureScope( ctx, Key.of( "session" ) );
			}
			if ( captureRequest ) {
				data.requestScope = captureScope( ctx, Key.of( "request" ) );
			}
		} catch ( Exception e ) {
			// Fail silently
		}
	}

	private IStruct captureScope( IBoxContext ctx, Key scopeKey ) {
		try {
			IScope scope = ctx.getScopeNearby( scopeKey );
			if ( scope != null ) {
				return Struct.fromMap( scope );
			}
		} catch ( Exception ignored ) {
		}
		return Struct.of();
	}

}
