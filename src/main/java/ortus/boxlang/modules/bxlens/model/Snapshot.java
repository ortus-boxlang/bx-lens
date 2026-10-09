/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.modules.bxlens.util.Secrets;

/**
 * Builds the JSON-ready snapshot of a finished request. This map is the contract between the Java side and the UI.
 */
public final class Snapshot {

	private Snapshot() {
	}

	/**
	 * The full payload of one request. Built on demand, when the bar is rendered or an API route asks for it, from the finished request.
	 */
	public static Map<String, Object> build( LensRequest req, LensConfig cfg ) {
		return build( req, cfg, false );
	}

	/**
	 * The payload of one request.
	 *
	 * @param console true for the console, which also gets the issues and the flags of the issue engine. The bar shows what happened in the
	 *                request and nothing the issue engine decided, so for the bar there are no issues, no flags and no N+1 counts.
	 */
	public static Map<String, Object> build( LensRequest req, LensConfig cfg, boolean console ) {
		Sanitizer			clean	= new Sanitizer( cfg );
		Map<String, Object>	m		= new LinkedHashMap<>();

		Map<String, Object>	r		= new LinkedHashMap<>();
		r.put( "id", req.id );
		r.put( "method", req.method );
		r.put( "url", req.url );
		r.put( "uri", req.uri );
		r.put( "query", Secrets.redactQuery( req.queryString, cfg, 2000 ) );
		r.put( "status", req.status );
		r.put( "contentType", req.contentType );
		r.put( "type", classify( req.contentType ) );
		r.put( "remoteAddr", req.remoteAddr );
		r.put( "host", req.host );
		r.put( "userAgent", clean.text( req.userAgent ) );
		r.put( "app", req.appName );
		r.put( "template", req.template );
		r.put( "startedAt", Instant.ofEpochMilli( req.startMillis ).toString() );
		r.put( "durationMs", Span.ms( req.durationNs() ) );
		r.put( "severity", console ? IssueEngine.severity( req ) : barState( req ) );
		r.put( "state", req.unfinished ? "unfinished" : barState( req ) );
		m.put( "request", r );

		Map<String, Object>	headers	= new LinkedHashMap<>();
		Map<String, String>	raw		= req.requestHeaders;
		if ( raw != null && !cfg.light ) {
			raw.forEach( ( k, v ) -> headers.put( k, clean.cleanKeyed( k, v ) ) );
		}
		m.put( "headers", headers );
		Map<String, String> rawOut = req.responseHeaders;
		if ( rawOut != null ) {
			Map<String, Object> rh = new LinkedHashMap<>();
			rawOut.forEach( ( k, v ) -> rh.put( k, clean.cleanKeyed( k, v ) ) );
			m.put( "responseHeaders", rh );
		}

		// Spans, with spans contributed by custom panels appended
		List<Map<String, Object>>	spans	= new ArrayList<>();
		List<Span>					copy;
		synchronized ( req.spans ) {
			copy = new ArrayList<>( req.spans );
		}
		copy.sort( Comparator.comparingLong( s -> s.startNs ) );
		for ( Span s : copy ) {
			spans.add( s.toMap( console ) );
		}
		int extra = 100000;
		synchronized ( req.panels ) {
			for ( LensPanelBuilder p : req.panels.values() ) {
				Object ps = p.toMap().get( "content" ) instanceof Map<?, ?> c ? c.get( "spans" ) : null;
				if ( ps instanceof List<?> list ) {
					for ( Object o : list ) {
						if ( o instanceof Map<?, ?> sm ) {
							Map<String, Object> s = new LinkedHashMap<>();
							s.put( "id", extra++ );
							s.put( "type", sm.get( "type" ) == null ? "custom" : String.valueOf( sm.get( "type" ) ) );
							s.put( "label", String.valueOf( sm.get( "label" ) ) );
							s.put( "start", num( sm.get( "start" ) ) );
							s.put( "dur", num( sm.get( "dur" ) ) );
							s.put( "depth", 0 );
							s.put( "file", "" );
							s.put( "line", 0 );
							s.put( "panel", p.getId() );
							spans.add( s );
						}
					}
				}
			}
		}
		m.put( "spans", spans );

		if ( console ) {
			m.put( "queries", new ArrayList<>( req.queries ) );
		} else {
			// Without what the issue engine added: the bar says what ran, not what was wrong with it
			List<Map<String, Object>> qs = new ArrayList<>();
			synchronized ( req.queries ) {
				for ( Map<String, Object> q : req.queries ) {
					Map<String, Object> c = new LinkedHashMap<>( q );
					c.remove( "flag" );
					c.remove( "count" );
					qs.add( c );
				}
			}
			m.put( "queries", qs );
		}
		m.put( "exceptions", new ArrayList<>( req.exceptions ) );
		m.put( "messages", new ArrayList<>( req.messages ) );
		m.put( "timers", new ArrayList<>( req.timers ) );
		m.put( "http", new ArrayList<>( req.http ) );
		m.put( "logs", new ArrayList<>( req.logs ) );
		if ( console ) {
			m.put( "issues", new ArrayList<>( req.issues ) );
		}

		for ( String key : List.of( "scopes", "runtime", "bifs", "cost", "slowSample" ) ) {
			Object v = req.data.get( key );
			if ( v != null ) {
				m.put( key, v );
			}
		}

		List<Map<String, Object>> panels = new ArrayList<>();
		synchronized ( req.panels ) {
			for ( LensPanelBuilder p : req.panels.values() ) {
				panels.add( p.toMap() );
			}
		}
		panels.sort( Comparator.comparingInt( p -> ( ( Number ) p.get( "order" ) ).intValue() ) );
		m.put( "panels", panels );

		Map<String, Object> counts = new LinkedHashMap<>();
		counts.put( "queries", req.queries.size() );
		counts.put( "templates", countSpans( copy, Span.TEMPLATE ) );
		counts.put( "functions", countSpans( copy, Span.FUNCTION ) );
		counts.put( "http", req.http.size() );
		counts.put( "exceptions", req.exceptions.size() );
		counts.put( "messages", req.messages.size() );
		counts.put( "timers", req.timers.size() );
		counts.put( "logs", req.logs.size() );
		if ( console ) {
			counts.put( "issues", req.countIssues() );
		}
		m.put( "counts", counts );
		return m;
	}

	/**
	 * What colors the strip of the bar: red for a server error or an exception nothing caught, else none. No issue engine is involved.
	 */
	public static String barState( LensRequest req ) {
		if ( req.status >= 500 ) {
			return "crit";
		}
		synchronized ( req.exceptions ) {
			for ( Map<String, Object> e : req.exceptions ) {
				if ( "uncaught".equals( e.get( "origin" ) ) ) {
					return "crit";
				}
			}
		}
		return "none";
	}

	/**
	 * One-line summary for the console request list.
	 */
	public static Map<String, Object> summary( LensRequest req, LensConfig cfg ) {
		Map<String, Object> s = new LinkedHashMap<>();
		s.put( "id", req.id );
		s.put( "method", req.method );
		s.put( "url", req.uri + ( req.queryString.isEmpty() ? "" : "?" + Secrets.redactQuery( req.queryString, cfg, 500 ) ) );
		s.put( "status", req.status );
		s.put( "type", classify( req.contentType ) );
		s.put( "ms", Span.ms( req.durationNs() ) );
		s.put( "queries", req.queries.size() );
		s.put( "at", req.startMillis );
		s.put( "state", req.unfinished ? "unfinished" : barState( req ) );
		return s;
	}

	/**
	 * The list row with what the issue engine found. Call it after the analysis.
	 */
	public static Map<String, Object> summaryWithIssues( LensRequest req, LensConfig cfg ) {
		Map<String, Object> s = summary( req, cfg );
		s.put( "issues", req.countIssues() );
		s.put( "severity", IssueEngine.severity( req ) );
		return s;
	}

	/**
	 * Short type name for a content type: html, json, sse, xml, text, js, css, image or other.
	 */
	public static String classify( String contentType ) {
		if ( contentType == null || contentType.isEmpty() ) {
			return "other";
		}
		String ct = contentType.toLowerCase( Locale.ROOT );
		if ( ct.startsWith( "text/html" ) ) {
			return "html";
		} else if ( ct.contains( "json" ) ) {
			return "json";
		} else if ( ct.startsWith( "text/event-stream" ) ) {
			return "sse";
		} else if ( ct.contains( "xml" ) ) {
			return "xml";
		} else if ( ct.contains( "javascript" ) ) {
			return "js";
		} else if ( ct.startsWith( "text/css" ) ) {
			return "css";
		} else if ( ct.startsWith( "image/" ) ) {
			return "image";
		} else if ( ct.startsWith( "text/" ) ) {
			return "text";
		}
		return "other";
	}

	private static int countSpans( List<Span> spans, String type ) {
		int n = 0;
		for ( Span s : spans ) {
			if ( s.type.equals( type ) ) {
				n++;
			}
		}
		return n;
	}

	private static double num( Object o ) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

}
