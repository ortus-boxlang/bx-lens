/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
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
