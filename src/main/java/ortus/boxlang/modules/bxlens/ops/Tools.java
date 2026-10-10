/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalog of what the ops agent may do. One entry per tool: its name, whether it needs the admin role, whether it changes something
 * (an ACT tool) and the arguments it takes. The model never decides what is allowed: {@link Toolbox} checks every call against this catalog,
 * the role of the signed in user and the console settings, whatever tool the model asks for and with whatever arguments.
 * <p>
 * The text the model reads (what each tool does) lives in <code>models/ops/LensTools.bx</code>, next to the <code>@AITool</code> annotation. A
 * test keeps that file and this catalog in step.
 */
public final class Tools {

	/**
	 * One argument of a tool.
	 *
	 * @param name     the argument name, as the model sends it
	 * @param type     string, int or bool
	 * @param required must be given
	 * @param min      smallest value of an int
	 * @param max      largest value of an int, or the longest string
	 * @param allowed  the only values a string may have, empty for any
	 * @param def      the value used when it is not given
	 */
	public record Param( String name, String type, boolean required, int min, int max, List<String> allowed, Object def ) {
	}

	/**
	 * One tool.
	 *
	 * @param name    the tool name
	 * @param admin   only the admin role may use it
	 * @param act     it changes something: it needs an approval, Plus, console.actions, ai.actions and a console that is not read only
	 * @param feature the Plus feature that must also be on, or an empty string
	 * @param group   where it is listed in the docs
	 * @param params  its arguments
	 */
	public record Tool( String name, boolean admin, boolean act, String feature, String group, List<Param> params ) {

		public Param param( String n ) {
			for ( Param p : params ) {
				if ( p.name().equals( n ) ) {
					return p;
				}
			}
			return null;
		}
	}

	private static final List<Tool>			ALL	= new ArrayList<>();
	private static final Map<String, Tool>	BY	= new LinkedHashMap<>();

	static {
		// LOOK: what the console shows, to the roles the console shows it to
		look( "overview", false, "Inspect" );
		look( "requests", false, "Inspect", num( "limit", 1, 50, 15 ), flag( "problemsOnly", false ), text( "urlContains", 100 ) );
		look( "requestDetail", false, "Inspect", req( text( "id", 40 ) ) );
		look( "inFlight", false, "Inspect" );
		look( "errors", false, "Inspect", num( "limit", 1, 30, 10 ), text( "id", 40 ) );
		look( "queryStats", false, "Inspect", num( "limit", 1, 30, 10 ), oneOf( "sort", "slowest", "slowest", "total", "count", "failures" ) );
		look( "executors", false, "Runtime", flag( "onlyProblems", false ) );
		look( "tasks", false, "Runtime" );
		look( "datasources", false, "Runtime" );
		look( "caches", false, "Runtime" );
		look( "modules", false, "Runtime" );
		look( "configuration", false, "Runtime", text( "filter", 60 ) );
		look( "lensInfo", false, "Runtime" );
		look( "searchDocs", false, "Help", req( text( "question", 300 ) ) );
		// JVM introspection: what the console shows only to the admin
		look( "system", true, "JVM" );
		look( "threadsSummary", true, "JVM" );
		look( "blockedThreads", true, "JVM" );
		look( "topCpuThreads", true, "JVM", num( "limit", 1, 30, 10 ) );
		look( "threadPools", true, "JVM" );
		look( "threadStack", true, "JVM", req( text( "name", 120 ) ) );
		look( "gcPressure", true, "JVM" );
		look( "diagnose", false, "JVM" );
		look( "logs", true, "Logs and environment", text( "file", 120 ), text( "query", 120 ), num( "lines", 1, 300, 100 ), text( "level", 10 ) );
		look( "environment", true, "Logs and environment" );
		// Database metadata only. No tool takes SQL text
		look( "dbTables", true, "Database metadata", req( text( "datasource", 80 ) ), text( "filter", 60 ) );
		look( "dbColumns", true, "Database metadata", req( text( "datasource", 80 ) ), req( text( "table", 120 ) ) );
		look( "dbTest", true, "Database metadata", req( text( "datasource", 80 ) ) );
		// ACT: each one waits for a click on Approve
		act( "taskAction", "taskActions", req( text( "scheduler", 80 ) ), text( "task", 120 ),
		    req( oneOf( "action", "", "run", "pause", "resume", "reload" ) ) );
		act( "cacheAction", "cacheActions", req( text( "cache", 80 ) ), req( oneOf( "action", "", "evict", "reap", "clear" ) ), text( "key", 200 ) );
		act( "runGc", "" );
		act( "setIntegration", "", req( text( "id", 40 ) ), req( flag( "enabled", false ) ) );
		act( "changeSetting", "", req( text( "key", 80 ) ), req( text( "value", 200 ) ) );
	}

	private Tools() {
	}

	private static void look( String name, boolean admin, String group, Param... params ) {
		add( new Tool( name, admin, false, "", group, List.of( params ) ) );
	}

	private static void act( String name, String feature, Param... params ) {
		add( new Tool( name, true, true, feature, "Act (needs approval)", List.of( params ) ) );
	}

	private static void add( Tool t ) {
		ALL.add( t );
		BY.put( t.name(), t );
	}

	private static Param req( Param p ) {
		return new Param( p.name(), p.type(), true, p.min(), p.max(), p.allowed(), p.def() );
	}

	private static Param num( String name, int min, int max, int def ) {
		return new Param( name, "int", false, min, max, List.of(), def );
	}

	private static Param text( String name, int maxLen ) {
		return new Param( name, "string", false, 0, maxLen, List.of(), "" );
	}

	private static Param flag( String name, boolean def ) {
		return new Param( name, "bool", false, 0, 0, List.of(), def );
	}

	private static Param oneOf( String name, String def, String... values ) {
		return new Param( name, "string", false, 0, 40, List.of( values ), def );
	}

	/**
	 * Every tool, in the order the docs list them.
	 */
	public static List<Tool> all() {
		return List.copyOf( ALL );
	}

	/**
	 * A tool by name, or null.
	 */
	public static Tool get( String name ) {
		return name == null ? null : BY.get( name );
	}

}
