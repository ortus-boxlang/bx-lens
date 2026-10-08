/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.modules.ModuleRecord;

/**
 * Lists the loaded BoxLang modules with their basic information: version, author, description, state and what each one registered.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ModulesCollector extends BaseCollector {

	@Override
	public String id() {
		return "modules";
	}

	@Override
	public void onRequestFinish( LensRequest req ) {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for ( Map.Entry<ortus.boxlang.runtime.scopes.Key, ModuleRecord> e : BoxRuntime.getInstance().getModuleService().getRegistry().entrySet() ) {
				ModuleRecord		r	= e.getValue();
				Map<String, Object>	m	= new LinkedHashMap<>();
				m.put( "name", e.getKey().getName() );
				m.put( "version", r.version );
				m.put( "author", r.author );
				m.put( "description", r.description );
				m.put( "webURL", r.webURL );
				m.put( "enabled", r.enabled );
				m.put( "activated", r.activated );
				m.put( "activationMs", r.activationTime );
				m.put( "bifs", r.bifs.size() );
				m.put( "components", r.components.size() );
				m.put( "interceptors", r.interceptors.size() );
				m.put( "dependencies", new ArrayList<>( r.dependencies ) );
				m.put( "path", r.physicalPath == null ? "" : r.physicalPath.toString() );
				m.put( "self", Keys.moduleName.equals( e.getKey() ) );
				out.add( m );
			}
		} catch ( Throwable t ) {
			fail( "list modules", t );
		}
		out.sort( Comparator.comparing( m -> String.valueOf( m.get( "name" ) ).toLowerCase() ) );
		req.data.put( "modules", out );
	}

}
