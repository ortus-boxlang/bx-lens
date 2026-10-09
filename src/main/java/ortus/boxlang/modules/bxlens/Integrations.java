/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.modules.ModuleRecord;
import ortus.boxlang.runtime.scopes.Key;

/**
 * The module integrations Lens knows about. An integration listens to the events of another module (for example bx-orm) and shows what it
 * reports. The rules are the same for all of them:
 * <ul>
 * <li>Opt in. The setting <code>collectors.{id}.enabled</code> is false until an admin turns it on.</li>
 * <li>Needs its module. Nothing is registered, and the console offers no switch, while the module is not installed.</li>
 * <li>Events only. Lens listens to events the module announces and never reaches into its classes. The one exception is a documented service
 * call the module offers for tools (read reflectively, because the module lives in another class loader).</li>
 * </ul>
 * The status of an integration is <code>notInstalled</code>, <code>available</code> (installed, switched off) or <code>on</code>. To add one,
 * see <code>docs/reference/integrations.md</code>.
 */
public final class Integrations {

	/**
	 * Describes one integration.
	 *
	 * @param id          short id, also the collector id and the middle part of the setting <code>collectors.{id}.enabled</code>
	 * @param name        display name
	 * @param module      the name the module registers under (the <code>moduleName</code> of its box.json), as the Modules page shows it
	 * @param install     what to install, for the hint shown when the module is missing
	 * @param description what Lens does with it
	 */
	public record Def( String id, String name, String module, String install, String description ) {

		/** The setting that switches the integration on. */
		public String settingKey() {
			return "collectors." + this.id + ".enabled";
		}
	}

	/** Installed and listening. */
	public static final String		ON				= "on";
	/** Installed, switched off. */
	public static final String		AVAILABLE		= "available";
	/** Its module is not there. */
	public static final String		NOT_INSTALLED	= "notInstalled";

	/** Every integration, in the order the console lists them. */
	public static final List<Def>	ALL				= List.of(
	    new Def( "orm", "BoxLang ORM", "orm", "bx-orm",
	        "The SQL, flushes and failures of ORM entities, from the onORMQuery, onORMFlush and onORMException events." ) );

	private final Predicate<String>	installed;

	/** Looks modules up in the runtime. */
	public Integrations() {
		this( Integrations::moduleInstalled );
	}

	/**
	 * @param installed tells whether a module name is installed (tests pass a fake)
	 */
	public Integrations( Predicate<String> installed ) {
		this.installed = installed;
	}

	/** The integration with an id, or null. */
	public static Def get( String id ) {
		if ( id != null ) {
			for ( Def d : ALL ) {
				if ( d.id().equalsIgnoreCase( id ) ) {
					return d;
				}
			}
		}
		return null;
	}

	/** Is the module of the integration installed? */
	public boolean isInstalled( Def d ) {
		try {
			return this.installed.test( d.module() );
		} catch ( Throwable t ) {
			return false;
		}
	}

	/** The status of an integration with the effective settings. */
	public String status( Def d, LensConfig cfg ) {
		return status( isInstalled( d ), cfg.isCollectorEnabled( d.id(), false ) );
	}

	/** The status from its two inputs. */
	public static String status( boolean moduleInstalled, boolean settingOn ) {
		if ( !moduleInstalled ) {
			return NOT_INSTALLED;
		}
		return settingOn ? ON : AVAILABLE;
	}

	/** Should the listeners of this integration be registered now? */
	public boolean on( String id, LensConfig cfg ) {
		Def d = get( id );
		return d != null && ON.equals( status( d, cfg ) );
	}

	/** The status of one integration as the console shows it. */
	public Map<String, Object> describe( Def d, LensConfig cfg ) {
		Map<String, Object>	m		= new LinkedHashMap<>();
		String				status	= status( d, cfg );
		m.put( "id", d.id() );
		m.put( "name", d.name() );
		m.put( "module", d.module() );
		m.put( "description", d.description() );
		m.put( "setting", d.settingKey() );
		m.put( "status", status );
		m.put( "installed", !NOT_INSTALLED.equals( status ) );
		m.put( "enabled", ON.equals( status ) );
		m.put( "hint", NOT_INSTALLED.equals( status ) ? "Install " + d.install() + " to enable" : "" );
		return m;
	}

	/** All integrations with their status. */
	public List<Map<String, Object>> list( LensConfig cfg ) {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Def d : ALL ) {
			out.add( describe( d, cfg ) );
		}
		return out;
	}

	private static boolean moduleInstalled( String module ) {
		try {
			ModuleRecord r = BoxRuntime.getInstance().getModuleService().getRegistry().get( Key.of( module ) );
			return r != null && r.enabled && r.isActivated();
		} catch ( Throwable t ) {
			return false;
		}
	}

}
