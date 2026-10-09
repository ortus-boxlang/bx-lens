/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * What a server is made of, with secrets hidden: the effective BoxLang configuration, modules, JVM arguments, environment variables and
 * system properties. Also builds the diagnostic bundle (one zip) to attach to a support ticket.
 */
public final class EnvironmentData {

	private final LensService service;

	public EnvironmentData( LensService service ) {
		this.service = service;
	}

	/**
	 * The environment as one map for the console.
	 */
	public Map<String, Object> environment() {
		Map<String, Object>	m	= new LinkedHashMap<>();
		BoxRuntime			rt	= BoxRuntime.getInstance();

		try {
			m.put( "config", walk( rt.getConfiguration().asStruct(), "", 0 ) );
		} catch ( Throwable t ) {
			m.put( "config", Map.of( "error", String.valueOf( t.getMessage() ) ) );
		}
		List<Map<String, Object>> mods = new ArrayList<>();
		try {
			for ( Map.Entry<Key, ModuleRecord> e : rt.getModuleService().getRegistry().entrySet() ) {
				ModuleRecord		r	= e.getValue();
				Map<String, Object>	x	= new LinkedHashMap<>();
				x.put( "name", e.getKey().getName() );
				x.put( "version", r.version );
				x.put( "enabled", r.enabled );
				x.put( "activated", r.activated );
				x.put( "path", r.physicalPath == null ? "" : r.physicalPath.toString() );
				mods.add( x );
			}
		} catch ( Throwable t ) {
			// Keep what we have
		}
		mods.sort( Comparator.comparing( x -> String.valueOf( x.get( "name" ) ).toLowerCase() ) );
		m.put( "modules", mods );
		List<String> args = new ArrayList<>();
		for ( String a : ManagementFactory.getRuntimeMXBean().getInputArguments() ) {
			int eq = a.indexOf( '=' );
			args.add( eq > 0 ? a.substring( 0, eq + 1 ) + Secrets.show( a.substring( 0, eq ), a.substring( eq + 1 ) ) : Secrets.text( a ) );
		}
		m.put( "jvmArgs", args );
		m.put( "env", pairs( new TreeMap<>( System.getenv() ) ) );
		Map<String, String> props = new TreeMap<>();
		System.getProperties().forEach( ( k, v ) -> props.put( String.valueOf( k ), String.valueOf( v ) ) );
		m.put( "properties", pairs( props ) );
		Map<String, Object> info = new LinkedHashMap<>();
		info.put( "lens", service.getVersion() );
		info.put( "java", System.getProperty( "java.version" ) );
		info.put( "os", System.getProperty( "os.name" ) + " " + System.getProperty( "os.version" ) + " " + System.getProperty( "os.arch" ) );
		info.put( "home", String.valueOf( rt.getRuntimeHome() ) );
		info.put( "cwd", System.getProperty( "user.dir" ) );
		info.put( "timezone", java.util.TimeZone.getDefault().getID() );
		m.put( "info", info );
		return m;
	}

	/**
	 * Every loaded module with what it provides, for the Modules page. Nested modules (inside another module's modules folder) show their parent.
	 */
	public Map<String, Object> modules() {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for ( Map.Entry<Key, ModuleRecord> e : BoxRuntime.getInstance().getModuleService().getRegistry().entrySet() ) {
				ModuleRecord		r	= e.getValue();
				Map<String, Object>	m	= new LinkedHashMap<>();
				m.put( "name", e.getKey().getName() );
				m.put( "version", r.version );
				m.put( "author", r.author );
				m.put( "description", r.description );
				m.put( "webURL", ortus.boxlang.modules.bxlens.util.Text.webUrl( r.webURL ) );
				m.put( "enabled", r.enabled );
				m.put( "activated", r.activated );
				m.put( "activationMs", r.activationTime );
				m.put( "activatedOn", r.activatedOn == null ? "" : r.activatedOn.toString() );
				m.put( "bifs", names( r.bifs ) );
				m.put( "components", names( r.components ) );
				m.put( "memberMethods", names( r.memberMethods ) );
				m.put( "interceptors", names( r.interceptors ) );
				m.put( "interceptionPoints", names( r.customInterceptionPoints ) );
				m.put( "dependencies", names( r.dependencies ) );
				m.put( "nested", names( r.nestedModules ) );
				m.put( "parent", r.parentModule == null ? "" : r.parentModule.getName() );
				m.put( "path", r.physicalPath == null ? "" : r.physicalPath.toString() );
				m.put( "publicMapping", r.publicMapping == null ? "" : String.valueOf( r.publicMapping.name() ) );
				m.put( "self", ortus.boxlang.modules.bxlens.util.Keys.moduleName.equals( e.getKey() ) );
				out.add( m );
			}
		} catch ( Throwable t ) {
			// Keep what we have
		}
		out.sort( Comparator.comparing( x -> String.valueOf( x.get( "name" ) ).toLowerCase() ) );
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "modules", out );
		return m;
	}

	private static List<String> names( Collection<?> c ) {
		List<String> out = new ArrayList<>();
		if ( c != null ) {
			for ( Object o : c ) {
				out.add( String.valueOf( o instanceof Key k ? k.getName() : o ) );
			}
		}
		return out;
	}

	private List<Map<String, Object>> pairs( Map<String, String> source ) {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map.Entry<String, String> e : source.entrySet() ) {
			Map<String, Object> x = new LinkedHashMap<>();
			x.put( "name", e.getKey() );
			x.put( "value", Secrets.show( e.getKey(), e.getValue() ) );
			x.put( "hidden", Secrets.isSecretName( e.getKey() ) && !e.getValue().isEmpty() );
			out.add( x );
		}
		return out;
	}

	private Object walk( Object v, String name, int depth ) {
		return RuntimeInfo.walk( v, name, depth );
	}

	/**
	 * A zip with what a support engineer needs: a thread dump, the environment, executors, tasks, datasources, caches and the effective
	 * settings. Secrets are hidden. No request data and no log files.
	 */
	public byte[] bundle() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try ( ZipOutputStream zip = new ZipOutputStream( bytes ) ) {
			add( zip, "README.txt", "BX Lens diagnostic bundle\nCreated " + Instant.now() + "\nBX Lens " + service.getVersion()
			    + "\nSecrets are hidden. No request data and no log files are included.\n" );
			add( zip, "thread-dump.txt", service.getData().threadDump() );
			add( zip, "environment.json", Json.write( environment() ) );
			add( zip, "system.json", Json.write( service.getData().system() ) );
			add( zip, "executors.json", Json.write( service.getData().executors() ) );
			add( zip, "tasks.json", Json.write( service.getData().tasks() ) );
			add( zip, "datasources.json", Json.write( service.getDatasources().list() ) );
			add( zip, "caches.json", Json.write( service.getCaches().list() ) );
			add( zip, "lens-settings.json", Json.write( service.settingsView() ) );
		}
		return bytes.toByteArray();
	}

	private static void add( ZipOutputStream zip, String name, String content ) throws IOException {
		zip.putNextEntry( new ZipEntry( name ) );
		zip.write( content.getBytes( StandardCharsets.UTF_8 ) );
		zip.closeEntry();
	}

}
