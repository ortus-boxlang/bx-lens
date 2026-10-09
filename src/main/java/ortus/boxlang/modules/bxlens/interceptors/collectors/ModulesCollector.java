/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.Collections;
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

	private static final long		TTL		= 30_000L;
	private volatile long			builtAt;
	private volatile List<Object>	cached	= List.of();

	@Override
	public boolean snapshotOnly() {
		return true;
	}

	@Override
	public void onRequestFinish( LensRequest req ) {
		long now = System.currentTimeMillis();
		if ( now - builtAt > TTL || cached.isEmpty() ) {
			cached	= Collections.unmodifiableList( build() );
			builtAt	= now;
		}
		req.data.put( "modules", cached );
	}

	/**
	 * The module list changes only when a module is loaded or reloaded, so it is rebuilt at most every thirty seconds, not for every request.
	 */
	private List<Object> build() {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for ( Map.Entry<ortus.boxlang.runtime.scopes.Key, ModuleRecord> e : BoxRuntime.getInstance().getModuleService().getRegistry().entrySet() ) {
				ModuleRecord		r	= e.getValue();
				Map<String, Object>	m	= new LinkedHashMap<>();
				m.put( "name", e.getKey().getName() );
				m.put( "version", r.version );
				m.put( "author", r.author );
				m.put( "description", r.description );
				m.put( "webURL", webUrl( r.webURL ) );
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
		return new ArrayList<>( out );
	}

	/**
	 * Only a web address is shown as a link.
	 */
	public static String webUrl( Object url ) {
		String s = url == null ? "" : url.toString().trim();
		return s.regionMatches( true, 0, "https://", 0, 8 ) || s.regionMatches( true, 0, "http://", 0, 7 ) ? s : "";
	}

}
