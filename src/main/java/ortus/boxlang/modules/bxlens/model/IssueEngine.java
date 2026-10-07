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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;

/**
 * Turns raw request data into issues: exceptions, N+1 and duplicate queries, slow queries, slow templates, slow requests and error statuses.
 * Runs once when the request finishes. Flags the matching spans so the waterfall can show them.
 */
public final class IssueEngine {

	private IssueEngine() {
	}

	/**
	 * Analyze a finished request and fill {@link LensRequest#issues}.
	 */
	@SuppressWarnings( "unchecked" )
	public static void analyze( LensRequest req, LensConfig cfg ) {
		// Exceptions
		synchronized ( req.exceptions ) {
			for ( Map<String, Object> ex : req.exceptions ) {
				String	file	= String.valueOf( ex.getOrDefault( "file", "" ) );
				int		line	= ex.get( "line" ) instanceof Number n ? n.intValue() : 0;
				req.addIssue( "crit", String.valueOf( ex.getOrDefault( "type", "Exception" ) ), String.valueOf( ex.getOrDefault( "message", "" ) ), file, line,
				    "exceptions", 0 );
			}
		}

		// Queries: group by normalized SQL
		Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
		synchronized ( req.queries ) {
			for ( Map<String, Object> q : req.queries ) {
				groups.computeIfAbsent( normalize( String.valueOf( q.get( "sql" ) ) ), k -> new ArrayList<>() ).add( q );
			}
		}
		for ( List<Map<String, Object>> group : groups.values() ) {
			int count = group.size();
			for ( Map<String, Object> q : group ) {
				q.put( "count", count );
			}
			if ( count >= cfg.nPlusOneMin ) {
				double total = 0;
				for ( Map<String, Object> q : group ) {
					total += q.get( "ms" ) instanceof Number n ? n.doubleValue() : 0;
				}
				Map<String, Object>	first	= group.get( 0 );
				int					spanId	= first.get( "span" ) instanceof Number n ? n.intValue() : 0;
				for ( Map<String, Object> q : group ) {
					q.put( "flag", List.of( "warn", "N+1" ) );
					Span s = spanById( req, q.get( "span" ) );
					if ( s != null ) {
						s.flag( "warn", "N+1" );
					}
				}
				req.addIssue( "warn", "N+1 query pattern",
				    "Ran " + count + " times (" + Math.round( total * 10 ) / 10.0 + " ms total): " + truncate( String.valueOf( first.get( "sql" ) ), 120 ),
				    String.valueOf( first.getOrDefault( "file", "" ) ), first.get( "line" ) instanceof Number n ? n.intValue() : 0, "queries", spanId );
			}
		}
		synchronized ( req.queries ) {
			for ( Map<String, Object> q : req.queries ) {
				double ms = q.get( "ms" ) instanceof Number n ? n.doubleValue() : 0;
				if ( cfg.slowQueryMs > 0 && ms >= cfg.slowQueryMs ) {
					String sev = ms >= cfg.slowQueryMs * 4.0 ? "crit" : "warn";
					q.put( "flag", List.of( sev, "Slow" ) );
					Span s = spanById( req, q.get( "span" ) );
					if ( s != null ) {
						s.flag( sev, "Slow" );
					}
					req.addIssue( sev, "Slow query",
					    Math.round( ms * 10 ) / 10.0 + " ms, limit is " + cfg.slowQueryMs + " ms: " + truncate( String.valueOf( q.get( "sql" ) ), 120 ),
					    String.valueOf( q.getOrDefault( "file", "" ) ), q.get( "line" ) instanceof Number n ? n.intValue() : 0, "queries",
					    q.get( "span" ) instanceof Number n ? n.intValue() : 0 );
				}
			}
		}

		// Templates and functions: flag by self time so a slow child does not blame every parent
		if ( cfg.slowTemplateMs > 0 ) {
			List<Span> snapshot;
			synchronized ( req.spans ) {
				snapshot = new ArrayList<>( req.spans );
			}
			for ( Span s : snapshot ) {
				if ( !Span.TEMPLATE.equals( s.type ) && !Span.FUNCTION.equals( s.type ) ) {
					continue;
				}
				long childNs = 0;
				for ( Span c : snapshot ) {
					if ( c.depth == s.depth + 1 && c.startNs >= s.startNs && c.endNs <= s.endNs ) {
						childNs += c.durationNs();
					}
				}
				double selfMs = Span.ms( Math.max( 0, s.durationNs() - childNs ) );
				if ( selfMs >= cfg.slowTemplateMs ) {
					s.flag( "warn", "Slow" );
					req.addIssue( "warn", "Slow " + ( Span.TEMPLATE.equals( s.type ) ? "template" : "function" ),
					    s.label + " spent " + selfMs + " ms of its own time (the first run of a template includes compilation)",
					    s.file, s.line, "timeline", s.id );
				}
			}
		}

		// Request level
		if ( req.status >= 500 ) {
			req.addIssue( "crit", "HTTP " + req.status, req.method + " " + req.uri, "", 0, "request", 0 );
		} else if ( req.status >= 400 ) {
			req.addIssue( "warn", "HTTP " + req.status, req.method + " " + req.uri, "", 0, "request", 0 );
		}
		double totalMs = Span.ms( req.durationNs() );
		if ( cfg.slowRequestMs > 0 && totalMs >= cfg.slowRequestMs ) {
			req.addIssue( "warn", "Slow request", totalMs + " ms, limit is " + cfg.slowRequestMs + " ms", "", 0, "timeline", 0 );
		}

		// Issues reported by panels
		synchronized ( req.panels ) {
			for ( LensPanelBuilder p : req.panels.values() ) {
				for ( Map<String, Object> i : p.getIssues() ) {
					Map<String, Object> copy = new LinkedHashMap<>( i );
					copy.put( "tab", p.getId() );
					copy.put( "span", 0 );
					req.issues.add( copy );
				}
			}
		}
	}

	/**
	 * Highest severity among issues: crit, warn or none.
	 */
	public static String severity( LensRequest req ) {
		boolean warn = false;
		synchronized ( req.issues ) {
			for ( Map<String, Object> i : req.issues ) {
				if ( "crit".equals( i.get( "severity" ) ) ) {
					return "crit";
				}
				warn = true;
			}
		}
		return warn ? "warn" : "none";
	}

	/**
	 * Collapse whitespace and case so equivalent statements group together.
	 */
	public static String normalize( String sql ) {
		return sql == null ? "" : sql.trim().replaceAll( "\\s+", " " ).toLowerCase( Locale.ROOT );
	}

	private static Span spanById( LensRequest req, Object id ) {
		if ( ! ( id instanceof Number n ) ) {
			return null;
		}
		synchronized ( req.spans ) {
			for ( Span s : req.spans ) {
				if ( s.id == n.intValue() ) {
					return s;
				}
			}
		}
		return null;
	}

	private static String truncate( String s, int max ) {
		return s.length() > max ? s.substring( 0, max ) + "..." : s;
	}

}
