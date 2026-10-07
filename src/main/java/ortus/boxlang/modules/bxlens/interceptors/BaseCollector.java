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
package ortus.boxlang.modules.bxlens.interceptors;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Base class for collectors that listen to BoxLang events. Provides the tracked {@link LensRequest} for an event, if there is one.
 * A request is only tracked when Lens is enabled, the caller is allowed and the request is not excluded, so a null result means "do nothing".
 */
public abstract class BaseCollector extends BaseInterceptor implements ILensCollector {

	protected LensService service() {
		return LensService.getInstance();
	}

	protected LensConfig config() {
		return LensService.getInstance().getConfig();
	}

	/**
	 * Request context of an event, falling back to the thread's current request.
	 */
	protected RequestBoxContext requestContext( IStruct event ) {
		Object		raw	= event == null ? null : event.get( Keys.context );
		IBoxContext	ctx	= raw instanceof IBoxContext c ? c : RequestBoxContext.getCurrent();
		return ctx == null ? null : ctx.getRequestContext();
	}

	/**
	 * The tracked request for an event or null.
	 */
	protected LensRequest request( IStruct event ) {
		RequestBoxContext rc = requestContext( event );
		if ( rc == null ) {
			return null;
		}
		LensRequest r = rc.getAttachment( Keys.requestAttach );
		return r != null && r.enabled && !r.finished.get() ? r : null;
	}

	/**
	 * Log a collector failure at debug level. Collectors must never break a request.
	 */
	protected void fail( String where, Throwable t ) {
		try {
			LensService.getInstance().getLogger().debug( "Collector [{}] failed in [{}]: {}", id(), where, t.toString() );
		} catch ( Throwable ignored ) {
			// Nothing else to do
		}
	}

}
