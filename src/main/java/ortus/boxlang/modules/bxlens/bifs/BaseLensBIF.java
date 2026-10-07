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
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.context.IBoxContext;

/**
 * Base class for the Lens BIFs. They are all safe to call when Lens is disabled or the request is not tracked: they do nothing.
 */
public abstract class BaseLensBIF extends BIF {

	protected LensService service() {
		return LensService.getInstance();
	}

	/**
	 * The tracked request for this context, or null.
	 */
	protected LensRequest request( IBoxContext context ) {
		return service().current( context );
	}

	protected Sanitizer sanitizer() {
		return new Sanitizer( service().getConfig() );
	}

}
