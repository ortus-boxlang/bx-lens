/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import ortus.boxlang.modules.bxlens.interceptors.ILensCollector;
import ortus.boxlang.modules.bxlens.store.RequestStore;
import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.modules.bxlens.web.WebExchange;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.util.JSONUtil;

/**
 * The standalone console. Served by <code>lensConsole()</code> from the module's public <code>index.bxm</code>, so every URL lives under
 * <code>/~bxlens/index.bxm</code>. The routes are read from the path after the script name.
 * <p>
 * Everything is checked in this order: the caller's address against <code>console.access</code>, then the session, then the CSRF token for
 * anything that changes state. Pages and API answers are never cached and carry a strict Content-Security-Policy, so the console cannot load
 * anything from outside the server.
 */
public final class ConsoleRouter {

	private static final String			COOKIE		= "bxlens_session";
	private static final String			COOKIE_PATH	= "/~bxlens/";
	private static final Set<String>	ASSETS		= Set.of( "console.css", "console.js", "alpine.min.js" );
	private static final String			CSP			= "default-src 'none'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
	    + "connect-src 'self'; font-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

	private final LensService			service;
	private volatile String				hostName;

	public ConsoleRouter( LensService service ) {
		this.service = service;
	}

	/**
	 * Handle the current request. Writes the whole response.
	 */
	public void handle( IBoxContext context ) {
		WebExchange ex = WebExchange.of( context.getRequestContext() );
		if ( ex == null ) {
			return;
		}
		LensConfig cfg = service.getConfig();
		if ( !cfg.consoleEnabled
		    || !service.getConsoleGuard().isAllowed( ex.remoteAddr(), ex.host(), ex.requestHeader( service.getConsoleGuard().requiredHeader() ) ) ) {
			// Do not reveal that a console exists. The refusal is written to the audit log, at most once a minute per address
			if ( cfg.consoleEnabled ) {
				auditRefusal( "denied.access", ex.remoteAddr(), ex.method() + " " + ex.pathInfo() );
			}
			send( context, ex, 404, "text/plain; charset=UTF-8", "Not found", true );
			return;
		}
		if ( cfg.getBool( "console.requireHttps", false ) && !ex.secure() && !AccessGuard.isLoopback( ex.remoteAddr() ) ) {
			send( context, ex, 403, "text/plain; charset=UTF-8", "HTTPS is required for the console", true );
			return;
		}
		String	path	= ex.pathInfo();
		String	method	= ex.method().toUpperCase();
		if ( path.length() > 1 && path.endsWith( "/" ) ) {
			path = path.substring( 0, path.length() - 1 );
		}
		try {
			if ( path.startsWith( "/assets/" ) && method.equals( "GET" ) ) {
				asset( context, ex, path.substring( "/assets/".length() ) );
			} else if ( ( path.isEmpty() || path.equals( "/" ) ) && method.equals( "GET" ) ) {
				page( context, ex );
			} else if ( path.equals( "/login" ) && method.equals( "POST" ) ) {
				login( context, ex );
			} else if ( path.equals( "/logout" ) && method.equals( "POST" ) ) {
				logout( context, ex );
			} else if ( path.equals( "/stream" ) && method.equals( "GET" ) ) {
				stream( context, ex );
			} else if ( path.startsWith( "/api/" ) ) {
				api( context, ex, method, path.substring( "/api/".length() ) );
			} else {
				send( context, ex, 404, "text/plain; charset=UTF-8", "Not found", true );
			}
		} catch ( Throwable t ) {
			service.getLogger().warn( "bx-lens console error on [{}]: {}", path, t.toString() );
			json( context, ex, 500, Map.of( "error", "Something went wrong. See the server log." ) );
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Pages and assets
	// ---------------------------------------------------------------------------------------------

	private void page( IBoxContext context, WebExchange ex ) {
		ConsoleAuth auth = service.getAuth();
		if ( !auth.isConfigured() ) {
			send( context, ex, 503, "text/html; charset=UTF-8", read( "setup.html" ), true );
			return;
		}
		ConsoleAuth.Session	s		= session( ex );
		String				html	= read( s == null ? "login.html" : "console.html" );
		String				base	= "/~bxlens/index.bxm";
		html = html.replace( "<!--ICONS-->", read( "icons.svg" ) ).replace( "{{base}}", base ).replace( "{{version}}", escape( service.getVersion() ) )
		    .replace( "{{csrf}}", s == null ? "" : s.csrf );
		send( context, ex, 200, "text/html; charset=UTF-8", html, true );
	}

	private void asset( IBoxContext context, WebExchange ex, String name ) {
		if ( !ASSETS.contains( name ) ) {
			send( context, ex, 404, "text/plain; charset=UTF-8", "Not found", false );
			return;
		}
		String	type	= name.endsWith( ".css" ) ? "text/css; charset=UTF-8" : "application/javascript; charset=UTF-8";
		String	body	= read( name );
		String	etag	= "\"" + Integer.toHexString( body.hashCode() ) + "-" + service.getVersion() + "\"";
		String	inm		= ex.requestHeader( "If-None-Match" );
		ex.setResponseHeader( "ETag", etag );
		ex.setResponseHeader( "Cache-Control", "private, max-age=0, must-revalidate" );
		if ( etag.equals( inm ) ) {
			ex.setStatus( 304 );
			return;
		}
		send( context, ex, 200, type, body, false );
	}

	// ---------------------------------------------------------------------------------------------
	// Login and logout
	// ---------------------------------------------------------------------------------------------

	private void login( IBoxContext context, WebExchange ex ) {
		// A custom header cannot be sent by a cross site form post, so this blocks login CSRF
		if ( !"1".equals( ex.requestHeader( "X-Lens-Login" ) ) ) {
			json( context, ex, 400, Map.of( "ok", false, "error", "Bad request" ) );
			return;
		}
		String					password	= ex.formParam( "password" );
		ConsoleAuth.LoginResult	r			= service.getAuth().login( password, ex.remoteAddr() );
		if ( r.ok() ) {
			service.getAudit().log( "login.ok", r.session().role, ex.remoteAddr(), "" );
			ex.setCookie( COOKIE, r.session().id, true, ex.secure(), "Strict", -1, COOKIE_PATH );
			json( context, ex, 200, Map.of( "ok", true ) );
		} else if ( r.lockedSeconds() > 0 ) {
			service.getAudit().log( "login.locked", "none", ex.remoteAddr(), "seconds=" + r.lockedSeconds() );
			ex.setResponseHeader( "Retry-After", String.valueOf( r.lockedSeconds() ) );
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "ok", false );
			m.put( "locked", true );
			m.put( "lockedSeconds", r.lockedSeconds() );
			m.put( "error", "Too many attempts. Try again later." );
			json( context, ex, 429, m );
		} else {
			service.getAudit().log( "login.fail", "none", ex.remoteAddr(), "attemptsLeft=" + r.attemptsLeft() );
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "ok", false );
			m.put( "attemptsLeft", r.attemptsLeft() );
			m.put( "error", "Wrong password." );
			json( context, ex, 401, m );
		}
	}

	private void logout( IBoxContext context, WebExchange ex ) {
		ConsoleAuth.Session s = session( ex );
		if ( s != null && !service.getAuth().csrfOk( s, ex.requestHeader( "X-Lens-CSRF" ) ) ) {
			json( context, ex, 403, Map.of( "error", "Bad CSRF token" ) );
			return;
		}
		if ( s != null ) {
			service.getAudit().log( "logout", s.role, ex.remoteAddr(), "" );
			service.getAuth().logout( s.id );
		}
		ex.setCookie( COOKIE, "", true, ex.secure(), "Strict", 0, COOKIE_PATH );
		json( context, ex, 200, Map.of( "ok", true ) );
	}

	// ---------------------------------------------------------------------------------------------
	// API
	// ---------------------------------------------------------------------------------------------

	private void api( IBoxContext context, WebExchange ex, String method, String route ) {
		ConsoleAuth.Session s = session( ex );
		if ( s == null ) {
			json( context, ex, 401, Map.of( "error", "Sign in required" ) );
			return;
		}
		if ( !method.equals( "GET" ) && !service.getAuth().csrfOk( s, ex.requestHeader( "X-Lens-CSRF" ) ) ) {
			service.getAudit().log( "denied.csrf", s.role, ex.remoteAddr(), method + " " + route );
			json( context, ex, 403, Map.of( "error", "Bad CSRF token" ) );
			return;
		}
		if ( !method.equals( "GET" ) && !route.startsWith( "heapdump" ) && !route.startsWith( "ai/" ) && !route.endsWith( "/test" )
		    && service.getConfig().getBool( "console.readOnly", false ) ) {
			service.getAudit().log( "denied.readonly", s.role, ex.remoteAddr(), method + " " + route );
			json( context, ex, 403, Map.of( "ok", false, "error", "The console is read-only (console.readOnly)" ) );
			return;
		}
		if ( !isAdmin( s ) && ( !method.equals( "GET" ) || ADMIN_ONLY.stream().anyMatch( route::startsWith ) ) ) {
			service.getAudit().log( "denied", s.role, ex.remoteAddr(), method + " " + route );
			json( context, ex, 403, Map.of( "ok", false, "error", "The viewer role cannot do this. Sign in as admin." ) );
			return;
		}
		if ( route.equals( "state" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, state( s, ex ) );
		} else if ( route.equals( "overview" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, overview() );
		} else if ( route.equals( "requests" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, Map.of( "requests", service.getStore().summaries(), "capacity", service.getStore().capacity() ) );
		} else if ( route.startsWith( "requests/" ) && method.equals( "GET" ) ) {
			RequestStore.Entry e = service.getStore().get( route.substring( "requests/".length() ) );
			if ( e == null ) {
				json( context, ex, 404, Map.of( "error", "That request has been recycled" ) );
			} else {
				send( context, ex, 200, "application/json; charset=UTF-8", e.json(), true );
			}
		} else if ( route.equals( "settings" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, settings( session( ex ) ) );
		} else if ( route.startsWith( "heapdump" ) ) {
			heapDump( context, ex, method, route, s );
		} else if ( route.equals( "gc" ) && method.equals( "POST" ) && panelOn( "system" ) ) {
			long	before	= java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
			long	t0		= System.nanoTime();
			System.gc();
			long				after	= java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
			Map<String, Object>	g		= new LinkedHashMap<>();
			g.put( "ok", true );
			g.put( "beforeBytes", before );
			g.put( "afterBytes", after );
			g.put( "freedBytes", Math.max( 0, before - after ) );
			g.put( "ms", ( System.nanoTime() - t0 ) / 1_000_000L );
			service.getAudit().log( "gc.run", s.role, ex.remoteAddr(), "freed=" + g.get( "freedBytes" ) );
			json( context, ex, 200, g );
		} else if ( route.equals( "settings" ) && method.equals( "POST" ) ) {
			changeSettings( context, ex );
		} else if ( route.equals( "settings/reset" ) && method.equals( "POST" ) ) {
			try {
				String key = ex.formParam( "key" );
				service.resetSettings( key );
				service.getAudit().log( "settings.reset", s.role, ex.remoteAddr(), "key=" + ( key == null || key.isBlank() ? "(all)" : key ) );
				json( context, ex, 200, settings( session( ex ) ) );
			} catch ( IllegalArgumentException e ) {
				json( context, ex, 400, Map.of( "ok", false, "error", e.getMessage() ) );
			} catch ( java.io.IOException e ) {
				json( context, ex, 500, Map.of( "ok", false, "error", "Could not save the overrides: " + e.getMessage() ) );
			}
		} else if ( route.equals( "executors" ) && method.equals( "GET" ) && panelOn( "executors" ) ) {
			json( context, ex, 200, service.getData().executors() );
		} else if ( route.equals( "datasources" ) && method.equals( "GET" ) && panelOn( "datasources" ) ) {
			json( context, ex, 200, service.getDatasources().list() );
		} else if ( route.startsWith( "datasources/" ) && route.endsWith( "/test" ) && method.equals( "POST" ) && panelOn( "datasources" ) ) {
			String id = decode( route.substring( "datasources/".length(), route.length() - "/test".length() ) );
			service.getAudit().log( "datasource.test", s.role, ex.remoteAddr(), "datasource=" + id );
			json( context, ex, 200, service.getDatasources().test( id ) );
		} else if ( route.startsWith( "caches" ) && panelOn( "caches" ) ) {
			caches( context, ex, method, route, s );
		} else if ( route.startsWith( "cachevalue/" ) && method.equals( "GET" ) && panelOn( "caches" ) ) {
			if ( plusOnly( context, ex, "cacheActions", "Reading a cache value" ) ) {
				return;
			}
			String name = decode( route.substring( "cachevalue/".length() ) );
			service.getAudit().log( "cache.value", s.role, ex.remoteAddr(), "cache=" + name + " key=" + ex.urlParam( "key" ) );
			Map<String, Object> v = service.getCaches().value( name, ex.urlParam( "key" ) );
			json( context, ex, v == null ? 404 : 200, v == null ? Map.of( "error", "Unknown cache" ) : v );
		} else if ( route.startsWith( "logfiles" ) && method.equals( "GET" ) && panelOn( "logfiles" ) ) {
			logFiles( context, ex, route, s );
		} else if ( route.equals( "modules" ) && method.equals( "GET" ) && panelOn( "modules" ) ) {
			json( context, ex, 200, service.getEnvironment().modules() );
		} else if ( route.equals( "orm" ) && method.equals( "GET" ) && panelOn( "orm" ) ) {
			if ( plusOnly( context, ex, "ormStats", "ORM statistics" ) ) {
				return;
			}
			json( context, ex, 200, service.getOrm().stats() );
		} else if ( route.equals( "environment" ) && method.equals( "GET" ) && panelOn( "environment" ) ) {
			service.getAudit().log( "environment.read", s.role, ex.remoteAddr(), "" );
			json( context, ex, 200, service.getEnvironment().environment() );
		} else if ( route.equals( "bundle" ) && method.equals( "GET" ) ) {
			if ( plusOnly( context, ex, "bundle", "The diagnostic bundle" ) ) {
				return;
			}
			service.getAudit().log( "bundle.download", s.role, ex.remoteAddr(), "" );
			String name = "lens-diagnostics-" + java.time.LocalDateTime.now().format( java.time.format.DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" ) )
			    + ".zip";
			ex.setResponseHeader( "Content-Type", "application/zip" );
			ex.setResponseHeader( "Content-Disposition", "attachment; filename=\"" + name + "\"" );
			ex.setResponseHeader( "Cache-Control", "no-store" );
			ex.setResponseHeader( "X-Content-Type-Options", "nosniff" );
			try {
				ex.sendBinary( service.getEnvironment().bundle() );
			} catch ( java.io.IOException e ) {
				json( context, ex, 500, Map.of( "error", "Could not build the bundle" ) );
			}
		} else if ( route.equals( "queries" ) && method.equals( "GET" ) && panelOn( "queries" ) ) {
			json( context, ex, 200, service.getQueryStats().snapshot() );
		} else if ( route.equals( "queries/reset" ) && method.equals( "POST" ) && panelOn( "queries" ) ) {
			service.getQueryStats().reset();
			service.getAudit().log( "queries.reset", s.role, ex.remoteAddr(), "" );
			json( context, ex, 200, service.getQueryStats().snapshot() );
		} else if ( route.equals( "inflight" ) && method.equals( "GET" ) && panelOn( "inflight" ) ) {
			json( context, ex, 200, Map.of( "requests", service.inflight() ) );
		} else if ( route.startsWith( "inflight/" ) && method.equals( "GET" ) && panelOn( "inflight" ) ) {
			Map<String, Object> st = service.inflightStack( decode( route.substring( "inflight/".length() ) ) );
			json( context, ex, st == null ? 404 : 200, st == null ? Map.of( "error", "That request has finished" ) : st );
		} else if ( route.equals( "errors" ) && method.equals( "GET" ) && panelOn( "errors" ) ) {
			json( context, ex, 200, service.getErrors().list() );
		} else if ( route.startsWith( "errors/" ) && method.equals( "GET" ) && panelOn( "errors" ) ) {
			Map<String, Object> g = service.getErrors().get( decode( route.substring( "errors/".length() ) ) );
			json( context, ex, g == null ? 404 : 200, g == null ? Map.of( "error", "That error is no longer kept" ) : g );
		} else if ( route.equals( "errors/reset" ) && method.equals( "POST" ) && panelOn( "errors" ) ) {
			service.getErrors().reset();
			service.getAudit().log( "errors.reset", s.role, ex.remoteAddr(), "" );
			json( context, ex, 200, service.getErrors().list() );
		} else if ( route.equals( "reports" ) && method.equals( "GET" ) && panelOn( "reports" ) ) {
			json( context, ex, 200, service.getReports().snapshot( service.diskStoreOn() ) );
		} else if ( route.equals( "reports/reset" ) && method.equals( "POST" ) && panelOn( "reports" ) ) {
			service.getReports().reset();
			service.getAudit().log( "reports.reset", s.role, ex.remoteAddr(), "" );
			json( context, ex, 200, service.getReports().snapshot( service.diskStoreOn() ) );
		} else if ( route.startsWith( "ai" ) && ( route.equals( "ai" ) || route.startsWith( "ai/" ) ) ) {
			ai( context, ex, method, route, s );
		} else if ( route.equals( "tasks" ) && method.equals( "GET" ) && panelOn( "tasks" ) ) {
			json( context, ex, 200, tasksFor( s ) );
		} else if ( route.equals( "system" ) && method.equals( "GET" ) && panelOn( "system" ) ) {
			json( context, ex, 200, service.getData().system() );
		} else if ( route.equals( "threads" ) && method.equals( "GET" ) && panelOn( "threads" ) ) {
			json( context, ex, 200, service.getData().threads() );
		} else if ( route.equals( "threads/dump" ) && method.equals( "GET" ) && panelOn( "threads" ) ) {
			String name = "lens-thread-dump-" + java.time.LocalDateTime.now().format( java.time.format.DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" ) )
			    + ".txt";
			service.getAudit().log( "threads.dump", s.role, ex.remoteAddr(), "" );
			ex.setResponseHeader( "Content-Disposition", "attachment; filename=\"" + name + "\"" );
			send( context, ex, 200, "text/plain; charset=UTF-8", service.getData().threadDump(), true );
		} else if ( route.equals( "bar" ) && method.equals( "GET" ) && panelOn( "designer" ) ) {
			json( context, ex, 200, bar() );
		} else if ( route.startsWith( "bar/" ) && method.equals( "POST" ) && !service.getLicensing().has( "barDesigner" ) ) {
			plusOnly( context, ex, "barDesigner", "Saving a bar layout" );
		} else if ( route.equals( "bar/layout" ) && method.equals( "POST" ) && panelOn( "designer" ) ) {
			saveLayout( context, ex );
		} else if ( route.equals( "bar/reset" ) && method.equals( "POST" ) && panelOn( "designer" ) ) {
			try {
				service.getAudit().log( "bar.reset", s.role, ex.remoteAddr(), "" );
				service.getLayout().reset();
				json( context, ex, 200, bar() );
			} catch ( java.io.IOException e ) {
				json( context, ex, 500, Map.of( "error", "Could not save the layout: " + e.getMessage() ) );
			}
		} else if ( route.startsWith( "tasks/" ) && method.equals( "POST" ) && panelOn( "tasks" ) ) {
			taskAction( context, ex, route.substring( "tasks/".length() ) );
		} else {
			json( context, ex, 404, Map.of( "error", "Unknown route" ) );
		}
	}

	/**
	 * Routes only an admin may call, even with GET: files, secrets, and everything that shows the inside of the server. A viewer keeps the
	 * overview, requests, in flight, queries, errors, reports, executors, tasks, datasources, cache statistics and modules.
	 */
	static final List<String>											ADMIN_ONLY		= List.of( "threads", "heapdump", "logfiles", "bundle", "cachevalue",
	    "environment", "system" );

	/** Console pages a viewer does not get. */
	static final Set<String>											ADMIN_PAGES		= Set.of( "logfiles", "environment", "system", "threads" );

	/** Settings whose value a viewer does not see. */
	static final Set<String>											ADMIN_SETTINGS	= Set.of( "console.access", "access.proxypeers" );

	private final java.util.concurrent.ConcurrentHashMap<String, Long>	refusals		= new java.util.concurrent.ConcurrentHashMap<>();

	private void auditRefusal( String event, String ip, String detail ) {
		long	now		= System.currentTimeMillis();
		Long	last	= refusals.get( ip );
		if ( last != null && now - last < 60_000L ) {
			return;
		}
		if ( refusals.size() > 1000 ) {
			refusals.clear();
		}
		refusals.put( ip, now );
		service.getAudit().log( event, "none", ip, detail );
	}

	private static boolean isAdmin( ConsoleAuth.Session s ) {
		return s != null && "admin".equals( s.role );
	}

	/**
	 * Refuse a BoxLang+ feature on Free. Answers 403 and returns true when refused.
	 */
	private boolean plusOnly( IBoxContext context, WebExchange ex, String feature, String what ) {
		if ( service.getLicensing().has( feature ) ) {
			return false;
		}
		service.getAudit().log( "denied.plus", "none", ex.remoteAddr(), feature );
		json( context, ex, 403, Map.of( "ok", false, "plus", true, "error", what + " is a BoxLang+ feature. A license or trial is needed." ) );
		return true;
	}

	private boolean canChange( ConsoleAuth.Session s ) {
		return isAdmin( s ) && !service.getConfig().getBool( "console.readOnly", false );
	}

	private Map<String, Object> tasksFor( ConsoleAuth.Session s ) {
		Map<String, Object> t = service.getData().tasks();
		t.put( "actions", Boolean.TRUE.equals( t.get( "actions" ) ) && canChange( s ) && service.getLicensing().has( "taskActions" ) );
		t.put( "locked", !service.getLicensing().has( "taskActions" ) );
		return t;
	}

	private Map<String, Object> state( ConsoleAuth.Session s, WebExchange ex ) {
		LensConfig			cfg	= service.getConfig();
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "version", service.getVersion() );
		m.put( "host", hostName() );
		m.put( "boxlang", boxlangVersion() );
		m.put( "jvmUptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000 );
		m.put( "csrf", s.csrf );
		m.put( "idleSeconds", service.getAuth().idleSeconds() );
		m.put( "collectLevel", cfg.collectLevel );
		m.put( "barEnabled", cfg.barEnabled );
		m.put( "tabs", tabs( s ) );
		m.put( "actions", cfg.getBool( "console.actions", true ) && canChange( s ) );
		m.put( "readOnly", !canChange( s ) );
		m.put( "role", s.role );
		m.put( "diskStore", service.diskStoreOn() );
		m.put( "plus", service.getLicensing().features() );
		m.put( "historyCap", service.getStore().capacity() );
		m.put( "insecure", !ex.secure() && !AccessGuard.isLoopback( ex.remoteAddr() ) );
		m.put( "liveStreams", service.getStreams().get() );
		m.put( "license", license() );
		return m;
	}

	/**
	 * The pages the console offers, minus the ones hidden in the settings.
	 */
	private List<Map<String, Object>> tabs( ConsoleAuth.Session s ) {
		LensConfig					cfg		= service.getConfig();
		List<Map<String, Object>>	out		= new ArrayList<>();
		String[][]					catalog	= {
		    { "overview", "Overview", "squares-four", "Inspect" },
		    { "requests", "Requests", "list-bullets", "Inspect" },
		    { "inflight", "In flight", "hourglass", "Inspect" },
		    { "errors", "Errors", "bug", "Inspect" },
		    { "reports", "Reports", "chart-bar", "Inspect" },
		    { "ask", "Ask Lens", "sparkle", "Inspect" },
		    { "queries", "Queries", "table", "Inspect" },
		    { "executors", "Executors", "lightning", "Runtime" },
		    { "tasks", "Tasks", "clock-countdown", "Runtime" },
		    { "datasources", "Datasources", "database", "Runtime" },
		    { "orm", "ORM", "stack", "Runtime" },
		    { "caches", "Caches", "package", "Runtime" },
		    { "logfiles", "Logs", "file-text", "Runtime" },
		    { "modules", "Modules", "plug", "Runtime" },
		    { "environment", "Environment", "sliders-horizontal", "Runtime" },
		    { "system", "System", "cpu", "Runtime" },
		    { "threads", "Threads", "tree-structure", "Runtime" },
		    { "designer", "Bar designer", "layout", "Config" },
		    { "settings", "Settings", "gear", "Config" }
		};
		for ( String[] t : catalog ) {
			if ( !isAdmin( s ) && ADMIN_PAGES.contains( t[ 0 ] ) ) {
				continue;
			}
			if ( t[ 0 ].equals( "settings" ) ) {
				// Always there, so the settings can always be read
			} else if ( cfg.hiddenTabs.contains( t[ 0 ] ) || !cfg.isCollectorEnabled( t[ 0 ], true ) ) {
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "id", t[ 0 ] );
			m.put( "label", t[ 1 ] );
			m.put( "icon", t[ 2 ] );
			m.put( "group", t[ 3 ] );
			out.add( m );
		}
		return out;
	}

	private Map<String, Object> license() {
		return service.getLicensing().status();
	}

	/**
	 * Heap dump: <code>GET heapdump</code> info, <code>POST heapdump</code> start (needs <code>confirm=true</code>), <code>GET heapdump/download</code>,
	 * <code>POST heapdump/discard</code>. Needs <code>console.allowHeapDump</code>. Read-only mode does not block it.
	 */
	/**
	 * <code>GET caches</code>, <code>GET caches/{name}/keys?filter=</code>, <code>POST caches/{name}/{clear|evict|reap}</code>.
	 */
	private void caches( IBoxContext context, WebExchange ex, String method, String route, ConsoleAuth.Session s ) {
		if ( route.equals( "caches" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, service.getCaches().list() );
			return;
		}
		String[] parts = route.split( "/" );
		if ( parts.length == 3 && parts[ 0 ].equals( "caches" ) ) {
			String name = decode( parts[ 1 ] );
			if ( parts[ 2 ].equals( "keys" ) && method.equals( "GET" ) ) {
				Map<String, Object> k = service.getCaches().keys( name, ex.urlParam( "filter" ) );
				json( context, ex, k == null ? 404 : 200, k == null ? Map.of( "error", "Unknown cache" ) : k );
				return;
			}
			if ( method.equals( "POST" ) ) {
				if ( plusOnly( context, ex, "cacheActions", "Evicting, reaping and clearing a cache" ) ) {
					return;
				}
				if ( !service.getConfig().getBool( "console.actions", true ) ) {
					service.getAudit().log( "denied.actions", s.role, ex.remoteAddr(), "cache." + parts[ 2 ] );
					json( context, ex, 403, Map.of( "ok", false, "message", "Actions are turned off in the settings" ) );
					return;
				}
				String key = ex.formParam( "key" );
				service.getAudit().log( "cache." + parts[ 2 ], s.role, ex.remoteAddr(), "cache=" + name + ( key == null ? "" : " key=" + key ) );
				Map<String, Object> r = service.getCaches().action( name, parts[ 2 ], key );
				json( context, ex, r == null ? 404 : 200, r == null ? Map.of( "error", "Unknown cache" ) : r );
				return;
			}
		}
		json( context, ex, 404, Map.of( "error", "Unknown route" ) );
	}

	/**
	 * <code>logfiles</code> lists the files, <code>logfiles/read?file=&lines=&q=&level=</code> reads the tail, <code>logfiles/download?file=</code>
	 * sends the whole file (admin only).
	 */
	private void logFiles( IBoxContext context, WebExchange ex, String route, ConsoleAuth.Session s ) {
		LogData logs = service.getLogs();
		try {
			if ( route.equals( "logfiles" ) ) {
				json( context, ex, 200, logs.list() );
			} else if ( route.equals( "logfiles/read" ) ) {
				int lines = 500;
				try {
					lines = Integer.parseInt( String.valueOf( ex.urlParam( "lines" ) ) );
				} catch ( NumberFormatException e ) {
					// Default
				}
				String q = ex.urlParam( "q" );
				service.getAudit().log( "logfile.read", s.role, ex.remoteAddr(),
				    "file=" + ex.urlParam( "file" ) + " lines=" + lines + ( q == null || q.isEmpty() ? "" : " search=" + q.length() + " chars" ) );
				Map<String, Object> r = logs.read( ex.urlParam( "file" ), lines, q, ex.urlParam( "level" ) );
				json( context, ex, r == null ? 404 : 200, r == null ? Map.of( "error", "No such log file" ) : r );
			} else if ( route.equals( "logfiles/download" ) ) {
				if ( plusOnly( context, ex, "logDownload", "Downloading a log file" ) ) {
					return;
				}
				java.nio.file.Path p = logs.resolve( ex.urlParam( "file" ) );
				if ( p == null ) {
					json( context, ex, 404, Map.of( "error", "No such log file" ) );
					return;
				}
				service.getAudit().log( "logfile.download", s.role, ex.remoteAddr(), "file=" + p.getFileName() );
				ex.setResponseHeader( "Content-Type", "text/plain; charset=UTF-8" );
				ex.setResponseHeader( "Content-Disposition",
				    "attachment; filename=\"" + p.getFileName().toString().replaceAll( "[^A-Za-z0-9._-]", "_" ) + "\"" );
				ex.setResponseHeader( "Cache-Control", "no-store" );
				ex.setResponseHeader( "X-Content-Type-Options", "nosniff" );
				ex.sendFile( p.toFile() );
			} else {
				json( context, ex, 404, Map.of( "error", "Unknown route" ) );
			}
		} catch ( LogData.Busy e ) {
			ex.setResponseHeader( "Retry-After", "2" );
			json( context, ex, 429, Map.of( "error", e.getMessage() ) );
		} catch ( java.io.IOException e ) {
			json( context, ex, 500, Map.of( "error", "Could not read the log file" ) );
		}
	}

	/**
	 * <code>GET ai</code> status, <code>GET ai/prompt</code> the text to copy, <code>POST ai/explain</code> and <code>POST ai/ask</code> call the model
	 * (admin only, needs ai.enabled and bx-ai).
	 */
	private void ai( IBoxContext context, WebExchange ex, String method, String route, ConsoleAuth.Session s ) {
		AiService ai = service.getAi();
		if ( route.equals( "ai" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, ai.info() );
			return;
		}
		try {
			if ( route.equals( "ai/prompt" ) && method.equals( "GET" ) ) {
				if ( "deadlock".equals( ex.urlParam( "kind" ) ) && !isAdmin( s ) ) {
					service.getAudit().log( "denied", s.role, ex.remoteAddr(), "ai/prompt deadlock" );
					json( context, ex, 403, Map.of( "ok", false, "error", "Thread stacks are for the admin role" ) );
					return;
				}
				String p = promptFor( ex, true );
				json( context, ex, p == null ? 404 : 200, p == null ? Map.of( "error", "Nothing to explain" ) : Map.of( "prompt", p ) );
			} else if ( route.equals( "ai/explain" ) && method.equals( "POST" ) ) {
				String p = promptFor( ex, false );
				if ( p == null ) {
					json( context, ex, 404, Map.of( "ok", false, "error", "Nothing to explain" ) );
					return;
				}
				service.getAudit().log( "ai.explain", s.role, ex.remoteAddr(),
				    "kind=" + ex.formParam( "kind" ) + " chars=" + p.length() + " provider=" + ai.info().get( "provider" ) );
				json( context, ex, 200, Map.of( "ok", true, "answer", ai.chat( p ) ) );
			} else if ( route.equals( "ai/ask" ) && method.equals( "POST" ) ) {
				String q = ex.formParam( "question" );
				if ( q == null || q.isBlank() || q.length() > 1000 ) {
					json( context, ex, 400, Map.of( "ok", false, "error", "Ask a question of up to 1000 characters" ) );
					return;
				}
				String p = AiPrompts.ask( q.trim(), ai.context() );
				service.getAudit().log( "ai.ask", s.role, ex.remoteAddr(), "chars=" + p.length() + " provider=" + ai.info().get( "provider" ) );
				json( context, ex, 200, Map.of( "ok", true, "answer", ai.chat( p ) ) );
			} else {
				json( context, ex, 404, Map.of( "error", "Unknown route" ) );
			}
		} catch ( IllegalStateException e ) {
			json( context, ex, 409, Map.of( "ok", false, "error", e.getMessage() ) );
		}
	}

	@SuppressWarnings( "unchecked" )
	private String promptFor( WebExchange ex, boolean query ) {
		String	kind	= query ? ex.urlParam( "kind" ) : ex.formParam( "kind" );
		String	id		= query ? ex.urlParam( "id" ) : ex.formParam( "id" );
		String	other	= query ? ex.urlParam( "n" ) : ex.formParam( "n" );
		if ( kind == null ) {
			return null;
		}
		switch ( kind ) {
			case "error" : {
				Map<String, Object> g = service.getErrors().get( id == null ? "" : id );
				if ( g == null ) {
					return null;
				}
				List<Object>	samples	= ( List<Object> ) g.get( "samples" );
				int				i		= 0;
				try {
					i = Integer.parseInt( String.valueOf( other ) );
				} catch ( NumberFormatException e ) {
					// First sample
				}
				return samples.isEmpty() ? null : AiPrompts.error( g, ( Map<String, Object> ) samples.get( Math.max( 0, Math.min( i, samples.size() - 1 ) ) ) );
			}
			case "deadlock" : {
				Map<String, Object>			t		= service.getData().threads();
				List<Object>				dead	= ( List<Object> ) t.get( "deadlocked" );
				List<Map<String, Object>>	mine	= new ArrayList<>();
				for ( Object th : ( List<Object> ) t.get( "threads" ) ) {
					Map<String, Object> m = ( Map<String, Object> ) th;
					if ( dead.contains( m.get( "id" ) ) ) {
						mine.add( m );
					}
				}
				return mine.isEmpty() ? null : AiPrompts.deadlock( mine );
			}
			case "ask" :
				return id == null || id.isBlank() || id.length() > 1000 ? null : AiPrompts.ask( id.trim(), service.getAi().context() );
			case "query" : {
				for ( Object o : ( List<Object> ) service.getQueryStats().snapshot().get( "statements" ) ) {
					Map<String, Object> m = ( Map<String, Object> ) o;
					if ( String.valueOf( m.get( "sql" ) ).equals( id ) && String.valueOf( m.get( "datasource" ) ).equals( other == null ? "" : other ) ) {
						return AiPrompts.query( m );
					}
				}
				return null;
			}
			default :
				return null;
		}
	}

	private void heapDump( IBoxContext context, WebExchange ex, String method, String route, ConsoleAuth.Session s ) {
		boolean		allowed	= service.getConfig().getBool( "console.allowHeapDump", false );
		HeapDumper	hd		= service.getHeapDumper();
		if ( route.equals( "heapdump" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, hd.info( allowed ) );
			return;
		}
		if ( !route.equals( "heapdump" ) || !method.equals( "GET" ) ) {
			if ( plusOnly( context, ex, "heapDump", "A heap dump" ) ) {
				return;
			}
		}
		if ( !allowed ) {
			json( context, ex, 403, Map.of( "ok", false, "error", "Heap dumps are off. Set console.allowHeapDump to true in boxlang.json." ) );
			return;
		}
		if ( route.equals( "heapdump" ) && method.equals( "POST" ) ) {
			if ( !"true".equals( ex.formParam( "confirm" ) ) ) {
				json( context, ex, 400, Map.of( "ok", false, "error", "Confirmation is required" ) );
				return;
			}
			String refused = hd.start();
			service.getAudit().log( refused == null ? "heapdump.start" : "heapdump.refused", s.role, ex.remoteAddr(), refused == null ? "" : refused );
			if ( refused != null ) {
				json( context, ex, 409, Map.of( "ok", false, "error", refused ) );
			} else {
				json( context, ex, 202, hd.info( true ) );
			}
		} else if ( route.equals( "heapdump/download" ) && method.equals( "GET" ) ) {
			java.io.File f = hd.takeForDownload();
			if ( f == null ) {
				json( context, ex, 404, Map.of( "ok", false, "error", "There is no heap dump to download" ) );
				return;
			}
			service.getAudit().log( "heapdump.download", s.role, ex.remoteAddr(), "bytes=" + f.length() );
			String name = "lens-heap-" + java.time.LocalDateTime.now().format( java.time.format.DateTimeFormatter.ofPattern( "yyyyMMdd-HHmmss" ) ) + ".hprof";
			ex.setResponseHeader( "Content-Type", "application/octet-stream" );
			ex.setResponseHeader( "Content-Disposition", "attachment; filename=\"" + name + "\"" );
			ex.setResponseHeader( "Cache-Control", "no-store" );
			ex.setResponseHeader( "X-Content-Type-Options", "nosniff" );
			ex.sendFile( f );
		} else if ( route.equals( "heapdump/discard" ) && method.equals( "POST" ) ) {
			hd.discard();
			service.getAudit().log( "heapdump.discard", s.role, ex.remoteAddr(), "" );
			json( context, ex, 200, hd.info( true ) );
		} else {
			json( context, ex, 404, Map.of( "error", "Unknown route" ) );
		}
	}

	private Map<String, Object> settings( ConsoleAuth.Session s ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "readOnly", !canChange( s ) );
		m.put( "viewer", !isAdmin( s ) );
		m.put( "groups", SettingsRegistry.GROUPS );
		List<Map<String, Object>> view = service.settingsView();
		if ( !isAdmin( s ) ) {
			// Where the console is reachable from, which proxies are trusted and where overrides are saved are for admins
			List<Map<String, Object>> masked = new ArrayList<>();
			for ( Map<String, Object> row : view ) {
				Map<String, Object>	r	= new LinkedHashMap<>( row );
				String				key	= String.valueOf( r.get( "key" ) ).toLowerCase( java.util.Locale.ROOT );
				if ( ADMIN_SETTINGS.contains( key ) || key.equals( "console.overridesfile" ) ) {
					r.put( "value", "admin only" );
					r.put( "configured", "admin only" );
				}
				masked.add( r );
			}
			view = masked;
		}
		m.put( "settings", view );
		m.put( "overridesFile", isAdmin( s ) ? String.valueOf( service.getSettingsStore().file() ) : "" );
		m.put( "overridden", service.getSettingsStore().get().size() );
		return m;
	}

	private void changeSettings( IBoxContext context, WebExchange ex ) {
		ConsoleAuth.Session sess = session( ex );
		try {
			Object parsed = JSONUtil.fromJSON( ex.formParam( "changes" ) );
			if ( ! ( parsed instanceof Map<?, ?> in ) ) {
				json( context, ex, 400, Map.of( "ok", false, "error", "Expected a map of changes" ) );
				return;
			}
			Map<String, Object> changes = new LinkedHashMap<>();
			for ( Map.Entry<?, ?> e : in.entrySet() ) {
				String k = e.getKey() instanceof ortus.boxlang.runtime.scopes.Key key ? key.getName() : String.valueOf( e.getKey() );
				changes.put( k, e.getValue() );
			}
			service.changeSettings( changes );
			service.getAudit().log( "settings.change", sess.role, ex.remoteAddr(), "keys=" + changes );
			json( context, ex, 200, settings( session( ex ) ) );
		} catch ( IllegalArgumentException e ) {
			json( context, ex, 400, Map.of( "ok", false, "error", e.getMessage() ) );
		} catch ( java.io.IOException e ) {
			json( context, ex, 500, Map.of( "ok", false, "error", "Could not save the overrides: " + e.getMessage() ) );
		} catch ( Throwable t ) {
			json( context, ex, 400, Map.of( "ok", false, "error", "Could not read the changes" ) );
		}
	}

	/**
	 * Aggregates over the requests in memory: counts, errors, percentiles and the slowest routes.
	 */
	private Map<String, Object> overview() {
		List<Map<String, Object>>	all		= service.getStore().summaries();
		long						now		= System.currentTimeMillis();
		long						cutoff	= now - 5 * 60_000L;
		List<Map<String, Object>>	win		= new ArrayList<>();
		for ( Map<String, Object> r : all ) {
			if ( num( r.get( "at" ) ) >= cutoff ) {
				win.add( r );
			}
		}
		List<Map<String, Object>>	use	= win.isEmpty() ? all : win;
		Map<String, Object>			m	= new LinkedHashMap<>();
		m.put( "windowMinutes", win.isEmpty() ? 0 : 5 );
		m.put( "requests", use.size() );
		int										errors	= 0;
		double									queries	= 0;
		double[]								times	= new double[ use.size() ];
		Map<String, List<Map<String, Object>>>	routes	= new TreeMap<>();
		int										i		= 0;
		for ( Map<String, Object> r : use ) {
			if ( num( r.get( "status" ) ) >= 500 ) {
				errors++;
			}
			queries			+= num( r.get( "queries" ) );
			times[ i++ ]	= num( r.get( "ms" ) );
			String	url	= String.valueOf( r.get( "url" ) );
			int		q	= url.indexOf( '?' );
			routes.computeIfAbsent( q > 0 ? url.substring( 0, q ) : url, k -> new ArrayList<>() ).add( r );
		}
		java.util.Arrays.sort( times );
		m.put( "errors", errors );
		m.put( "errorRate", use.isEmpty() ? 0 : Math.round( errors * 1000.0 / use.size() ) / 10.0 );
		m.put( "medianMs", percentile( times, 0.5 ) );
		m.put( "p95Ms", percentile( times, 0.95 ) );
		m.put( "queriesPerRequest", use.isEmpty() ? 0 : Math.round( queries * 10.0 / use.size() ) / 10.0 );
		m.put( "perSecond", win.isEmpty() ? 0 : Math.round( win.size() * 10.0 / 300 ) / 10.0 );
		List<Map<String, Object>> slow = new ArrayList<>();
		routes.forEach( ( route, rs ) -> {
			double[]			t	= rs.stream().mapToDouble( r -> num( r.get( "ms" ) ) ).sorted().toArray();
			long				err	= rs.stream().filter( r -> num( r.get( "status" ) ) >= 500 ).count();
			Map<String, Object>	rm	= new LinkedHashMap<>();
			rm.put( "route", route );
			rm.put( "p95Ms", percentile( t, 0.95 ) );
			rm.put( "calls", rs.size() );
			rm.put( "errors", err );
			slow.add( rm );
		} );
		slow.sort( Comparator.comparingDouble( ( Map<String, Object> r ) -> num( r.get( "p95Ms" ) ) ).reversed() );
		m.put( "slowest", slow.size() > 6 ? slow.subList( 0, 6 ) : slow );
		// Series for the sparkline: requests per 5 second bucket over the last 5 minutes
		int[] buckets = new int[ 60 ];
		for ( Map<String, Object> r : all ) {
			long age = now - ( long ) num( r.get( "at" ) );
			if ( age >= 0 && age < 300_000 ) {
				buckets[ 59 - ( int ) ( age / 5000 ) ]++;
			}
		}
		List<Integer> series = new ArrayList<>();
		for ( int b : buckets ) {
			series.add( b );
		}
		m.put( "series", series );
		// Attention list from what we know today: failing routes and slow routes
		List<Map<String, Object>> attention = new ArrayList<>();
		for ( Map<String, Object> r : slow ) {
			if ( num( r.get( "errors" ) ) > 0 ) {
				attention.add( item( "crit", r.get( "route" ) + " returned " + r.get( "errors" ) + " server errors", "requests" ) );
			} else if ( num( r.get( "p95Ms" ) ) >= service.getConfig().slowRequestMs ) {
				attention.add( item( "warn", r.get( "route" ) + " p95 is " + Math.round( num( r.get( "p95Ms" ) ) ) + " ms", "requests" ) );
			}
		}
		m.put( "attention", attention.size() > 6 ? attention.subList( 0, 6 ) : attention );
		return m;
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private static final String[][] BAR_TABS = {
	    { "issues", "Issues" }, { "timeline", "Timeline" }, { "queries", "Queries" }, { "templates", "Templates" }, { "http", "HTTP" },
	    { "exceptions", "Exceptions" }, { "messages", "Messages" }, { "timers", "Timers" }, { "cache", "Cache" }, { "modules", "Modules" }, { "bifs", "BIFs" },
	    { "request", "Request" }, { "scopes", "Scopes" }, { "jvm", "Runtime" }, { "history", "History" }
	};

	/**
	 * Every tab the bar can show: the built-in ones and the panels modules and applications declared.
	 */
	private List<Map<String, Object>> barCatalog() {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( String[] t : BAR_TABS ) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "id", t[ 0 ] );
			m.put( "label", t[ 1 ] );
			m.put( "custom", false );
			m.put( "hidden", service.getConfig().hiddenTabs.contains( t[ 0 ] ) );
			out.add( m );
		}
		for ( Map<String, Object> p : service.getRegistry().list() ) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "id", p.get( "id" ) );
			m.put( "label", p.get( "label" ) );
			m.put( "custom", true );
			m.put( "hidden", service.getConfig().hiddenTabs.contains( String.valueOf( p.get( "id" ) ) ) );
			out.add( m );
		}
		return out;
	}

	private Map<String, Object> bar() {
		LensConfig			cfg	= service.getConfig();
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "enabled", cfg.barEnabled );
		m.put( "catalog", barCatalog() );
		m.put( "layout", service.getLayout().get() );
		return m;
	}

	private void saveLayout( IBoxContext context, WebExchange ex ) {
		try {
			Object parsed = JSONUtil.fromJSON( ex.formParam( "layout" ) );
			if ( ! ( parsed instanceof java.util.Collection<?> c ) ) {
				json( context, ex, 400, Map.of( "error", "Expected a list of tabs" ) );
				return;
			}
			Set<String> valid = new java.util.HashSet<>();
			for ( Map<String, Object> t : barCatalog() ) {
				valid.add( String.valueOf( t.get( "id" ) ) );
			}
			service.getLayout().save( c, valid );
			service.getAudit().log( "bar.layout", session( ex ).role, ex.remoteAddr(), "" );
			json( context, ex, 200, bar() );
		} catch ( Throwable t ) {
			json( context, ex, 400, Map.of( "error", "Could not save the layout" ) );
		}
	}

	private boolean panelOn( String id ) {
		LensConfig cfg = service.getConfig();
		return cfg.isCollectorEnabled( id, true ) && !cfg.hiddenTabs.contains( id );
	}

	/**
	 * Tasks actions: <code>{scheduler}/{task}/{pause|resume|run}</code> or <code>{scheduler}/{pauseall|resumeall|reload}</code>.
	 */
	private void taskAction( IBoxContext context, WebExchange ex, String route ) {
		if ( plusOnly( context, ex, "taskActions", "Running, pausing, resuming and reloading tasks" ) ) {
			return;
		}
		if ( !service.getConfig().getBool( "console.actions", true ) ) {
			json( context, ex, 403, Map.of( "ok", false, "message", "Actions are turned off in the settings" ) );
			return;
		}
		String[]	parts	= route.split( "/" );
		String		scheduler, task = null, action;
		if ( parts.length == 2 ) {
			scheduler	= decode( parts[ 0 ] );
			action		= parts[ 1 ];
		} else if ( parts.length == 3 ) {
			scheduler	= decode( parts[ 0 ] );
			task		= decode( parts[ 1 ] );
			action		= parts[ 2 ];
		} else {
			json( context, ex, 404, Map.of( "error", "Unknown route" ) );
			return;
		}
		service.getAudit().log( "task." + action, session( ex ).role, ex.remoteAddr(), "task=" + scheduler + ( task == null ? "" : "/" + task ) );
		json( context, ex, 200, service.getData().taskAction( action, scheduler, task ) );
	}

	/**
	 * Server sent events. One loop per browser: a newer stream from the same session stops the older one. Ends after ten minutes and the
	 * browser reconnects by itself.
	 */
	private void stream( IBoxContext context, WebExchange ex ) {
		ConsoleAuth.Session s = session( ex );
		if ( s == null ) {
			json( context, ex, 401, Map.of( "error", "Sign in required" ) );
			return;
		}
		int max = Math.max( 1, service.getConfig().getInt( "console.maxStreams", 10 ) );
		// Take the place first and give it back when over the limit, so two streams that start together cannot both slip under it
		if ( service.getStreams().incrementAndGet() > max ) {
			service.getStreams().decrementAndGet();
			json( context, ex, 429, Map.of( "error", "Too many live streams" ) );
			return;
		}
		String		topics	= ex.urlParam( "topics" ) == null ? "executors,tasks,system" : ex.urlParam( "topics" );
		Set<String>	want	= new java.util.HashSet<>( List.of( topics.split( "," ) ) );
		String		logFile	= want.contains( "log" ) && isAdmin( s ) ? ex.urlParam( "logfile" ) : null;
		long		logPos	= 0;
		try {
			logPos = Long.parseLong( String.valueOf( ex.urlParam( "logoffset" ) ) );
		} catch ( NumberFormatException e ) {
			// Starts at the end of the file below
		}
		if ( logFile != null && logPos <= 0 ) {
			java.nio.file.Path lp = service.getLogs().resolve( logFile );
			try {
				logPos = lp == null ? 0 : java.nio.file.Files.size( lp );
			} catch ( java.io.IOException e ) {
				logPos = 0;
			}
		}
		int mine = s.streamGen.incrementAndGet();
		try {
			ex.setStatus( 200 );
			ex.setResponseHeader( "Content-Type", "text/event-stream; charset=UTF-8" );
			ex.setResponseHeader( "Cache-Control", "no-cache, no-transform" );
			ex.setResponseHeader( "X-Accel-Buffering", "no" );
			ex.setResponseHeader( "X-Content-Type-Options", "nosniff" );
			context.writeToBuffer( ": connected\n\n" );
			context.flushBuffer( true );
			long end = System.currentTimeMillis() + 5 * 60_000L;
			while ( System.currentTimeMillis() < end && !Thread.currentThread().isInterrupted() ) {
				if ( service.getAuth().peek( s.id ) == null || s.streamGen.get() != mine ) {
					break;
				}
				Map<String, Object> tick = new LinkedHashMap<>();
				tick.put( "at", System.currentTimeMillis() );
				if ( want.contains( "executors" ) && panelOn( "executors" ) ) {
					tick.put( "executors", service.getData().executors() );
				}
				if ( want.contains( "tasks" ) && panelOn( "tasks" ) ) {
					tick.put( "tasks", tasksFor( s ) );
				}
				if ( want.contains( "datasources" ) && panelOn( "datasources" ) ) {
					tick.put( "datasources", service.getDatasources().list() );
				}
				if ( want.contains( "inflight" ) && panelOn( "inflight" ) ) {
					tick.put( "inflight", Map.of( "requests", service.inflight() ) );
				}
				if ( want.contains( "queries" ) && panelOn( "queries" ) ) {
					tick.put( "queries", service.getQueryStats().snapshot() );
				}
				if ( want.contains( "system" ) && panelOn( "system" ) && isAdmin( s ) ) {
					tick.put( "system", service.getData().system() );
				}
				if ( logFile != null && panelOn( "logfiles" ) ) {
					Map<String, Object> more = service.getLogs().since( logFile, logPos );
					if ( more != null ) {
						logPos = ( Long ) more.get( "offset" );
						if ( ! ( ( List<?> ) more.get( "lines" ) ).isEmpty() ) {
							tick.put( "log", more );
						}
					}
				}
				if ( want.contains( "requests" ) ) {
					tick.put( "requests", service.getStore().summaries() );
					tick.put( "overview", overview() );
				}
				context.writeToBuffer( "event: tick\ndata: " + Json.write( tick ) + "\n\n" );
				context.flushBuffer( true );
				if ( ex.writeFailed() ) {
					break;
				}
				Thread.sleep( 1000 );
			}
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		} catch ( Throwable t ) {
			// The browser went away
		} finally {
			service.getStreams().decrementAndGet();
		}
	}

	private static String decode( String s ) {
		return java.net.URLDecoder.decode( s, StandardCharsets.UTF_8 );
	}

	private Map<String, Object> item( String sev, String text, String go ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "severity", sev );
		m.put( "text", text );
		m.put( "go", go );
		return m;
	}

	private ConsoleAuth.Session session( WebExchange ex ) {
		return service.getAuth().find( ex.cookie( COOKIE ) );
	}

	private String bodyField( WebExchange ex, String field ) {
		try {
			Object raw = ex.rawBody();
			if ( raw instanceof Map<?, ?> m ) {
				return valueOf( m, field );
			}
			Object o = JSONUtil.fromJSON( ex.body() );
			if ( o instanceof Map<?, ?> m ) {
				return valueOf( m, field );
			}
		} catch ( Throwable t ) {
			// Not JSON
		}
		return null;
	}

	private static String valueOf( Map<?, ?> m, String field ) {
		for ( Map.Entry<?, ?> e : m.entrySet() ) {
			String k = e.getKey() instanceof Key key ? key.getName() : String.valueOf( e.getKey() );
			if ( k.equalsIgnoreCase( field ) ) {
				return e.getValue() == null ? null : e.getValue().toString();
			}
		}
		return null;
	}

	private void json( IBoxContext context, WebExchange ex, int status, Object body ) {
		send( context, ex, status, "application/json; charset=UTF-8", Json.write( body ), true );
	}

	private void send( IBoxContext context, WebExchange ex, int status, String type, String body, boolean noStore ) {
		ex.setStatus( status );
		ex.setResponseHeader( "Content-Type", type );
		ex.setResponseHeader( "X-Content-Type-Options", "nosniff" );
		ex.setResponseHeader( "Referrer-Policy", "no-referrer" );
		ex.setResponseHeader( "X-Frame-Options", "DENY" );
		if ( type.startsWith( "text/html" ) ) {
			ex.setResponseHeader( "Content-Security-Policy", CSP );
		}
		if ( noStore ) {
			ex.setResponseHeader( "Cache-Control", "no-store" );
		}
		context.writeToBuffer( body );
	}

	private String read( String name ) {
		try {
			return Files.readString( Path.of( service.getModuleDir() ).resolve( "assets" ).resolve( name ), StandardCharsets.UTF_8 );
		} catch ( IOException e ) {
			return "";
		}
	}

	private String hostName() {
		String h = hostName;
		if ( h == null ) {
			try {
				h = InetAddress.getLocalHost().getHostName();
			} catch ( Exception e ) {
				h = "localhost";
			}
			hostName = h;
		}
		return h;
	}

	private String boxlangVersion() {
		try {
			Object v = BoxRuntime.getInstance().getVersionInfo().get( Key.of( "version" ) );
			return v == null ? "" : v.toString();
		} catch ( Throwable t ) {
			return "";
		}
	}

	private static double percentile( double[] sorted, double p ) {
		if ( sorted.length == 0 ) {
			return 0;
		}
		int idx = ( int ) Math.min( sorted.length - 1, Math.ceil( p * sorted.length ) - 1 );
		return Math.round( sorted[ Math.max( 0, idx ) ] * 10.0 ) / 10.0;
	}

	private static double num( Object o ) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

	private static String escape( String s ) {
		return s == null ? "" : s.replace( "&", "&amp;" ).replace( "<", "&lt;" ).replace( ">", "&gt;" ).replace( "\"", "&quot;" );
	}

}
