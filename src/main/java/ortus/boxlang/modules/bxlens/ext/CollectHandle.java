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
package ortus.boxlang.modules.bxlens.ext;

import ortus.boxlang.modules.bxlens.model.LensRequest;

/**
 * Handle given to <code>onLensCollect</code> listeners as <code>event.lens</code>. It scopes panel creation to the request being finished.
 */
public class CollectHandle {

	private final LensRequest request;

	public CollectHandle( LensRequest request ) {
		this.request = request;
	}

	/**
	 * Get a panel for this request. Declare its look first with the registry, or pass a label and renderer here.
	 */
	public LensPanelBuilder panel( String id ) {
		return request.panel( id, id, "table" );
	}

	/**
	 * Get a panel for this request with an explicit label and renderer.
	 */
	public LensPanelBuilder panel( String id, String label, String renderer ) {
		return request.panel( id, label, renderer );
	}

	public String getRequestId() {
		return request.id;
	}

	public String getMethod() {
		return request.method;
	}

	public String getUri() {
		return request.uri;
	}

	public int getStatus() {
		return request.status;
	}

}
