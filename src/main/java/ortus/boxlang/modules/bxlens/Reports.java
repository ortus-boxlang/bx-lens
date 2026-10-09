/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Bounded;
import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Text;

/**
 * Totals for the whole server: requests, errors, status classes, latency, queries, the busiest, slowest and most failing URLs, and a minute by
 * minute series. All counters are atomic, so recording never blocks a request. Held in memory. With the Plus disk store the state is saved
 * atomically (temp file, then rename) so the totals "since first install" survive restarts and upgrades.
 */
public final class Reports {

	private static final long[]	BUCKETS		= { 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000 };
	private static final int	MAX_URLS	= 300;

	/** One URL. */
	private static final class Url {

		final LongAdder		count	= new LongAdder();
		final LongAdder		errors	= new LongAdder();
		final LongAdder		totalMs	= new LongAdder();
		final AtomicLong	maxMs	= new AtomicLong();
		volatile long		lastSeen;
	}

	private final long													startedAt		= System.currentTimeMillis();
	private volatile long												firstInstall	= startedAt;
	private final LongAdder												requests		= new LongAdder();
	private final LongAdder												errors			= new LongAdder();
	private final LongAdder												slow			= new LongAdder();
	private final LongAdder												totalMs			= new LongAdder();
	private final AtomicLong											maxMs			= new AtomicLong();
	private final LongAdder												queries			= new LongAdder();
	private final LongAdder												queryMs			= new LongAdder();
	private final LongAdder												httpCalls		= new LongAdder();
	private final LongAdder												exceptions		= new LongAdder();
	private final AtomicLongArray										status			= new AtomicLongArray( 6 );
	private final AtomicLongArray										latency			= new AtomicLongArray( BUCKETS.length + 1 );
	private final Map<String, Url>										urls			= new ConcurrentHashMap<>();
	private final Bounded												bounded			= new Bounded();
	/** minute (epoch minutes) to { requests, errors, totalMs }. */
	private final ConcurrentSkipListMap<Long, long[]>					minutes			= new ConcurrentSkipListMap<>();
	private volatile long												baseRequests, baseErrors, baseSlow, baseQueries, baseExceptions, baseRuns;
	private volatile boolean											dirty;
	private volatile int												keepMinutes		= 60;
	/** Which server these totals belong to. */
	private volatile java.util.function.Supplier<Map<String, Object>>	serverSource	= Map::of;

	/**
	 * Where the identity of this server comes from, so the totals say which machine they describe.
	 */
	public void identify( java.util.function.Supplier<Map<String, Object>> source ) {
		this.serverSource = source;
	}

	/**
	 * How many minutes of the minute series to keep. 60 in memory only, more with the disk store.
	 */
	public void keepMinutes( int minutes ) {
		this.keepMinutes = Math.max( 10, minutes );
	}

	/**
	 * Count one finished request.
	 */
	public void record( LensRequest req, int slowRequestMs ) {
		try {
			long	ms		= Math.round( req.durationNs() / 1_000_000.0 );
			boolean	failed	= req.status >= 500;
			synchronized ( req.exceptions ) {
				for ( Map<String, Object> e : req.exceptions ) {
					if ( "uncaught".equals( e.get( "origin" ) ) ) {
						failed = true;
					}
				}
			}
			requests.increment();
			totalMs.add( ms );
			maxMs.accumulateAndGet( ms, Math::max );
			status.incrementAndGet( Math.max( 0, Math.min( 5, req.status / 100 ) ) );
			if ( failed ) {
				errors.increment();
			}
			if ( slowRequestMs > 0 && ms >= slowRequestMs ) {
				slow.increment();
			}
			int b = 0;
			while ( b < BUCKETS.length && ms > BUCKETS[ b ] ) {
				b++;
			}
			latency.incrementAndGet( b );
			double qms = 0;
			synchronized ( req.queries ) {
				queries.add( req.queries.size() );
				for ( Map<String, Object> q : req.queries ) {
					qms += q.get( "ms" ) instanceof Number n ? n.doubleValue() : 0;
				}
			}
			queryMs.add( Math.round( qms ) );
			httpCalls.add( req.http.size() );
			exceptions.add( req.exceptions.size() );
			url( req, ms, failed );
			long[] m = minutes.computeIfAbsent( System.currentTimeMillis() / 60_000L, k -> new long[ 3 ] );
			synchronized ( m ) {
				m[ 0 ]++;
				m[ 1 ]	+= failed ? 1 : 0;
				m[ 2 ]	+= ms;
			}
			long cut = System.currentTimeMillis() / 60_000L - keepMinutes;
			while ( !minutes.isEmpty() && minutes.firstKey() < cut ) {
				minutes.pollFirstEntry();
			}
			dirty = true;
		} catch ( Throwable t ) {
			// Reports never break a request
		}
	}

	private void url( LensRequest req, long ms, boolean failed ) {
		// Numbers and ids in the path are one URL, so /orders/1 and /orders/2 do not fill the table
		String	key	= req.method + " " + Text.collapsePath( req.uri );
		Url		u	= urls.get( key );
		if ( u == null ) {
			u			= new Url();
			u.lastSeen	= System.currentTimeMillis();
			Url prior = urls.putIfAbsent( key, u );
			if ( prior != null ) {
				u = prior;
			} else {
				bounded.trim( urls, MAX_URLS, x -> x.lastSeen );
			}
		}
		u.count.increment();
		u.totalMs.add( ms );
		u.maxMs.accumulateAndGet( ms, Math::max );
		u.lastSeen = System.currentTimeMillis();
		if ( failed ) {
			u.errors.increment();
		}
	}

	private long percentile( double p ) {
		long total = 0;
		for ( int i = 0; i < latency.length(); i++ ) {
			total += latency.get( i );
		}
		if ( total == 0 ) {
			return 0;
		}
		long need = ( long ) Math.ceil( total * p ), seen = 0;
		for ( int i = 0; i < latency.length(); i++ ) {
			seen += latency.get( i );
			if ( seen >= need ) {
				return i < BUCKETS.length ? BUCKETS[ i ] : maxMs.get();
			}
		}
		return maxMs.get();
	}

	/**
	 * Everything for the Reports page.
	 *
	 * @param persisted is the disk store on, so the lifetime totals are real
	 */
	public Map<String, Object> snapshot( boolean persisted ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "startedAt", startedAt );
		m.put( "uptimeMs", System.currentTimeMillis() - startedAt );
		m.put( "persisted", persisted );
		m.put( "server", this.serverSource.get() );
		long				reqs	= requests.sum();
		Map<String, Object>	now		= new LinkedHashMap<>();
		now.put( "requests", reqs );
		now.put( "errors", errors.sum() );
		now.put( "slow", slow.sum() );
		now.put( "queries", queries.sum() );
		now.put( "queryMs", queryMs.sum() );
		now.put( "httpCalls", httpCalls.sum() );
		now.put( "exceptions", exceptions.sum() );
		now.put( "avgMs", reqs == 0 ? 0 : Math.round( totalMs.sum() * 10.0 / reqs ) / 10.0 );
		now.put( "maxMs", maxMs.get() );
		now.put( "p50", percentile( 0.50 ) );
		now.put( "p95", percentile( 0.95 ) );
		now.put( "p99", percentile( 0.99 ) );
		Map<String, Object> st = new LinkedHashMap<>();
		for ( int i = 1; i <= 5; i++ ) {
			st.put( i + "xx", status.get( i ) );
		}
		now.put( "status", st );
		m.put( "session", now );
		if ( persisted ) {
			Map<String, Object> life = new LinkedHashMap<>();
			life.put( "since", firstInstall );
			life.put( "runs", baseRuns + 1 );
			life.put( "requests", baseRequests + reqs );
			life.put( "errors", baseErrors + errors.sum() );
			life.put( "slow", baseSlow + slow.sum() );
			life.put( "queries", baseQueries + queries.sum() );
			life.put( "exceptions", baseExceptions + exceptions.sum() );
			m.put( "lifetime", life );
		}
		List<Map<String, Object>> series = new ArrayList<>();
		for ( Map.Entry<Long, long[]> e : minutes.entrySet() ) {
			long[] v = e.getValue();
			synchronized ( v ) {
				Map<String, Object> p = new LinkedHashMap<>();
				p.put( "t", e.getKey() * 60_000L );
				p.put( "requests", v[ 0 ] );
				p.put( "errors", v[ 1 ] );
				p.put( "avgMs", v[ 0 ] == 0 ? 0 : Math.round( v[ 2 ] * 10.0 / v[ 0 ] ) / 10.0 );
				series.add( p );
			}
		}
		m.put( "series", series );
		m.put( "seriesMinutes", keepMinutes );
		m.put( "slowestUrls", top( Comparator.comparingLong( ( Url u ) -> u.maxMs.get() ).reversed() ) );
		m.put( "busiestUrls", top( Comparator.comparingLong( ( Url u ) -> u.count.sum() ).reversed() ) );
		m.put( "failingUrls", top( Comparator.comparingLong( ( Url u ) -> u.errors.sum() ).reversed() ) );
		return m;
	}

	private List<Map<String, Object>> top( Comparator<Url> order ) {
		List<Map<String, Object>> out = new ArrayList<>();
		urls.entrySet().stream().sorted( ( a, b ) -> order.compare( a.getValue(), b.getValue() ) ).limit( 10 ).forEach( e -> {
			Url					u	= e.getValue();
			Map<String, Object>	x	= new LinkedHashMap<>();
			x.put( "url", e.getKey() );
			x.put( "count", u.count.sum() );
			x.put( "errors", u.errors.sum() );
			x.put( "avgMs", u.count.sum() == 0 ? 0 : Math.round( u.totalMs.sum() * 10.0 / u.count.sum() ) / 10.0 );
			x.put( "maxMs", u.maxMs.get() );
			out.add( x );
		} );
		return out;
	}

	public void reset() {
		requests.reset();
		errors.reset();
		slow.reset();
		totalMs.reset();
		maxMs.set( 0 );
		queries.reset();
		queryMs.reset();
		httpCalls.reset();
		exceptions.reset();
		for ( int i = 0; i < status.length(); i++ ) {
			status.set( i, 0 );
		}
		for ( int i = 0; i < latency.length(); i++ ) {
			latency.set( i, 0 );
		}
		urls.clear();
		minutes.clear();
		dirty = true;
	}

	// ---- persistence for the Plus disk store ----

	public boolean isDirty() {
		return dirty;
	}

	/**
	 * What to save. Lifetime totals include earlier runs.
	 */
	public Map<String, Object> toPersist() {
		dirty = false;
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "server", this.serverSource.get() );
		m.put( "firstInstall", firstInstall );
		m.put( "runs", baseRuns + 1 );
		m.put( "requests", baseRequests + requests.sum() );
		m.put( "errors", baseErrors + errors.sum() );
		m.put( "slow", baseSlow + slow.sum() );
		m.put( "queries", baseQueries + queries.sum() );
		m.put( "exceptions", baseExceptions + exceptions.sum() );
		List<List<Long>> mins = new ArrayList<>();
		for ( Map.Entry<Long, long[]> e : minutes.entrySet() ) {
			long[] v = e.getValue();
			synchronized ( v ) {
				mins.add( List.of( e.getKey(), v[ 0 ], v[ 1 ], v[ 2 ] ) );
			}
		}
		m.put( "minutes", mins );
		return m;
	}

	/**
	 * Read saved state at startup: the lifetime totals and the minute series that is still in range.
	 */
	public void load( Map<String, Object> saved ) {
		if ( saved == null || saved.isEmpty() ) {
			return;
		}
		firstInstall	= Plain.num( saved.get( "firstInstall" ), startedAt );
		baseRuns		= Plain.num( saved.get( "runs" ), 0 );
		baseRequests	= Plain.num( saved.get( "requests" ), 0 );
		baseErrors		= Plain.num( saved.get( "errors" ), 0 );
		baseSlow		= Plain.num( saved.get( "slow" ), 0 );
		baseQueries		= Plain.num( saved.get( "queries" ), 0 );
		baseExceptions	= Plain.num( saved.get( "exceptions" ), 0 );
		long cut = System.currentTimeMillis() / 60_000L - keepMinutes;
		for ( Object o : Plain.list( saved.get( "minutes" ) ) ) {
			List<Object> v = Plain.list( o );
			if ( v.size() == 4 && Plain.num( v.get( 0 ), 0 ) >= cut ) {
				minutes.put( Plain.num( v.get( 0 ), 0 ), new long[] { Plain.num( v.get( 1 ), 0 ), Plain.num( v.get( 2 ), 0 ), Plain.num( v.get( 3 ), 0 ) } );
			}
		}
	}

}
