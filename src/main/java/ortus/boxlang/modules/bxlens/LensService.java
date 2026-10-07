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

import ortus.boxlang.modules.bxlens.ext.CollectHandle;
import ortus.boxlang.modules.bxlens.ext.LensRegistry;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.interceptors.ILensCollector;
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
	public static final String			MARKER		= "<!--bxlens:here-->";

	private static volatile LensService	instance;

	private volatile BoxRuntime			runtime;
	private volatile LensConfig			config		= LensConfig.defaults();
	private volatile AccessGuard		guard		= new AccessGuard( config );
	private volatile RequestStore		store		= new RequestStore( 50 );
	private volatile BarRenderer		renderer;
	private volatile String				version		= "0.0.0";
	private final GlobalStats			stats		= new GlobalStats();
	private final LensRegistry			registry	= new LensRegistry();
	private final List<ILensCollector>	collectors	= Collections.synchronizedList( new ArrayList<>() );
	private volatile BoxLangLogger		logger;

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
		this.runtime	= runtime;
		this.version	= version == null ? "" : version;
		this.config		= new LensConfig( settings );
		this.guard		= new AccessGuard( this.config );
		this.store		= new RequestStore( this.config.maxRequests );
		this.renderer	= new BarRenderer( Path.of( moduleDir ).resolve( "assets" ), this.config.getBool( "dev.reloadAssets", false ) );
		if ( !this.renderer.isComplete() ) {
			getLogger().warn( "bx-lens assets are missing under [{}]. The bar will not render.", moduleDir );
		}
		if ( !this.config.enabled ) {
			getLogger().info( "bx-lens is installed but disabled. Set modules.bxLens.settings.enabled to true to turn it on." );
			// The lifecycle collector still registers so a runtime config reload can be picked up later
		}
		for ( ILensCollector c : builtIns() ) {
			if ( c instanceof LifecycleCollector || this.config.isCollectorEnabled( c.id(), c.enabledByDefault() ) ) {
				register( c );
			}
		}
		announceRegister();
		getLogger().info( "bx-lens {} active: enabled={}, collectors={}", this.version, this.config.enabled, collectorIds() );
	}

	/**
	 * Unregister all collectors and clear history.
	 */
	public synchronized void shutdown() {
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
		if ( !config.enabled || rc == null ) {
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
		if ( !guard.isAllowed( ex.remoteAddr(), ex.host(), ex.requestHeader( guard.requiredHeader() ) ) ) {
			return null;
		}
		LensRequest req = new LensRequest();
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
		rc.putAttachment( Keys.requestAttach, req );
		try {
			ex.setResponseHeader( config.idHeader, req.id );
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
		stats.recordRequest( Math.round( req.durationNs() / 1_000_000.0 ) );

		Map<String, Object>	snapshot	= Snapshot.build( req, config, ex );
		String				json		= Json.write( snapshot );
		if ( req.html || config.trackNonHtml ) {
			store.add( new RequestStore.Entry( req.id, Snapshot.summary( req ), json ) );
		}
		if ( trigger == Trigger.END && req.html && renderer != null && renderer.isComplete() && ex != null && !ex.responseStarted() ) {
			try {
				StringBuffer	buffer		= rc.getBuffer();
				int				markerAt	= buffer.indexOf( MARKER );
				if ( ( config.inject || markerAt >= 0 ) && req.injected.compareAndSet( false, true ) ) {
					String block = renderer.render( pagePayload( json ) );
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
	String pagePayload( String requestJson ) {
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
		return config.enabled;
	}

	public List<ILensCollector> getCollectors() {
		synchronized ( collectors ) {
			return new ArrayList<>( collectors );
		}
	}

	/**
	 * The bxLens logger.
	 */
	public BoxLangLogger getLogger() {
		BoxLangLogger l = logger;
		if ( l == null ) {
			l		= ( runtime != null ? runtime : BoxRuntime.getInstance() ).getLoggingService().getLogger( "bxLens" );
			logger	= l;
		}
		return l;
	}

}
