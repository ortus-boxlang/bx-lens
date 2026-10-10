/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.bxlens.util.Bounded;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.modules.bxlens.util.Text;

/**
 * What the ORM events added up to since Lens started listening, per ORM application and datasource. Every <code>onORMQuery</code> and
 * <code>onORMFlush</code> counts here, also the ones that ran outside a request (startup DDL, a task) and so show in no request. Bounded: 100
 * application and datasource pairs (the least recently seen goes first) and the last 20 failures. Statement text is masked, parameter values are
 * never stored.
 */
public final class OrmTotals {

	public static final int	MAX_PAIRS		= 100;
	public static final int	MAX_FAILURES	= 20;

	private static final class Counts {

		final String	app;
		final String	datasource;
		long			queries, selects, inserts, updates, deletes, ddl, other, errors, rows, unattributed;
		long			nanos, maxNanos;
		String			slowest		= "";
		long			flushes, flushInserts, flushUpdates, flushDeletes, flushNanos, maxFlushNanos;
		long			exceptions;
		volatile long	lastSeen	= System.nanoTime();

		Counts( String app, String datasource ) {
			this.app		= app;
			this.datasource	= datasource;
		}
	}

	private final Map<String, Counts>				pairs		= new ConcurrentHashMap<>();
	private final Bounded							bounded		= new Bounded();
	private final ArrayDeque<Map<String, Object>>	failures	= new ArrayDeque<>();
	private volatile long							since		= System.currentTimeMillis();

	private Counts counts( String app, String ds ) {
		String	a	= app == null ? "" : app;
		String	d	= ds == null ? "" : ds;
		String	key	= a + "\u0000" + d;
		Counts	c	= this.pairs.get( key );
		if ( c == null ) {
			c = this.pairs.computeIfAbsent( key, k -> new Counts( a, d ) );
			this.bounded.trim( this.pairs, MAX_PAIRS, x -> x.lastSeen );
		}
		c.lastSeen = System.nanoTime();
		return c;
	}

	/**
	 * Count one statement.
	 *
	 * @param kind       select, insert, update, delete, ddl or other
	 * @param attributed did it show in a request
	 */
	public void query( String app, String ds, String kind, long nanos, long rows, boolean failed, boolean attributed, String sql ) {
		Counts c = counts( app, ds );
		synchronized ( c ) {
			c.queries++;
			switch ( kind == null ? "other" : kind ) {
				case "select" -> c.selects++;
				case "insert" -> c.inserts++;
				case "update" -> c.updates++;
				case "delete" -> c.deletes++;
				case "ddl" -> c.ddl++;
				default -> c.other++;
			}
			if ( failed ) {
				c.errors++;
			}
			if ( rows > 0 ) {
				c.rows += rows;
			}
			if ( !attributed ) {
				c.unattributed++;
			}
			c.nanos += Math.max( 0, nanos );
			if ( nanos >= c.maxNanos ) {
				c.maxNanos	= nanos;
				c.slowest	= sql == null ? "" : cut( Text.maskSql( Text.collapseSpaces( sql ) ), 1000 );
			}
		}
	}

	/** Count one Hibernate flush. */
	public void flush( String app, String ds, long inserts, long updates, long deletes, long nanos ) {
		Counts c = counts( app, ds );
		synchronized ( c ) {
			c.flushes++;
			c.flushInserts	+= Math.max( 0, inserts );
			c.flushUpdates	+= Math.max( 0, updates );
			c.flushDeletes	+= Math.max( 0, deletes );
			c.flushNanos	+= Math.max( 0, nanos );
			c.maxFlushNanos	= Math.max( c.maxFlushNanos, nanos );
		}
	}

	/** Remember a failed statement: when, where and the message, with the statement masked. */
	public void exception( String app, String ds, String sql, String message ) {
		Counts c = counts( app, ds );
		synchronized ( c ) {
			c.exceptions++;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "at", System.currentTimeMillis() );
		m.put( "app", app == null ? "" : app );
		m.put( "datasource", ds == null ? "" : ds );
		m.put( "sql", sql == null ? "" : cut( Text.maskSql( Text.collapseSpaces( sql ) ), 1000 ) );
		m.put( "error", cut( Text.maskSql( Secrets.text( message == null ? "" : message ) ), 500 ) );
		synchronized ( this.failures ) {
			this.failures.addFirst( m );
			while ( this.failures.size() > MAX_FAILURES ) {
				this.failures.removeLast();
			}
		}
	}

	/** Forget everything. */
	public void reset() {
		this.pairs.clear();
		synchronized ( this.failures ) {
			this.failures.clear();
		}
		this.since = System.currentTimeMillis();
	}

	/** The totals, one row per application and datasource, most recently seen first. */
	public Map<String, Object> snapshot() {
		List<Counts> all = new ArrayList<>( this.pairs.values() );
		all.sort( Comparator.comparingLong( ( Counts c ) -> c.lastSeen ).reversed() );
		List<Map<String, Object>>	rows	= new ArrayList<>();
		long						queries	= 0, flushes = 0, errors = 0, unattributed = 0;
		for ( Counts c : all ) {
			synchronized ( c ) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put( "app", c.app );
				m.put( "datasource", c.datasource );
				m.put( "queries", c.queries );
				m.put( "selects", c.selects );
				m.put( "inserts", c.inserts );
				m.put( "updates", c.updates );
				m.put( "deletes", c.deletes );
				m.put( "ddl", c.ddl );
				m.put( "other", c.other );
				m.put( "errors", c.errors );
				m.put( "rows", c.rows );
				m.put( "unattributed", c.unattributed );
				m.put( "totalMs", Math.round( c.nanos / 1000.0 ) / 1000.0 );
				m.put( "maxMs", Math.round( c.maxNanos / 1000.0 ) / 1000.0 );
				m.put( "slowest", c.slowest );
				m.put( "flushes", c.flushes );
				m.put( "flushInserts", c.flushInserts );
				m.put( "flushUpdates", c.flushUpdates );
				m.put( "flushDeletes", c.flushDeletes );
				m.put( "flushMs", Math.round( c.flushNanos / 1000.0 ) / 1000.0 );
				m.put( "maxFlushMs", Math.round( c.maxFlushNanos / 1000.0 ) / 1000.0 );
				m.put( "exceptions", c.exceptions );
				rows.add( m );
				queries			+= c.queries;
				flushes			+= c.flushes;
				errors			+= c.errors;
				unattributed	+= c.unattributed;
			}
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "datasources", rows );
		out.put( "queries", queries );
		out.put( "flushes", flushes );
		out.put( "errors", errors );
		out.put( "unattributed", unattributed );
		out.put( "since", this.since );
		synchronized ( this.failures ) {
			out.put( "failures", new ArrayList<>( this.failures ) );
		}
		return out;
	}

	private static String cut( String s, int max ) {
		return s.length() > max ? s.substring( 0, max ) + "..." : s;
	}

}
