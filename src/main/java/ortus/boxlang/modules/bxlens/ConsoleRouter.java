/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
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
	private static final long			STARTED		= System.currentTimeMillis();

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
			// Do not reveal that a console exists
			send( context, ex, 404, "text/plain; charset=UTF-8", "Not found", true );
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
			ex.setCookie( COOKIE, r.session().id, true, ex.secure(), "Strict", -1, COOKIE_PATH );
			json( context, ex, 200, Map.of( "ok", true ) );
		} else if ( r.lockedSeconds() > 0 ) {
			ex.setResponseHeader( "Retry-After", String.valueOf( r.lockedSeconds() ) );
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "ok", false );
			m.put( "locked", true );
			m.put( "lockedSeconds", r.lockedSeconds() );
			m.put( "error", "Too many attempts. Try again later." );
			json( context, ex, 429, m );
		} else {
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
			json( context, ex, 403, Map.of( "error", "Bad CSRF token" ) );
			return;
		}
		if ( route.equals( "state" ) && method.equals( "GET" ) ) {
			json( context, ex, 200, state( s ) );
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
			json( context, ex, 200, settings() );
		} else {
			json( context, ex, 404, Map.of( "error", "Unknown route" ) );
		}
	}

	private Map<String, Object> state( ConsoleAuth.Session s ) {
		LensConfig			cfg	= service.getConfig();
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "version", service.getVersion() );
		m.put( "host", hostName() );
		m.put( "boxlang", boxlangVersion() );
		m.put( "uptimeSeconds", ( System.currentTimeMillis() - STARTED ) / 1000 );
		m.put( "jvmUptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000 );
		m.put( "csrf", s.csrf );
		m.put( "idleSeconds", service.getAuth().idleSeconds() );
		m.put( "collectLevel", cfg.collectLevel );
		m.put( "barEnabled", cfg.barEnabled );
		m.put( "tabs", tabs() );
		m.put( "license", license() );
		return m;
	}

	/**
	 * The pages the console offers, minus the ones hidden in the settings.
	 */
	private List<Map<String, Object>> tabs() {
		LensConfig					cfg		= service.getConfig();
		List<Map<String, Object>>	out		= new ArrayList<>();
		String[][]					catalog	= {
		    { "overview", "Overview", "squares-four", "Inspect" },
		    { "requests", "Requests", "list-bullets", "Inspect" },
		    { "settings", "Settings", "gear", "Config" }
		};
		for ( String[] t : catalog ) {
			if ( cfg.hiddenTabs.contains( t[ 0 ] ) && !t[ 0 ].equals( "settings" ) ) {
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

	/**
	 * License status. Detection through bx-plus comes with the licensing step, until then everything is available.
	 */
	private Map<String, Object> license() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "state", "none" );
		m.put( "label", "Free" );
		m.put( "note", "License detection is not wired up yet" );
		return m;
	}

	private Map<String, Object> settings() {
		LensConfig			cfg	= service.getConfig();
		Map<String, Object>	m	= new LinkedHashMap<>();
		Map<String, Object>	a	= new LinkedHashMap<>();
		a.put( "console.enabled", cfg.consoleEnabled );
		a.put( "console.password", service.getAuth().isConfigured() ? "set" : "not set" );
		a.put( "console.access", cfg.get( "console.access" ) == null ? "local" : cfg.get( "console.access" ) );
		a.put( "console.sessionMinutes", cfg.sessionMinutes );
		a.put( "bar.enabled", cfg.barEnabled );
		a.put( "bar.access", cfg.get( "bar.access" ) == null ? "local" : cfg.get( "bar.access" ) );
		a.put( "bar.allowAllIPs", cfg.getBool( "bar.allowAllIPs", false ) );
		a.put( "collect.level", cfg.collectLevel );
		a.put( "history.maxRequests", cfg.maxRequests );
		m.put( "access", a );
		List<Map<String, Object>> cols = new ArrayList<>();
		for ( ILensCollector c : service.getCollectors() ) {
			Map<String, Object> cm = new LinkedHashMap<>();
			cm.put( "id", c.id() );
			cm.put( "heavy", c.heavy() );
			cols.add( cm );
		}
		m.put( "collectors", cols );
		m.put( "hiddenTabs", cfg.hiddenTabs );
		return m;
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
