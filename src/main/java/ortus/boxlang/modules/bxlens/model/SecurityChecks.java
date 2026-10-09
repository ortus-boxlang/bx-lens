/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensConfig;

/**
 * Cheap checks of the response that catch common security slips while a developer is looking: missing security headers on HTML pages and
 * cookies the browser lets scripts read. Turn off with <code>checks.securityHeaders</code>.
 */
public final class SecurityChecks {

	/**
	 * One cookie set by the response.
	 */
	public record Cookie( String name, boolean httpOnly, boolean secure, boolean sameSite ) {
	}

	private SecurityChecks() {
	}

	/**
	 * Will {@link #analyze} look at this response? Lets the caller skip reading the cookies of a response that is not checked.
	 */
	public static boolean applies( LensRequest req, LensConfig cfg ) {
		return cfg.getBool( "checks.securityHeaders", true ) && req.html && req.status >= 200 && req.status < 400;
	}

	/**
	 * Add issues for a finished HTML response.
	 *
	 * @param headers response headers, any case
	 * @param cookies cookies the response sets
	 * @param secure  was the request made over HTTPS
	 */
	public static void analyze( LensRequest req, LensConfig cfg, Map<String, ?> headers, List<Cookie> cookies, boolean secure ) {
		if ( !cfg.getBool( "checks.securityHeaders", true ) || !req.html || req.status < 200 || req.status >= 400 ) {
			return;
		}
		List<String>		lower	= new ArrayList<>();
		Map<String, String>	h		= new java.util.HashMap<>();
		headers.forEach( ( k, v ) -> h.put( k.toLowerCase( Locale.ROOT ), String.valueOf( v ) ) );
		lower.addAll( h.keySet() );

		List<String> missing = new ArrayList<>();
		if ( !h.containsKey( "content-security-policy" ) ) {
			missing.add( "Content-Security-Policy" );
		}
		if ( !h.containsKey( "x-frame-options" ) && !h.getOrDefault( "content-security-policy", "" ).contains( "frame-ancestors" ) ) {
			missing.add( "X-Frame-Options" );
		}
		if ( !h.containsKey( "x-content-type-options" ) ) {
			missing.add( "X-Content-Type-Options" );
		}
		if ( secure && !h.containsKey( "strict-transport-security" ) ) {
			missing.add( "Strict-Transport-Security" );
		}
		if ( !missing.isEmpty() ) {
			req.addIssue( "info", "Security headers missing", String.join( ", ", missing ) + ". Add them in your web server or Application.bx.", "", 0,
			    "request", 0 );
		}

		List<String> noHttpOnly = new ArrayList<>(), noSecure = new ArrayList<>(), noSameSite = new ArrayList<>();
		for ( Cookie c : cookies ) {
			if ( !c.httpOnly() ) {
				noHttpOnly.add( c.name() );
			}
			if ( secure && !c.secure() ) {
				noSecure.add( c.name() );
			}
			if ( !c.sameSite() ) {
				noSameSite.add( c.name() );
			}
		}
		List<String> parts = new ArrayList<>();
		if ( !noHttpOnly.isEmpty() ) {
			parts.add( "no HttpOnly: " + String.join( ", ", noHttpOnly ) );
		}
		if ( !noSecure.isEmpty() ) {
			parts.add( "no Secure flag: " + String.join( ", ", noSecure ) );
		}
		if ( !noSameSite.isEmpty() ) {
			parts.add( "no SameSite: " + String.join( ", ", noSameSite ) );
		}
		if ( !parts.isEmpty() ) {
			req.addIssue( "info", "Cookie flags missing", String.join( "; ", parts ), "", 0, "request", 0 );
		}
	}

}
