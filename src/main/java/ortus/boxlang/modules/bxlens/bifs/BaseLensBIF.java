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
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Base class for all bx-lens BIFs.
 * Provides convenience accessors for LensService, LensRequestData, and module settings.
 */
public abstract class BaseLensBIF extends BIF {

	protected IBoxContext getRequestContext( IBoxContext ctx ) {
		IBoxContext req = ctx.getRequestContext();
		return req != null ? req : ctx;
	}

	protected LensRequestData getLensData( IBoxContext ctx ) {
		return LensRequestData.get( getRequestContext( ctx ) );
	}

	protected LensService getLensService() {
		return ( LensService ) runtime.getGlobalService( LensService.NAME );
	}

	protected IStruct getModuleSettings() {
		return moduleService.getModuleRecord( KeyDictionary.moduleName ).settings;
	}

	protected boolean isEnabled( LensRequestData data ) {
		return data != null && data.enabled;
	}

}
