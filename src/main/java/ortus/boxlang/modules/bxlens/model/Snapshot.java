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
import ortus.boxlang.modules.bxlens.web.WebExchange;

/**
 * Builds the JSON-ready snapshot of a finished request. This map is the contract between the Java side and the UI.
 */
public final class Snapshot {

	private Snapshot() {
	}

	/**
	 * The full payload of one request.
	 */
	public static Map<String, Object> build( LensRequest req, LensConfig cfg, WebExchange exchange ) {
		Sanitizer			clean	= new Sanitizer( cfg );
		Map<String, Object>	m		= new LinkedHashMap<>();

		Map<String, Object>	r		= new LinkedHashMap<>();
		r.put( "id", req.id );
		r.put( "method", req.method );
		r.put( "url", req.url );
		r.put( "uri", req.uri );
		r.put( "query", req.queryString );
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
		r.put( "severity", IssueEngine.severity( req ) );
		m.put( "request", r );

		Map<String, Object> headers = new LinkedHashMap<>();
		if ( exchange != null && !cfg.light ) {
			try {
				exchange.requestHeaders().forEach( ( k, v ) -> headers.put( k, clean.cleanKeyed( k, v ) ) );
			} catch ( Throwable t ) {
				// Exchange already recycled
			}
		}
		m.put( "headers", headers );

		// Spans, with spans contributed by custom panels appended
		List<Map<String, Object>>	spans	= new ArrayList<>();
		List<Span>					copy;
		synchronized ( req.spans ) {
			copy = new ArrayList<>( req.spans );
		}
		copy.sort( Comparator.comparingLong( s -> s.startNs ) );
		for ( Span s : copy ) {
			spans.add( s.toMap() );
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

		m.put( "queries", new ArrayList<>( req.queries ) );
		m.put( "exceptions", new ArrayList<>( req.exceptions ) );
		m.put( "messages", new ArrayList<>( req.messages ) );
		m.put( "timers", new ArrayList<>( req.timers ) );
		m.put( "http", new ArrayList<>( req.http ) );
		m.put( "logs", new ArrayList<>( req.logs ) );
		m.put( "issues", new ArrayList<>( req.issues ) );

		for ( String key : List.of( "scopes", "jvm", "cache", "modules", "cost", "slowSample", "responseHeaders" ) ) {
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
		counts.put( "issues", req.countIssues() );
		m.put( "counts", counts );
		return m;
	}

	/**
	 * One-line summary for the History list.
	 */
	public static Map<String, Object> summary( LensRequest req ) {
		Map<String, Object> s = new LinkedHashMap<>();
		s.put( "id", req.id );
		s.put( "method", req.method );
		s.put( "url", req.uri + ( req.queryString.isEmpty() ? "" : "?" + req.queryString ) );
		s.put( "status", req.status );
		s.put( "type", classify( req.contentType ) );
		s.put( "ms", Span.ms( req.durationNs() ) );
		s.put( "issues", req.countIssues() );
		s.put( "severity", IssueEngine.severity( req ) );
		s.put( "queries", req.queries.size() );
		s.put( "at", req.startMillis );
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
