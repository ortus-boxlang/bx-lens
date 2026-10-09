/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

/**
 * How Lens itself is doing: its version, whether it collects, the history it holds, and its work queue (waiting, done, dropped and failed
 * tasks). Works in any request.
 */
@BoxBIF
public class LensDiagnostics extends BaseLensBIF {

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "version", service().getVersion() );
		m.put( "enabled", service().isEnabled() );
		m.put( "collectLevel", service().getConfig().collectLevel );
		m.put( "history", Map.of( "size", service().getStore().size(), "capacity", service().getStore().capacity() ) );
		m.put( "async", service().asyncStats() );
		return BoxData.of( m );
	}

}
