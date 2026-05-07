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
package ortus.boxlang.modules.bxlens.util;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Central registry of Key constants used by bx-lens.
 */
public class KeyDictionary {

	public static final Key	moduleName	= new Key( "bxLens" );
	public static final Key	lensData	= new Key( "__bxLensData__" );

	/**
	 * Retrieve the LensService from the global service registry.
	 *
	 * @return the LensService instance, or null if not yet registered
	 */
	public static LensService getLensService() {
		return ( LensService ) BoxRuntime.getInstance().getGlobalService( LensService.NAME );
	}

}
