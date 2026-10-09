/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.jdbc.DataSource;
import ortus.boxlang.runtime.scopes.Key;

/**
 * Datasource pools for the console, read from the BoxLang datasource service and each Hikari pool. Pool timings (how long a caller waits
 * for a connection, how long a connection is held, how long it takes to open one, timeouts) come from a small Hikari metrics tracker that Lens
 * attaches to a pool that has none. A pool that already has a tracker (yours) is left alone.
 */
public final class DatasourceData {

	/**
	 * Counters for one pool. Cheap: a few adders, no histograms.
	 */
	static final class Tracker implements IMetricsTracker {

		final LongAdder		acquireCount	= new LongAdder();
		final LongAdder		acquireNanos	= new LongAdder();
		final AtomicLong	acquireMaxNanos	= new AtomicLong();
		final LongAdder		usageCount		= new LongAdder();
		final LongAdder		usageMillis		= new LongAdder();
		final AtomicLong	usageMaxMillis	= new AtomicLong();
		final LongAdder		createCount		= new LongAdder();
		final LongAdder		createMillis	= new LongAdder();
		final LongAdder		timeouts		= new LongAdder();
		final long			since			= System.currentTimeMillis();

		@Override
		public void recordConnectionAcquiredNanos( long n ) {
			acquireCount.increment();
			acquireNanos.add( n );
			acquireMaxNanos.accumulateAndGet( n, Math::max );
		}

		@Override
		public void recordConnectionUsageMillis( long ms ) {
			usageCount.increment();
			usageMillis.add( ms );
			usageMaxMillis.accumulateAndGet( ms, Math::max );
		}

		@Override
		public void recordConnectionCreatedMillis( long ms ) {
			createCount.increment();
			createMillis.add( ms );
		}

		@Override
		public void recordConnectionTimeout() {
			timeouts.increment();
		}
	}

	private final Map<String, Tracker>											trackers	= new ConcurrentHashMap<>();
	private final Set<String>													attached	= java.util.Collections.newSetFromMap( new ConcurrentHashMap<>() );
	/** The pools Lens put its tracker on, so it can take it off again when the module stops. */
	private final Map<String, java.lang.ref.WeakReference<HikariDataSource>>	ours		= new ConcurrentHashMap<>();

	/**
	 * Put a tracker on every started pool that has none. Cheap enough to call every few seconds.
	 */
	public void attachAll() {
		try {
			for ( Map.Entry<Key, DataSource> e : BoxRuntime.getInstance().getDataSourceService().getAll().entrySet() ) {
				String				id	= e.getKey().getName();
				HikariDataSource	h	= e.getValue().isPoolingStarted() ? e.getValue().getHikariDataSource() : null;
				if ( h == null || !h.isRunning() || attached.contains( id + "@" + System.identityHashCode( h ) ) ) {
					continue;
				}
				attached.add( id + "@" + System.identityHashCode( h ) );
				if ( h.getMetricsTrackerFactory() == null ) {
					MetricsTrackerFactory f = ( poolName, stats ) -> trackers.computeIfAbsent( id, k -> new Tracker() );
					h.setMetricsTrackerFactory( f );
					ours.put( id + "@" + System.identityHashCode( h ), new java.lang.ref.WeakReference<>( h ) );
					trackers.computeIfAbsent( id, k -> new Tracker() );
				}
			}
		} catch ( Throwable t ) {
			// Metrics are a bonus, never a problem
		}
	}

	/**
	 * Take the tracker off every pool it was put on, so the pools are as they were before Lens.
	 */
	public void shutdown() {
		for ( java.lang.ref.WeakReference<HikariDataSource> ref : ours.values() ) {
			try {
				HikariDataSource h = ref.get();
				if ( h != null && h.isRunning() ) {
					h.setMetricsTrackerFactory( null );
				}
			} catch ( Throwable t ) {
				// The pool does not allow it any more
			}
		}
		ours.clear();
		attached.clear();
		trackers.clear();
	}

	/**
	 * All datasources with pool numbers, settings and timings.
	 */
	public Map<String, Object> list() {
		attachAll();
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for ( Map.Entry<Key, DataSource> e : BoxRuntime.getInstance().getDataSourceService().getAll().entrySet() ) {
				out.add( describe( e.getKey().getName(), e.getValue() ) );
			}
		} catch ( Throwable t ) {
			// Return what we have
		}
		out.sort( Comparator.comparing( m -> String.valueOf( m.get( "name" ) ), String.CASE_INSENSITIVE_ORDER ) );
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "datasources", out );
		m.put( "at", System.currentTimeMillis() );
		return m;
	}

	private Map<String, Object> describe( String id, DataSource ds ) {
		Map<String, Object>	m	= new LinkedHashMap<>();
		var					cfg	= ds.getConfiguration();
		m.put( "id", id );
		m.put( "name", cfg.getOriginalName() );
		m.put( "application", cfg.hasApplicationName() ? cfg.getApplicationName().getName() : "" );
		m.put( "onTheFly", ds.isOnTheFly() );
		m.put( "driver", cfg.getDriverName().getName() );
		HikariDataSource h = ds.isPoolingStarted() ? ds.getHikariDataSource() : null;
		m.put( "pooling", h != null && h.isRunning() );
		if ( h == null || !h.isRunning() ) {
			m.put( "state", "idle" );
			return m;
		}
		m.put( "url", maskUrl( h.getJdbcUrl() ) );
		m.put( "user", h.getUsername() == null ? "" : h.getUsername() );
		var					pool	= h.getHikariPoolMXBean();
		int					active	= pool.getActiveConnections();
		int					idle	= pool.getIdleConnections();
		int					total	= pool.getTotalConnections();
		int					pending	= pool.getThreadsAwaitingConnection();
		int					max		= h.getMaximumPoolSize();
		Map<String, Object>	p		= new LinkedHashMap<>();
		p.put( "active", active );
		p.put( "idle", idle );
		p.put( "total", total );
		p.put( "pending", pending );
		p.put( "max", max );
		p.put( "min", h.getMinimumIdle() );
		p.put( "utilization", max > 0 ? Math.round( active * 1000.0 / max ) / 10.0 : 0 );
		m.put( "pool", p );
		Map<String, Object> c = new LinkedHashMap<>();
		c.put( "connectionTimeoutMs", h.getConnectionTimeout() );
		c.put( "idleTimeoutMs", h.getIdleTimeout() );
		c.put( "maxLifetimeMs", h.getMaxLifetime() );
		c.put( "keepaliveMs", h.getKeepaliveTime() );
		c.put( "validationTimeoutMs", h.getValidationTimeout() );
		c.put( "leakDetectionMs", h.getLeakDetectionThreshold() );
		c.put( "autoCommit", h.isAutoCommit() );
		c.put( "readOnly", h.isReadOnly() );
		c.put( "isolation", h.getTransactionIsolation() == null ? "driver default" : h.getTransactionIsolation() );
		c.put( "poolName", h.getPoolName() );
		m.put( "config", c );
		Tracker t = trackers.get( id );
		if ( t != null ) {
			Map<String, Object> mt = new LinkedHashMap<>();
			mt.put( "since", t.since );
			mt.put( "acquired", t.acquireCount.sum() );
			mt.put( "acquireAvgMs", avg( t.acquireNanos.sum() / 1_000_000.0, t.acquireCount.sum() ) );
			mt.put( "acquireMaxMs", Math.round( t.acquireMaxNanos.get() / 10_000.0 ) / 100.0 );
			mt.put( "usedCount", t.usageCount.sum() );
			mt.put( "usageAvgMs", avg( t.usageMillis.sum(), t.usageCount.sum() ) );
			mt.put( "usageMaxMs", t.usageMaxMillis.get() );
			mt.put( "created", t.createCount.sum() );
			mt.put( "createAvgMs", avg( t.createMillis.sum(), t.createCount.sum() ) );
			mt.put( "timeouts", t.timeouts.sum() );
			m.put( "metrics", mt );
		}
		String state = "ok";
		if ( t != null && t.timeouts.sum() > 0 ) {
			state = "timeouts";
		}
		if ( max > 0 && active >= max ) {
			state = "saturated";
		} else if ( pending > 0 ) {
			state = "waiting";
		}
		m.put( "state", state );
		return m;
	}

	/**
	 * Open a connection, check it and close it, and report how long it took.
	 */
	public Map<String, Object> test( String id ) {
		Map<String, Object>	m	= new LinkedHashMap<>();
		DataSource			ds	= null;
		try {
			for ( Map.Entry<Key, DataSource> e : BoxRuntime.getInstance().getDataSourceService().getAll().entrySet() ) {
				if ( e.getKey().getName().equals( id ) ) {
					ds = e.getValue();
				}
			}
		} catch ( Throwable t ) {
			// Handled below
		}
		if ( ds == null ) {
			m.put( "ok", false );
			m.put( "message", "Unknown datasource" );
			return m;
		}
		long t0 = System.nanoTime();
		try ( Connection c = ds.getConnection() ) {
			long	opened	= System.nanoTime();
			boolean	valid	= c.isValid( 5 );
			m.put( "ok", valid );
			m.put( "openMs", Math.round( ( opened - t0 ) / 10_000.0 ) / 100.0 );
			m.put( "validateMs", Math.round( ( System.nanoTime() - opened ) / 10_000.0 ) / 100.0 );
			m.put( "message", valid ? "Connection opened and validated" : "The connection opened but did not validate" );
			try {
				m.put( "product", c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion() );
			} catch ( Throwable ignore ) {
				// Optional
			}
		} catch ( Throwable t ) {
			m.put( "ok", false );
			m.put( "message", t.getClass().getSimpleName() + ": " + String.valueOf( t.getMessage() ) );
		}
		return m;
	}

	/**
	 * Open a connection to a datasource for reading its metadata. The caller closes it.
	 *
	 * @return the connection, or null when there is no such datasource
	 */
	public Connection open( String id ) {
		try {
			for ( Map.Entry<Key, DataSource> e : BoxRuntime.getInstance().getDataSourceService().getAll().entrySet() ) {
				if ( e.getKey().getName().equals( id ) ) {
					return e.getValue().getConnection();
				}
			}
		} catch ( RuntimeException e ) {
			throw e;
		}
		return null;
	}

	private static double avg( double total, long count ) {
		return count == 0 ? 0 : Math.round( total / count * 100.0 ) / 100.0;
	}

	/**
	 * Remove credentials and secret-looking parameters from a JDBC URL, with the shared secret-name matcher.
	 */
	static String maskUrl( String url ) {
		return url == null ? "" : ortus.boxlang.modules.bxlens.util.Secrets.url( url );
	}

}
