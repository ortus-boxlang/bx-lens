/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.bxlens;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ortus.boxlang.modules.bxlens.ext.CollectHandle;
import ortus.boxlang.modules.bxlens.ext.LensRegistry;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.interceptors.ILensCollector;
import ortus.boxlang.modules.bxlens.interceptors.TaskOutcomes;
import ortus.boxlang.modules.bxlens.interceptors.collectors.CacheCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ExceptionCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.FunctionCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.HttpCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.JvmCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.LifecycleCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.LogCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ModulesCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.QueryCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.ScopesCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.TemplateCollector;
import ortus.boxlang.modules.bxlens.interceptors.collectors.TransactionCollector;
import ortus.boxlang.modules.bxlens.model.IssueEngine;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Snapshot;
import ortus.boxlang.modules.bxlens.render.BarRenderer;
import ortus.boxlang.modules.bxlens.store.RequestStore;
import ortus.boxlang.modules.bxlens.util.GlobalStats;
import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.web.WebExchange;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Owns everything global to bx-lens: the parsed settings, the collectors, the in-memory request history and the renderer.
 * One instance lives in the module class loader. {@link #getInstance()} never returns null, so BIFs and collectors can always call it;
 * before activation (or when Lens is disabled) it simply tracks nothing.
 */
public final class LensService {

	/**
	 * What ended the request.
	 */
	public enum Trigger {
		END,
		ERROR,
		ABORT
	}

	/**
	 * Placeholder written by lensRender() and replaced with the bar when the request ends.
	 */
	public static final String										MARKER				= "<!--bxlens:here-->";

	private static volatile LensService								instance;

	private volatile BoxRuntime										runtime;
	private volatile LensConfig										config				= LensConfig.defaults();
	private volatile AccessGuard									barGuard			= new AccessGuard( config, "bar.access", false );
	private volatile AccessGuard									consoleGuard		= new AccessGuard( config, "console.access", true );
	private volatile ConsoleAuth									auth				= new ConsoleAuth( config );
	private volatile TaskOutcomes									outcomes			= new TaskOutcomes();
	private volatile Licensing										licensing			= new Licensing( "" );
	private volatile LayoutStore									layout				= new LayoutStore( null );
	private volatile Map<String, Object>							baseSettings		= Map.of();
	private volatile LensConfig										baseConfig			= LensConfig.defaults();
	private volatile SettingsRegistry								settingsRegistry	= new SettingsRegistry( List.of() );
	private volatile SettingsStore									settingsStore		= new SettingsStore( null, settingsRegistry );
	private final List<ILensCollector>								allBuiltIns			= new ArrayList<>();
	private final ConsoleData										consoleData			= new ConsoleData( this );
	private final AtomicInteger										streams				= new AtomicInteger();
	private final Map<String, LensRequest>							active				= new java.util.concurrent.ConcurrentHashMap<>();
	private volatile java.util.concurrent.ScheduledExecutorService	watchdog;
	private volatile RequestStore									store				= new RequestStore( 50 );
	private volatile BarRenderer									renderer;
	private volatile String											version				= "0.0.0";
	private volatile String											moduleDir			= "";
	private final GlobalStats										stats				= new GlobalStats();
	private final LensRegistry										registry			= new LensRegistry();
	private final List<ILensCollector>								collectors			= Collections.synchronizedList( new ArrayList<>() );
	private volatile BoxLangLogger									logger;
	private volatile BoxLangLogger									auditLogger;
	private final Audit												audit				= new Audit( this );
	private volatile HeapDumper										heapDumper			= new HeapDumper();
	private final DatasourceData									datasources			= new DatasourceData();
	private final CacheData											caches				= new CacheData( this );
	private final LogData											logs				= new LogData();
	private final EnvironmentData									environment			= new EnvironmentData( this );

	private LensService() {
	}

	/**
	 * The service singleton.
	 */
	public static LensService getInstance() {
		LensService i = instance;
		if ( i == null ) {
			synchronized ( LensService.class ) {
				if ( instance == null ) {
					instance = new LensService();
				}
				i = instance;
			}
		}
		return i;
	}

	/**
	 * Activate with the module settings. Called from ModuleConfig.bx on load. Safe to call again on reload.
	 *
	 * @param runtime   the runtime
	 * @param settings  the module settings
	 * @param moduleDir physical path of the module folder
	 * @param version   module version
	 */
	public synchronized void activate( BoxRuntime runtime, Map<?, ?> settings, String moduleDir, String version ) {
		shutdown();
		this.runtime		= runtime;
		this.version		= version == null ? "" : version;
		this.moduleDir		= moduleDir == null ? "" : moduleDir;
		this.baseSettings	= LensConfig.overlay( settings, null );
		this.baseConfig		= new LensConfig( settings );
		allBuiltIns.clear();
		allBuiltIns.addAll( builtIns() );
		List<String> ids = new ArrayList<>( List.of( "executors", "tasks", "datasources", "caches", "logfiles", "environment", "system", "threads" ) );
		allBuiltIns.forEach( c -> {
			if ( !ids.contains( c.id() ) && ! ( c instanceof LifecycleCollector ) ) {
				ids.add( c.id() );
			}
		} );
		this.settingsRegistry = new SettingsRegistry( ids );
		Path overridesFile = null;
		try {
			String custom = this.baseConfig.getString( "console.overridesFile", "" );
			overridesFile = custom.isBlank() ? runtime.getRuntimeHome().resolve( "config" ).resolve( "bxlens-settings.json" ) : Path.of( custom );
		} catch ( Throwable t ) {
			// No home: overrides live in memory only
		}
		this.settingsStore	= new SettingsStore( overridesFile, this.settingsRegistry );
		this.config			= new LensConfig( LensConfig.overlay( settings, this.settingsStore.get() ) );
		if ( this.settingsStore.skipped() > 0 ) {
			getLogger().warn( "bx-lens: ignored {} invalid entries in the settings overrides file [{}]", this.settingsStore.skipped(), overridesFile );
		}
		this.barGuard		= new AccessGuard( this.config, "bar.access", this.config.getBool( "bar.allowAllIPs", false ) );
		this.consoleGuard	= new AccessGuard( this.config, "console.access", true );
		this.auth			= new ConsoleAuth( this.config );
		this.outcomes		= new TaskOutcomes();
		this.licensing		= new Licensing( this.config.getString( "dev.license", "" ) );
		Path layoutFile = null;
		try {
			layoutFile = runtime.getRuntimeHome().resolve( "config" ).resolve( "bxlens-layout.json" );
		} catch ( Throwable t ) {
			// No home: the layout lives in memory only
		}
		this.layout = new LayoutStore( layoutFile );
		if ( this.config.consoleEnabled ) {
			runtime.getInterceptorService().register( this.outcomes );
		}
		this.store		= new RequestStore( this.config.maxRequests );
		this.renderer	= new BarRenderer( Path.of( moduleDir ).resolve( "assets" ), this.config.getBool( "dev.reloadAssets", false ) );
		if ( !this.renderer.isComplete() ) {
			getLogger().warn( "bx-lens assets are missing under [{}]. The bar will not render.", moduleDir );
		}
		if ( !this.config.barEnabled && !this.config.consoleEnabled ) {
			getLogger().info( "bx-lens is installed but off. Set bar.enabled or console.enabled in the module settings to turn it on." );
		}
		if ( this.barGuard.isDowngraded() ) {
			getLogger().error(
			    "bx-lens: bar.access is [all] but bar.allowAllIPs is not true. Showing the bar to loopback only. Set bar.allowAllIPs to true to confirm you want every IP to see request internals." );
		}
		if ( this.config.consoleEnabled && this.config.consolePassword().isBlank() ) {
			getLogger().error( "bx-lens: console.enabled is true but console.password is empty or cannot be decrypted. The console stays unavailable." );
		}
		if ( this.config.consoleEnabled && this.consoleGuard.isOpenToAll() ) {
			getLogger().warn( "bx-lens: console.access is [all]. Use HTTPS and a strong password." );
		}
		reconcileCollectors();
		startWatchdog();
		announceRegister();
		getLogger().info( "bx-lens {} active: bar={}, console={}, collect={}, collectors={}", this.version, this.config.barEnabled, this.config.consoleEnabled,
		    this.config.collectLevel, collectorIds() );
	}

	/**
	 * Unregister all collectors and clear history.
	 */
	public synchronized void shutdown() {
		heapDumper.shutdown();
		heapDumper = new HeapDumper();
		if ( watchdog != null ) {
			watchdog.shutdownNow();
			watchdog = null;
		}
		active.clear();
		try {
			if ( runtime != null ) {
				runtime.getInterceptorService().unregister( outcomes );
			}
		} catch ( Throwable t ) {
			// Not registered
		}
		synchronized ( collectors ) {
			for ( ILensCollector c : collectors ) {
				try {
					if ( c instanceof BaseCollector bc && runtime != null ) {
						runtime.getInterceptorService().unregister( bc );
					}
				} catch ( Throwable t ) {
					// Already gone
				}
			}
			collectors.clear();
		}
		store.clear();
		registry.clear();
	}

	/**
	 * Register an additional collector, for example from a Java extension.
	 */
	public void register( ILensCollector collector ) {
		if ( collector instanceof BaseCollector bc && runtime != null ) {
			runtime.getInterceptorService().register( bc );
		}
		collectors.add( collector );
	}

	/**
	 * Remove a collector.
	 */
	public void unregister( ILensCollector collector ) {
		try {
			if ( collector instanceof BaseCollector bc && runtime != null ) {
				runtime.getInterceptorService().unregister( bc );
			}
		} catch ( Throwable t ) {
			// Already gone
		}
		collectors.remove( collector );
	}

	/**
	 * Make the registered collectors match the settings: add the ones now wanted, remove the ones no longer wanted.
	 */
	private void reconcileCollectors() {
		synchronized ( collectors ) {
			for ( ILensCollector c : allBuiltIns ) {
				boolean	heavyBlocked	= this.config.light && c.heavy();
				boolean	want			= c instanceof LifecycleCollector
				    || ( !heavyBlocked && this.config.isCollectorEnabled( c.id(), c.enabledByDefault() ) );
				boolean	has				= collectors.contains( c );
				if ( want && !has ) {
					register( c );
				} else if ( !want && has ) {
					unregister( c );
				}
			}
		}
	}

	public SettingsRegistry getSettingsRegistry() {
		return settingsRegistry;
	}

	public SettingsStore getSettingsStore() {
		return settingsStore;
	}

	/**
	 * Change settings from the console. <code>null</code> removes the override for that key. Everything is checked first: a bad value
	 * changes nothing.
	 *
	 * @throws IllegalArgumentException when a key or value is not valid
	 */
	public synchronized void changeSettings( Map<String, Object> changes ) throws java.io.IOException {
		Map<String, Object> next = new java.util.LinkedHashMap<>( settingsStore.get() );
		for ( Map.Entry<String, Object> e : changes.entrySet() ) {
			SettingsRegistry.Def d = settingsRegistry.get( e.getKey() );
			if ( d == null ) {
				throw new IllegalArgumentException( "Unknown setting: " + e.getKey() );
			}
			if ( e.getValue() == null ) {
				next.remove( d.key() );
			} else {
				next.put( d.key(), e.getValue() );
			}
		}
		settingsStore.set( next );
		applySettings();
	}

	/**
	 * Drop one override, or all of them when the key is null.
	 */
	public synchronized void resetSettings( String key ) throws java.io.IOException {
		Map<String, Object> next = new java.util.LinkedHashMap<>( settingsStore.get() );
		if ( key == null || key.isBlank() ) {
			next.clear();
		} else {
			SettingsRegistry.Def d = settingsRegistry.get( key );
			if ( d == null ) {
				throw new IllegalArgumentException( "Unknown setting: " + key );
			}
			next.remove( d.key() );
		}
		settingsStore.set( next );
		applySettings();
	}

	private void applySettings() {
		this.config = new LensConfig( LensConfig.overlay( baseSettings, settingsStore.get() ) );
		reconcileCollectors();
	}

	/**
	 * Every known setting with its effective value, where it came from and whether it can be changed. Secrets are never included.
	 */
	public List<Map<String, Object>> settingsView() {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( SettingsRegistry.Def d : settingsRegistry.all() ) {
			Map<String, Object>	m			= d.toMap();
			Object				value		= config.get( d.key() );
			Object				configured	= baseConfig.get( d.key() );
			if ( "secret".equals( d.type() ) ) {
				m.put( "value", value == null || value.toString().isBlank() ? "not set" : "set" );
				m.put( "configured", m.get( "value" ) );
			} else {
				if ( d.key().startsWith( "collectors." ) && d.key().endsWith( ".enabled" ) && value == null ) {
					value = config.isCollectorEnabled( d.key().split( "\\." )[ 1 ], ( Boolean ) d.def() );
				}
				if ( configured == null && d.key().startsWith( "collectors." ) && d.key().endsWith( ".enabled" ) ) {
					configured = baseConfig.isCollectorEnabled( d.key().split( "\\." )[ 1 ], ( Boolean ) d.def() );
				}
				m.put( "value", value == null ? d.def() : value );
				m.put( "configured", configured == null ? d.def() : configured );
			}
			m.put( "source", settingsStore.get().containsKey( d.key() ) ? "override" : "config" );
			out.add( m );
		}
		return out;
	}

	/**
	 * Let applications and modules declare panels. Fires the onLensRegister interception point.
	 */
	public void announceRegister() {
		if ( runtime == null ) {
			return;
		}
		try {
			registry.clear();
			runtime.getInterceptorService().announce( Keys.onLensRegister, Struct.of( "registry", registry ) );
		} catch ( Throwable t ) {
			getLogger().warn( "onLensRegister listener failed: {}", t.toString() );
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Request lifecycle
	// ---------------------------------------------------------------------------------------------

	/**
	 * Start tracking a request when Lens is enabled, the caller is allowed and the path is not excluded.
	 *
	 * @return the tracked request or null
	 */
	public LensRequest begin( RequestBoxContext rc ) {
		if ( !config.active || rc == null ) {
			return null;
		}
		LensRequest existing = rc.getAttachment( Keys.requestAttach );
		if ( existing != null ) {
			return existing;
		}
		WebExchange ex = WebExchange.of( rc );
		if ( ex == null ) {
			return null;
		}
		String uri = ex.uri();
		if ( config.isExcluded( uri ) ) {
			return null;
		}
		// The bar only shows for allowed callers. The console collects every request so production traffic is visible to it.
		boolean showBar = config.barEnabled && barGuard.isAllowed( ex.remoteAddr(), ex.host(), ex.requestHeader( barGuard.requiredHeader() ) );
		if ( !showBar && !config.consoleEnabled ) {
			return null;
		}
		LensRequest req = new LensRequest();
		req.showBar			= showBar;
		req.requestContext	= rc;
		req.method			= ex.method();
		req.url				= ex.url();
		req.uri				= uri;
		req.queryString		= ex.queryString();
		req.remoteAddr		= ex.remoteAddr();
		req.host			= ex.host();
		String ua = ex.requestHeader( "User-Agent" );
		req.userAgent	= ua == null ? "" : ua;
		req.template	= uri;
		req.thread		= Thread.currentThread();
		req.data.put( "_costStart", ortus.boxlang.modules.bxlens.util.Cost.begin() );
		active.put( req.id, req );
		rc.putAttachment( Keys.requestAttach, req );
		try {
			if ( showBar ) {
				ex.setResponseHeader( config.idHeader, req.id );
			}
		} catch ( Throwable t ) {
			// Headers already sent
		}
		synchronized ( collectors ) {
			for ( ILensCollector c : collectors ) {
				try {
					c.onRequestStart( req );
				} catch ( Throwable t ) {
					getLogger().debug( "Collector [{}] failed on request start: {}", c.id(), t.toString() );
				}
			}
		}
		announce( Keys.onLensRequestStart, Struct.of( "context", rc, "requestId", req.id ) );
		return req;
	}

	/**
	 * Finish a request: stop timing, let collectors gather final data, find issues, store it in the history and, for HTML responses, inject the bar.
	 */
	public void finish( RequestBoxContext rc, Trigger trigger ) {
		LensRequest req = rc.getAttachment( Keys.requestAttach );
		if ( req == null || !req.finished.compareAndSet( false, true ) ) {
			return;
		}
		req.endNanos = System.nanoTime();
		active.remove( req.id );
		Object costStart = req.data.remove( "_costStart" );
		if ( costStart instanceof ortus.boxlang.modules.bxlens.util.Cost.Start cs ) {
			Map<String, Object> cost = ortus.boxlang.modules.bxlens.util.Cost.since( cs );
			if ( cost != null ) {
				req.data.put( "cost", cost );
			}
		}
		req.closeAll();
		WebExchange ex = WebExchange.of( rc );
		if ( ex != null ) {
			try {
				req.status = ex.status();
				String ct = ex.responseHeader( "Content-Type" );
				req.contentType = ct == null ? "" : ct;
			} catch ( Throwable t ) {
				// Exchange recycled
			}
		}
		if ( trigger == Trigger.ERROR && req.status < 400 ) {
			req.status = 500;
		}
		req.html = config.isInjectable( req.contentType );
		try {
			req.appName = rc.getApplicationListener().getAppName().getName();
		} catch ( Throwable t ) {
			req.appName = "";
		}
		synchronized ( collectors ) {
			for ( ILensCollector c : collectors ) {
				try {
					c.onRequestFinish( req );
				} catch ( Throwable t ) {
					getLogger().debug( "Collector [{}] failed on request finish: {}", c.id(), t.toString() );
				}
			}
		}
		announce( Keys.onLensCollect, Struct.of( "context", rc, "requestId", req.id, "lens", new CollectHandle( req ) ) );
		IssueEngine.analyze( req, config );
		if ( ex != null ) {
			try {
				ortus.boxlang.modules.bxlens.model.SecurityChecks.analyze( req, config, ex.responseHeaders(), ex.responseCookies(), ex.secure() );
				Map<String, Object>							rh		= new LinkedHashMap<>();
				ortus.boxlang.modules.bxlens.util.Sanitizer	clean	= new ortus.boxlang.modules.bxlens.util.Sanitizer( config );
				ex.responseHeaders().forEach( ( k, v ) -> rh.put( k, clean.cleanKeyed( k, v ) ) );
				req.data.put( "responseHeaders", rh );
			} catch ( Throwable t ) {
				getLogger().debug( "Security checks failed: {}", t.toString() );
			}
		}
		applySlowSample( req );
		stats.recordRequest( Math.round( req.durationNs() / 1_000_000.0 ) );

		Map<String, Object>	snapshot	= Snapshot.build( req, config, ex );
		String				json		= Json.write( snapshot );
		if ( req.html || config.trackNonHtml ) {
			store.add( new RequestStore.Entry( req.id, Snapshot.summary( req ), json ) );
		}
		if ( trigger == Trigger.END && req.showBar && req.html && renderer != null && renderer.isComplete() && ex != null && !ex.responseStarted() ) {
			try {
				StringBuffer	buffer		= rc.getBuffer();
				int				markerAt	= buffer.indexOf( MARKER );
				if ( ( config.inject || markerAt >= 0 ) && req.injected.compareAndSet( false, true ) ) {
					boolean	consoleOk	= config.consoleEnabled && ex != null
					    && consoleGuard.isAllowed( ex.remoteAddr(), ex.host(), ex.requestHeader( consoleGuard.requiredHeader() ) );
					String	block		= renderer.render( pagePayload( json, consoleOk ? "/~bxlens/index.bxm" : "" ) );
					if ( markerAt >= 0 ) {
						buffer.replace( markerAt, markerAt + MARKER.length(), block );
					} else {
						BarRenderer.insert( buffer, block );
					}
				}
			} catch ( Throwable t ) {
				getLogger().warn( "bx-lens could not inject the bar: {}", t.toString() );
			}
		}
		announce( Keys.onLensRequestFinish, Struct.of( "context", rc, "requestId", req.id ) );
	}

	/**
	 * Once a request runs longer than the slow request limit, take one stack sample of its thread, so the issue can say where it was stuck.
	 */
	private int tick;

	private void startWatchdog() {
		watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor( r -> {
			Thread t = new Thread( r, "bxlens-watchdog" );
			t.setDaemon( true );
			return t;
		} );
		watchdog.scheduleWithFixedDelay( () -> {
			try {
				if ( config.consoleEnabled && ++tick % 25 == 0 ) {
					datasources.attachAll();
				}
				if ( !config.active || config.slowRequestMs <= 0 || !config.getBool( "checks.slowSample", true ) ) {
					return;
				}
				long now = System.nanoTime();
				for ( LensRequest r : active.values() ) {
					if ( !r.data.containsKey( "slowSample" ) && ( now - r.startNanos ) / 1_000_000L >= config.slowRequestMs && r.thread != null
					    && r.thread.isAlive() ) {
						List<Map<String, Object>> frames = new ArrayList<>();
						for ( StackTraceElement e : r.thread.getStackTrace() ) {
							if ( frames.size() >= 40 ) {
								break;
							}
							Map<String, Object> f = new LinkedHashMap<>();
							f.put( "text", e.toString() );
							String file = e.getFileName() == null ? "" : e.getFileName().toLowerCase();
							f.put( "bx", file.endsWith( ".bx" ) || file.endsWith( ".bxm" ) || file.endsWith( ".bxs" ) || file.endsWith( ".cfc" )
							    || file.endsWith( ".cfm" ) );
							frames.add( f );
						}
						Map<String, Object> sample = new LinkedHashMap<>();
						sample.put( "atMs", Math.round( ( now - r.startNanos ) / 1_000_000.0 ) );
						sample.put( "frames", frames );
						r.data.put( "slowSample", sample );
					}
				}
			} catch ( Throwable t ) {
				// The watchdog must never stop
			}
		}, 200, 200, java.util.concurrent.TimeUnit.MILLISECONDS );
	}

	@SuppressWarnings( "unchecked" )
	private void applySlowSample( LensRequest req ) {
		Object s = req.data.get( "slowSample" );
		if ( ! ( s instanceof Map<?, ?> sample ) ) {
			return;
		}
		Object frames = sample.get( "frames" );
		if ( ! ( frames instanceof List<?> list ) ) {
			return;
		}
		for ( Object o : list ) {
			if ( o instanceof Map<?, ?> f && Boolean.TRUE.equals( f.get( "bx" ) ) ) {
				String					text	= String.valueOf( f.get( "text" ) );
				java.util.regex.Matcher	m		= java.util.regex.Pattern.compile( "\\(([^()]*\\.(?:bxm|bxs|bx|cfc|cfm)):(\\d+)\\)" ).matcher( text );
				if ( m.find() ) {
					synchronized ( req.issues ) {
						for ( Map<String, Object> issue : req.issues ) {
							if ( "Slow request".equals( issue.get( "title" ) ) ) {
								issue.put( "detail", issue.get( "detail" ) + ". At " + sample.get( "atMs" ) + " ms it was in "
								    + m.group( 1 ).replaceAll( ".*/", "" ) + ":" + m.group( 2 ) );
								issue.put( "file", m.group( 1 ) );
								issue.put( "line", Integer.parseInt( m.group( 2 ) ) );
							}
						}
					}
				}
				return;
			}
		}
	}

	/**
	 * The tracked request for a context, or null when this request is not tracked.
	 */
	public LensRequest current( IBoxContext context ) {
		if ( context == null ) {
			return null;
		}
		RequestBoxContext rc = context.getRequestContext();
		if ( rc == null ) {
			return null;
		}
		LensRequest r = rc.getAttachment( Keys.requestAttach );
		return r != null && r.enabled ? r : null;
	}

	/**
	 * Build the JSON for the page: the request snapshot, recent history and UI settings.
	 */
	String pagePayload( String requestJson, String consoleUrl ) {
		Map<String, Object> ui = new LinkedHashMap<>();
		ui.put( "version", version );
		ui.put( "theme", config.getString( "ui.theme", "auto" ) );
		ui.put( "startOpen", config.getBool( "ui.startOpen", false ) );
		ui.put( "autoOpenOnException", config.getBool( "ui.autoOpenOnException", true ) );
		ui.put( "defaultTab", config.getString( "ui.defaultTab", "timeline" ) );
		ui.put( "height", config.getInt( "ui.height", 360 ) );
		ui.put( "allowDetach", config.getBool( "ui.allowDetach", true ) );
		ui.put( "hotkey", config.getString( "ui.hotkey", "Ctrl+`" ) );
		ui.put( "editorLink", config.getString( "editor.linkPattern", "vscode://file/{path}:{line}" ) );
		ui.put( "remoteBase", config.getString( "editor.remoteBase", "" ) );
		ui.put( "localBase", config.getString( "editor.localBase", "" ) );
		ui.put( "maxRequests", config.maxRequests );
		ui.put( "layout", layout.get() );
		ui.put( "hiddenTabs", config.hiddenTabs );
		ui.put( "consoleUrl", consoleUrl );
		ui.put( "slowQueryMs", config.slowQueryMs );
		ui.put( "slowRequestMs", config.slowRequestMs );
		Map<String, Object> page = new LinkedHashMap<>();
		page.put( "ui", ui );
		page.put( "declared", registry.list() );
		page.put( "history", store.summaries() );
		StringBuilder sb = new StringBuilder( requestJson.length() + 4096 );
		sb.append( "{\"data\":" ).append( requestJson );
		String pageJson = Json.write( page );
		sb.append( ',' ).append( pageJson, 1, pageJson.length() );
		return sb.toString();
	}

	private void announce( ortus.boxlang.runtime.scopes.Key point, IStruct data ) {
		try {
			if ( runtime != null ) {
				runtime.getInterceptorService().announce( point, data );
			}
		} catch ( Throwable t ) {
			getLogger().debug( "Listener for [{}] failed: {}", point.getName(), t.toString() );
		}
	}

	private List<ILensCollector> builtIns() {
		return List.of( new LifecycleCollector(), new TemplateCollector(), new FunctionCollector(), new QueryCollector(), new HttpCollector(),
		    new ExceptionCollector(), new LogCollector(), new TransactionCollector(), new ScopesCollector(), new JvmCollector(), new CacheCollector(),
		    new ModulesCollector() );
	}

	private List<String> collectorIds() {
		List<String> ids = new ArrayList<>();
		synchronized ( collectors ) {
			collectors.forEach( c -> ids.add( c.id() ) );
		}
		return ids;
	}

	// ---------------------------------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------------------------------

	public LensConfig getConfig() {
		return config;
	}

	public GlobalStats getStats() {
		return stats;
	}

	public RequestStore getStore() {
		return store;
	}

	public LensRegistry getRegistry() {
		return registry;
	}

	public String getVersion() {
		return version;
	}

	public boolean isEnabled() {
		return config.active;
	}

	public AccessGuard getConsoleGuard() {
		return consoleGuard;
	}

	public AccessGuard getBarGuard() {
		return barGuard;
	}

	public ConsoleAuth getAuth() {
		return auth;
	}

	public LayoutStore getLayout() {
		return layout;
	}

	public Licensing getLicensing() {
		return licensing;
	}

	public TaskOutcomes getOutcomes() {
		return outcomes;
	}

	public ConsoleData getData() {
		return consoleData;
	}

	public AtomicInteger getStreams() {
		return streams;
	}

	public String getModuleDir() {
		return moduleDir;
	}

	public List<ILensCollector> getCollectors() {
		synchronized ( collectors ) {
			return new ArrayList<>( collectors );
		}
	}

	/**
	 * The bxLens logger.
	 */
	public BoxLangLogger auditLogger() {
		BoxLangLogger l = auditLogger;
		if ( l == null ) {
			l			= ( runtime != null ? runtime : BoxRuntime.getInstance() ).getLoggingService().getLogger( "bxlens-audit" );
			auditLogger	= l;
		}
		return l;
	}

	public EnvironmentData getEnvironment() {
		return environment;
	}

	public LogData getLogs() {
		return logs;
	}

	public CacheData getCaches() {
		return caches;
	}

	public DatasourceData getDatasources() {
		return datasources;
	}

	public HeapDumper getHeapDumper() {
		return heapDumper;
	}

	public Audit getAudit() {
		return audit;
	}

	public BoxLangLogger getLogger() {
		BoxLangLogger l = logger;
		if ( l == null ) {
			l		= ( runtime != null ? runtime : BoxRuntime.getInstance() ).getLoggingService().getLogger( "bxLens" );
			logger	= l;
		}
		return l;
	}

}
