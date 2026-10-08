/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;

/**
 * Query statistics across requests, in memory: one entry per distinct statement (with its placeholders, so the same statement with other
 * values counts as one). Shows the slowest, the longest in total, the most executed and the ones that fail. Failures are queries that were
 * started and never finished, because core announces no query error event. Bounded: the least recently seen statement is dropped past
 * {@link #MAX_STATEMENTS}. Only the statement text is kept, never parameter values.
 */
public final class QueryStats {

	public static final int MAX_STATEMENTS = 500;

	private static final class Stat {

		String	sql;
		String	datasource;
		long	count;
		long	failures;
		double	totalMs;
		double	maxMs;
		double	minMs			= Double.MAX_VALUE;
		long	rows;
		long	slow;
		long	firstSeen;
		long	lastSeen;
		String	file			= "";
		int		line;
		String	lastError		= "";
		String	slowestRequest	= "";
		String	lastRequest		= "";
	}

	private final Map<String, Stat>	stats	= new ConcurrentHashMap<>();
	private volatile long			since	= System.currentTimeMillis();

	/**
	 * Fold the queries of a finished request into the statistics.
	 */
	@SuppressWarnings( "unchecked" )
	public void record( LensRequest req, int slowMs ) {
		try {
			Set<Object> finished = new HashSet<>();
			synchronized ( req.queries ) {
				for ( Map<String, Object> q : req.queries ) {
					finished.add( q.get( "span" ) );
					add( req, q, slowMs );
				}
			}
			Set<String> counted = new HashSet<>();
			synchronized ( req.spans ) {
				for ( Span s : req.spans ) {
					if ( Span.QUERY.equals( s.type ) && !finished.contains( s.id ) ) {
						String sql = String.valueOf( s.detail.getOrDefault( "sql", s.label ) );
						counted.add( sql.trim().replaceAll( "\\s+", " " ) );
						fail( req, sql, "The query did not finish" );
					}
				}
			}
			// A statement that cannot be prepared (unknown table, bad syntax) never fires a query event. It shows when the error reaches the request
			synchronized ( req.exceptions ) {
				for ( Map<String, Object> e : req.exceptions ) {
					Object sql = e.get( "sql" );
					if ( sql != null && !sql.toString().isBlank() && !"null".equals( sql.toString() )
					    && counted.add( sql.toString().trim().replaceAll( "\\s+", " " ) ) ) {
						fail( req, sql.toString(), String.valueOf( e.getOrDefault( "message", "" ) ) );
					}
				}
			}
		} catch ( Throwable t ) {
			// Statistics never break a request
		}
	}

	private Stat stat( String sql, String ds ) {
		String	key	= ds + "\u0000" + sql.trim().replaceAll( "\\s+", " " );
		Stat	s	= stats.get( key );
		if ( s == null ) {
			if ( stats.size() >= MAX_STATEMENTS ) {
				stats.entrySet().stream().min( Comparator.comparingLong( e -> e.getValue().lastSeen ) ).ifPresent( e -> stats.remove( e.getKey() ) );
			}
			s				= new Stat();
			s.sql			= sql.length() > 4000 ? sql.substring( 0, 4000 ) + "..." : sql;
			s.datasource	= ds;
			s.firstSeen		= System.currentTimeMillis();
			Stat prior = stats.putIfAbsent( key, s );
			if ( prior != null ) {
				s = prior;
			}
		}
		return s;
	}

	private void add( LensRequest req, Map<String, Object> q, int slowMs ) {
		String	sql	= String.valueOf( q.getOrDefault( "sql", "" ) );
		String	ds	= String.valueOf( q.getOrDefault( "datasource", "" ) );
		double	ms	= q.get( "ms" ) instanceof Number n ? n.doubleValue() : 0;
		Stat	s	= stat( sql, ds );
		synchronized ( s ) {
			s.count++;
			s.totalMs	+= ms;
			s.minMs		= Math.min( s.minMs, ms );
			if ( ms >= s.maxMs ) {
				s.maxMs				= ms;
				s.slowestRequest	= req.id;
			}
			s.rows += q.get( "rows" ) instanceof Number n ? n.longValue() : 0;
			if ( slowMs > 0 && ms >= slowMs ) {
				s.slow++;
			}
			s.lastSeen		= System.currentTimeMillis();
			s.lastRequest	= req.id;
			String file = String.valueOf( q.getOrDefault( "file", "" ) );
			if ( !file.isEmpty() && !"null".equals( file ) ) {
				s.file	= file;
				s.line	= q.get( "line" ) instanceof Number n ? n.intValue() : 0;
			}
		}
	}

	private void fail( LensRequest req, String sql, String error ) {
		Stat s = stat( sql, "" );
		synchronized ( s ) {
			s.count++;
			s.failures++;
			s.lastError		= error.length() > 500 ? error.substring( 0, 500 ) : error;
			s.lastSeen		= System.currentTimeMillis();
			s.lastRequest	= req.id;
		}
	}

	/**
	 * Forget everything.
	 */
	public void reset() {
		stats.clear();
		since = System.currentTimeMillis();
	}

	/**
	 * The statistics, with totals.
	 */
	public Map<String, Object> snapshot() {
		List<Map<String, Object>>	rows	= new ArrayList<>();
		long						count	= 0, failed = 0, slow = 0;
		double						total	= 0;
		for ( Stat s : stats.values() ) {
			synchronized ( s ) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put( "sql", s.sql );
				m.put( "datasource", s.datasource );
				m.put( "count", s.count );
				m.put( "failures", s.failures );
				m.put( "slow", s.slow );
				long ok = s.count - s.failures;
				m.put( "avgMs", ok == 0 ? 0 : round( s.totalMs / ok ) );
				m.put( "maxMs", round( s.maxMs ) );
				m.put( "minMs", s.minMs == Double.MAX_VALUE ? 0 : round( s.minMs ) );
				m.put( "totalMs", round( s.totalMs ) );
				m.put( "rows", s.rows );
				m.put( "firstSeen", s.firstSeen );
				m.put( "lastSeen", s.lastSeen );
				m.put( "file", s.file );
				m.put( "line", s.line );
				m.put( "lastError", s.lastError );
				m.put( "slowestRequest", s.slowestRequest );
				m.put( "lastRequest", s.lastRequest );
				rows.add( m );
				count	+= s.count;
				failed	+= s.failures;
				slow	+= s.slow;
				total	+= s.totalMs;
			}
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "statements", rows );
		out.put( "executed", count );
		out.put( "failed", failed );
		out.put( "slow", slow );
		out.put( "totalMs", round( total ) );
		out.put( "avgMs", count - failed == 0 ? 0 : round( total / ( count - failed ) ) );
		out.put( "since", since );
		out.put( "max", MAX_STATEMENTS );
		return out;
	}

	private static double round( double v ) {
		return Math.round( v * 100.0 ) / 100.0;
	}

}
