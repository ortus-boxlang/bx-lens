/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;

/**
 * Adds the runtime snapshot to the page: BoxLang and Java versions, the system, uptime, heap, the names of the caches and a short list of the
 * main settings. It is read from the runtime and cached for a few seconds ({@link ortus.boxlang.modules.bxlens.RuntimeInfo}), so a request
 * pays for a field read, not for a rebuild.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class JvmCollector extends BaseCollector {

	@Override
	public String id() {
		return "jvm";
	}

	@Override
	public boolean snapshotOnly() {
		return true;
	}

	@Override
	public void onRequestFinish( LensRequest req ) {
		req.data.put( "runtime", service().getRuntimeInfo().snapshot() );
	}

}
