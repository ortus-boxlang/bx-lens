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

import ortus.boxlang.modules.bxlens.model.LensRequest;

/**
 * A Lens collector. One class per kind of data. Collectors that listen to BoxLang events extend {@link BaseCollector},
 * which makes them interceptors that the service registers when the collector is enabled in the settings.
 * <p>
 * Collectors are shared across threads: keep per-request state in the {@link LensRequest}, never in fields.
 */
public interface ILensCollector {

	/**
	 * Unique id, also the key under <code>collectors</code> in the settings.
	 */
	String id();

	/**
	 * Is this collector on when the settings do not mention it? Expensive collectors return false.
	 */
	default boolean enabledByDefault() {
		return true;
	}

	/**
	 * Is this collector too heavy or too revealing for the light collect level? Heavy collectors are not registered at that level.
	 */
	default boolean heavy() {
		return false;
	}

	/**
	 * Called when a tracked request starts.
	 */
	default void onRequestStart( LensRequest request ) {
	}

	/**
	 * Called when a tracked request finishes, before the snapshot is built. Collectors that gather data at the end of a request do it here.
	 */
	default void onRequestFinish( LensRequest request ) {
	}

}
