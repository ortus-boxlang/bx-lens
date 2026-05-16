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
package ortus.boxlang.modules.bxlens.interceptors;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Base class for all bx-lens collectors.
 * Provides common helpers for getting context, settings, and LensRequestData.
 */
public abstract class BaseCollector extends BaseInterceptor {

	/**
	 * Unique identifier for this collector (e.g. "queries", "http").
	 */
	public abstract String getName();

	/**
	 * Helper to get the LensService instance from the runtime.
	 *
	 * @return
	 */
	protected LensService getLensService() {
		return ( LensService ) getRuntime().getGlobalService( KeyDictionary.bxLensService );
	}

	/**
	 * Helper to get the module settings struct from the runtime.
	 *
	 * @return module settings struct
	 */
	protected IStruct getModuleSettings() {
		return getRuntime().getModuleService().getModuleRecord( KeyDictionary.moduleName ).settings;
	}

	protected IBoxContext getCtx( IStruct event ) {
		Object ctx = event.get( Key.of( "context" ) );
		if ( ctx instanceof IBoxContext )
			return ( IBoxContext ) ctx;
		return RequestBoxContext.getCurrent();
	}

	protected LensRequestData getLensData( IStruct event ) {
		IBoxContext ctx = getCtx( event );
		if ( ctx == null )
			return null;
		IBoxContext req = ctx.getRequestContext();
		if ( req == null )
			req = ctx;
		return req.getAttachment( KeyDictionary.lensData );
	}

	/**
	 * Lazy-creates LensRequestData if not yet present. Only call from request lifecycle events.
	 */
	protected LensRequestData getOrCreateLensData( IStruct event, String requestId, boolean enabled ) {
		IBoxContext ctx = getCtx( event );
		if ( ctx == null )
			return null;
		return LensRequestData.getOrCreate( ctx, requestId, enabled );
	}

	protected int getMaxSetting( String key, int defaultValue ) {
		try {
			Object v = getModuleSettings().getOrDefault( Key.of( key ), defaultValue );
			return v instanceof Number ? ( ( Number ) v ).intValue() : defaultValue;
		} catch ( Exception e ) {
			return defaultValue;
		}
	}

}
