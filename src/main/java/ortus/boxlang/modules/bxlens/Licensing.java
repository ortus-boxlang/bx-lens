/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.dynamic.casters.BooleanCaster;
import ortus.boxlang.runtime.interop.DynamicInteropService;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.IService;
import ortus.boxlang.runtime.types.DateTime;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Detects the BoxLang+ license through the bx-plus module, the same way bx-redis and bx-pdf do: ask the global
 * <code>BoxlangLicenseService</code> whether the license is valid and whether it is a trial, and read the expiry from the
 * <code>BoxlangLicenseInfo</code> function. Modules have isolated class loaders, so nothing from bx-plus is imported.
 * <p>
 * The answer is cached for a few minutes. Missing, invalid or failing license checks never break Lens: they report the free state.
 */
public final class Licensing {

	public static final String				PLANS_URL	= "https://boxlang.io/plans";

	private static final long				CACHE_MS	= 5 * 60_000L;
	private static final Key				IS_VALID	= Key.of( "isValidLicense" );
	private static final Key				IS_TRIAL	= Key.of( "isTrialMode" );
	private static final Key				INFO		= Key.of( "BoxlangLicenseInfo" );

	private final Supplier<IService>		serviceSupplier;
	private final Supplier<IStruct>			infoSupplier;
	private final String					override;
	private volatile Map<String, Object>	cached;
	private volatile long					cachedAt;

	/**
	 * @param override a developer override: <code>trial</code>, <code>plus</code>, <code>expired</code> or <code>none</code>. Empty means detect.
	 */
	public Licensing( String override ) {
		this( () -> BoxRuntime.getInstance().getGlobalService( "BoxlangLicenseService" ), () -> {
			BoxRuntime runtime = BoxRuntime.getInstance();
			return ( IStruct ) runtime.getFunctionService().getGlobalFunction( INFO ).invoke( runtime.getRuntimeContext(), new Object[] {}, false, INFO );
		}, override );
	}

	/**
	 * For tests: supply the license service and the license info directly.
	 */
	public Licensing( Supplier<IService> serviceSupplier, Supplier<IStruct> infoSupplier, String override ) {
		this.serviceSupplier	= serviceSupplier;
		this.infoSupplier		= infoSupplier;
		this.override			= override == null ? "" : override.trim().toLowerCase();
	}

	/**
	 * The license state: <code>state</code> is plus, trial, expired or none, with a label, an optional note, days left for a trial and the plans link.
	 */
	public Map<String, Object> status() {
		long				now	= System.currentTimeMillis();
		Map<String, Object>	c	= cached;
		if ( c != null && now - cachedAt < CACHE_MS ) {
			return c;
		}
		c			= compute();
		cachedAt	= now;
		cached		= c;
		return c;
	}

	/**
	 * The features that need BoxLang+ or a trial. Everything else is open to everyone.
	 * <ul>
	 * <li><code>diskStore</code>: errors and reports saved to disk, lifetime totals</li>
	 * <li><code>fullHistory</code>: more than {@link LensService#FREE_HISTORY} requests in memory</li>
	 * <li><code>ai</code>: Explain with AI and Ask Lens through bx-ai</li>
	 * <li><code>cost</code>: request cost (CPU, memory) and the slow request sample</li>
	 * <li><code>taskActions</code>: run, pause, resume and reload scheduled tasks</li>
	 * <li><code>cacheActions</code>: read a cache value, evict, reap, clear</li>
	 * <li><code>logDownload</code>, <code>bundle</code>, <code>heapDump</code>, <code>barDesigner</code> (saving a layout)</li>
	 * </ul>
	 */
	public static final java.util.List<String> PLUS_FEATURES = java.util.List.of( "diskStore", "fullHistory", "ai", "cost", "taskActions", "cacheActions",
	    "logDownload", "bundle", "heapDump", "barDesigner", "ormStats" );

	/**
	 * Is a feature available? Plus and a trial get everything. Free keeps the bar and the live console, minus the features in {@link #PLUS_FEATURES}.
	 */
	public boolean has( String feature ) {
		if ( PLUS_FEATURES.contains( feature ) ) {
			String state = String.valueOf( status().get( "state" ) );
			return "plus".equals( state ) || "trial".equals( state );
		}
		return true;
	}

	/**
	 * Every gated feature with whether it is available now, for the UI.
	 */
	public Map<String, Object> features() {
		Map<String, Object> m = new LinkedHashMap<>();
		for ( String f : PLUS_FEATURES ) {
			m.put( f, has( f ) );
		}
		return m;
	}

	private Map<String, Object> compute() {
		if ( !override.isEmpty() ) {
			return build( override, override.equals( "trial" ) ? 41L : null, "Developer override" );
		}
		IService service;
		try {
			service = serviceSupplier.get();
		} catch ( Throwable t ) {
			service = null;
		}
		if ( service == null ) {
			return build( "none", null, "The bx-plus module is not installed" );
		}
		try {
			boolean	valid	= BooleanCaster.cast( ask( service, IS_VALID ) );
			boolean	trial	= BooleanCaster.cast( ask( service, IS_TRIAL ) );
			Long	days	= null;
			String	note	= "";
			try {
				IStruct info = infoSupplier.get();
				if ( info != null ) {
					Object exp = info.get( Key.expires );
					if ( exp instanceof DateTime d ) {
						days = Duration.between( ZonedDateTime.now(), d.getWrapped() ).toDays();
					}
					Object msg = info.get( Key.message );
					note = msg == null ? "" : msg.toString();
				}
			} catch ( Throwable t ) {
				// Keep what we know
			}
			if ( !valid ) {
				return build( "expired", null, note.isBlank() ? "The license is not valid" : note );
			}
			return build( trial ? "trial" : "plus", trial ? days : null, note );
		} catch ( Throwable t ) {
			return build( "none", null, "The license check failed: " + t.getMessage() );
		}
	}

	private Object ask( IService service, Key method ) {
		BoxRuntime runtime = BoxRuntime.getInstance();
		return DynamicInteropService.dereferenceAndInvoke( null, service, runtime.getRuntimeContext(), method, new Object[] {}, false );
	}

	private Map<String, Object> build( String state, Long daysLeft, String note ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "state", state );
		m.put( "label", switch ( state ) {
			case "plus" -> "BoxLang+ active";
			case "trial" -> daysLeft != null ? "Trial · " + Math.max( 0, daysLeft ) + " days left" : "Trial";
			case "expired" -> "License expired";
			default -> "Free";
		} );
		m.put( "daysLeft", daysLeft );
		m.put( "note", note );
		m.put( "plansUrl", PLANS_URL );
		return m;
	}

}
