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
import java.util.function.Supplier;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Hibernate statistics of the bx-orm applications, for the ORM page. bx-orm offers them to tools through its own service:
 * <code>ORMService.getStatistics( appName )</code> and <code>ORMService.setStatisticsEnabled( appName, enabled )</code>. Lens calls those two by
 * reflection, because bx-orm lives in another class loader and Lens has no dependency on it. Lens never reads Hibernate itself.
 * <p>
 * The SQL, the flush counts and the failures come from the ORM events instead (see {@link OrmTotals}).
 */
public final class OrmData {

	/** Hibernate statistics are not collected unless the application sets <code>generateStatistics</code>, or an admin switches them on. */
	public static final String		OFF_MESSAGE	= "statistics are off in this app: set generateStatistics in ormSettings, or turn them on here";

	private final Supplier<Object>	service;

	/** Finds the ORM service in the runtime. */
	public OrmData() {
		this( OrmData::runtimeService );
	}

	/**
	 * @param service gives the ORM service object, or null when bx-orm is not there (tests pass a fake)
	 */
	public OrmData( Supplier<Object> service ) {
		this.service = service;
	}

	private static Object runtimeService() {
		try {
			return BoxRuntime.getInstance().getGlobalService( Key.of( "ORMService" ) );
		} catch ( Throwable t ) {
			return null;
		}
	}

	/**
	 * The statistics of every ORM application that started: <code>apps</code> is a list of <code>{ app, enabled, datasources: [ { name,
	 * enabled, ...counters } ] }</code>. <code>supported</code> is false when the installed bx-orm has no statistics API (before 1.7.2).
	 */
	public Map<String, Object> statistics() {
		Map<String, Object>	out		= new LinkedHashMap<>();
		List<Object>		apps	= new ArrayList<>();
		out.put( "apps", apps );
		Object svc = this.service.get();
		out.put( "serviceFound", svc != null );
		out.put( "offMessage", OFF_MESSAGE );
		out.put( "supported", svc != null && has( svc, "getStatistics", Key.class ) );
		if ( svc == null || !Boolean.TRUE.equals( out.get( "supported" ) ) ) {
			return out;
		}
		for ( String name : appNames( svc ) ) {
			Map<String, Object> app = new LinkedHashMap<>();
			app.put( "app", name );
			List<Object>	datasources	= new ArrayList<>();
			boolean			any			= false;
			try {
				Object raw = svc.getClass().getMethod( "getStatistics", Key.class ).invoke( svc, Key.of( name ) );
				if ( plain( raw, 0 ) instanceof Map<?, ?> stats && stats.get( "datasources" ) instanceof Map<?, ?> byName ) {
					for ( Map.Entry<?, ?> e : byName.entrySet() ) {
						Map<String, Object> ds = new LinkedHashMap<>();
						ds.put( "name", String.valueOf( e.getKey() ) );
						if ( e.getValue() instanceof Map<?, ?> counters ) {
							for ( Map.Entry<?, ?> c : counters.entrySet() ) {
								ds.put( String.valueOf( c.getKey() ), c.getValue() );
							}
						}
						any = any || Boolean.TRUE.equals( ds.get( "enabled" ) );
						datasources.add( ds );
					}
				}
			} catch ( Throwable t ) {
				app.put( "error", String.valueOf( cause( t ).getMessage() ) );
			}
			app.put( "enabled", any );
			app.put( "datasources", datasources );
			apps.add( app );
		}
		return out;
	}

	/**
	 * Switch the statistics of an ORM application on or off, through <code>ORMService.setStatisticsEnabled</code>.
	 *
	 * @throws IllegalArgumentException when there is no such ORM application
	 * @throws IllegalStateException    when bx-orm is missing or too old to offer the call
	 */
	public void setStatistics( String app, boolean enabled ) {
		Object svc = this.service.get();
		if ( svc == null || !has( svc, "setStatisticsEnabled", Key.class, boolean.class ) ) {
			throw new IllegalStateException( "This bx-orm cannot switch statistics. Update bx-orm to 1.7.2 or later." );
		}
		if ( app == null || !appNames( svc ).contains( app ) ) {
			throw new IllegalArgumentException( "No ORM application named [" + app + "]" );
		}
		try {
			svc.getClass().getMethod( "setStatisticsEnabled", Key.class, boolean.class ).invoke( svc, Key.of( app ), enabled );
		} catch ( Throwable t ) {
			throw new IllegalStateException( String.valueOf( cause( t ).getMessage() ) );
		}
	}

	/** Is bx-orm there and does it offer statistics? */
	public boolean canSwitchStatistics() {
		Object svc = this.service.get();
		return svc != null && has( svc, "setStatisticsEnabled", Key.class, boolean.class );
	}

	@SuppressWarnings( "unchecked" )
	private List<String> appNames( Object svc ) {
		try {
			Object names = svc.getClass().getMethod( "getORMAppNames" ).invoke( svc );
			return names instanceof List<?> l ? new ArrayList<>( ( List<String> ) l ) : List.of();
		} catch ( Throwable t ) {
			return List.of();
		}
	}

	private static boolean has( Object svc, String method, Class<?>... types ) {
		try {
			svc.getClass().getMethod( method, types );
			return true;
		} catch ( NoSuchMethodException e ) {
			return false;
		}
	}

	private static Throwable cause( Throwable t ) {
		return t instanceof java.lang.reflect.InvocationTargetException e && e.getCause() != null ? e.getCause() : t;
	}

	/** Struct keys become names, so the JSON writer gets plain maps, lists and scalars. Nesting is capped. */
	private static Object plain( Object o, int depth ) {
		if ( depth > 6 ) {
			return String.valueOf( o );
		}
		if ( o instanceof Map<?, ?> m ) {
			Map<String, Object> out = new LinkedHashMap<>();
			for ( Map.Entry<?, ?> e : m.entrySet() ) {
				Object k = e.getKey();
				out.put( k instanceof Key key ? key.getName() : String.valueOf( k ), plain( e.getValue(), depth + 1 ) );
			}
			return out;
		}
		if ( o instanceof List<?> l ) {
			List<Object> out = new ArrayList<>();
			for ( Object x : l ) {
				out.add( plain( x, depth + 1 ) );
			}
			return out;
		}
		return o == null || o instanceof Number || o instanceof Boolean || o instanceof String ? o : String.valueOf( o );
	}

}
