/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.Integrations;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.SettingsRegistry;
import ortus.boxlang.modules.bxlens.util.Secrets;

/**
 * The tools that change something. They are the same actions the console offers an admin, run only after a person has approved this
 * exact action in the chat. There is no restart, no shutdown, no heap dump and no file access here, and no setting that controls the agent
 * itself can be changed by it.
 */
final class Act {

	private final LensService service;

	Act( LensService service ) {
		this.service = service;
	}

	/**
	 * What the approval card says, in plain words, worked out from the validated arguments and not from anything the model wrote.
	 */
	String describe( String tool, Map<String, Object> a ) {
		return switch ( tool ) {
			case "taskAction" -> {
				String	action	= String.valueOf( a.get( "action" ) );
				String	task	= String.valueOf( a.get( "task" ) );
				yield ( "reload".equals( action ) ? "Reload the scheduler " + a.get( "scheduler" ) + " from its tasks file"
				    : capital( action ) + " the task " + a.get( "scheduler" ) + "/" + task ) + " (action " + action + ")";
			}
			case "cacheAction" -> capital( String.valueOf( a.get( "action" ) ) ) + " on the cache " + a.get( "cache" )
			    + ( String.valueOf( a.get( "key" ) ).isEmpty() ? ( "clear".equals( a.get( "action" ) ) ? ": every object in it is removed" : "" )
			        : ", key " + a.get( "key" ) );
			case "runGc" -> "Run a garbage collection now (System.gc). It can pause the application for a moment";
			case "setIntegration" -> ( Boolean.TRUE.equals( a.get( "enabled" ) ) ? "Turn on" : "Turn off" ) + " the integration " + a.get( "id" );
			case "changeSetting" -> "Change the Lens setting " + a.get( "key" ) + " to " + Secrets.text( String.valueOf( a.get( "value" ) ) );
			default -> tool;
		};
	}

	Map<String, Object> run( String tool, Map<String, Object> a ) {
		switch ( tool ) {
			case "taskAction" : {
				String	action		= String.valueOf( a.get( "action" ) );
				String	scheduler	= String.valueOf( a.get( "scheduler" ) );
				String	task		= String.valueOf( a.get( "task" ) );
				if ( "reload".equals( action ) ) {
					return this.service.getData().taskAction( action, scheduler, null );
				}
				if ( task.isEmpty() ) {
					throw new IllegalArgumentException( "The argument [task] is required for " + action + "." );
				}
				return this.service.getData().taskAction( action, scheduler, task );
			}
			case "cacheAction" : {
				String				key	= String.valueOf( a.get( "key" ) );
				Map<String, Object>	r	= this.service.getCaches().action( String.valueOf( a.get( "cache" ) ), String.valueOf( a.get( "action" ) ),
				    key.isEmpty() ? null : key );
				if ( r == null ) {
					throw new IllegalArgumentException( "Unknown cache. Use caches to list them." );
				}
				return r;
			}
			case "runGc" : {
				long	before	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
				long	t0		= System.nanoTime();
				System.gc();
				long				after	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
				Map<String, Object>	g		= new LinkedHashMap<>();
				g.put( "ok", true );
				g.put( "freedBytes", Math.max( 0, before - after ) );
				g.put( "ms", ( System.nanoTime() - t0 ) / 1_000_000L );
				g.put( "message", "Garbage collection ran" );
				return g;
			}
			case "setIntegration" : {
				try {
					this.service.setIntegration( String.valueOf( a.get( "id" ) ), Boolean.TRUE.equals( a.get( "enabled" ) ) );
				} catch ( java.io.IOException e ) {
					throw new IllegalStateException( "Could not save the setting" );
				}
				return Map.of( "ok", true, "message",
				    "Integration " + a.get( "id" ) + " is now " + ( Boolean.TRUE.equals( a.get( "enabled" ) ) ? "on" : "off" ) );
			}
			case "changeSetting" : {
				String key = String.valueOf( a.get( "key" ) );
				if ( blocked( key ) ) {
					throw new IllegalArgumentException( "The agent may not change " + key + "." );
				}
				SettingsRegistry.Def d = this.service.getSettingsRegistry().get( key );
				if ( d == null || !d.live() ) {
					throw new IllegalArgumentException( key + " is not a setting that can be changed live." );
				}
				Object value = this.service.getSettingsRegistry().coerce( key, a.get( "value" ) );
				try {
					this.service.changeSettings( Map.of( d.key(), value ) );
				} catch ( java.io.IOException e ) {
					throw new IllegalStateException( "Could not save the setting" );
				}
				return Map.of( "ok", true, "message", d.key() + " is now " + value );
			}
			default :
				throw new IllegalArgumentException( "Unknown action" );
		}
	}

	/**
	 * Settings the agent may never change: its own (a changed address or key would send data elsewhere), and the ones that decide who can
	 * see Lens.
	 */
	static boolean blocked( String key ) {
		String k = key.toLowerCase( Locale.ROOT );
		return k.startsWith( "ai." ) || k.startsWith( "console." ) || k.startsWith( "access." ) || k.startsWith( "bar.access" ) || k.startsWith( "store." );
	}

	/** The setting names a test or a doc may list as changeable by the agent. */
	static List<String> blockedPrefixes() {
		return List.of( "ai.", "console.", "access.", "bar.access", "store." );
	}

	private static String capital( String s ) {
		return s.isEmpty() ? s : Character.toUpperCase( s.charAt( 0 ) ) + s.substring( 1 );
	}

}
