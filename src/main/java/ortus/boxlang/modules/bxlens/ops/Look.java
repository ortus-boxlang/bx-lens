/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.ConsoleRouter;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.LogData;
import ortus.boxlang.modules.bxlens.OverviewData;
import ortus.boxlang.modules.bxlens.store.RequestStore;
import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.modules.bxlens.util.Text;

/**
 * The tools that only read what the console already shows. Each method returns plain data and leaves redaction and the size cap to the
 * {@link Toolbox}. The role has been checked before a method here runs; <code>admin</code> is kept for the few tools whose answer differs
 * by role.
 */
final class Look {

	private final LensService	service;
	private final boolean		admin;

	Look( LensService service, boolean admin ) {
		this.service	= service;
		this.admin		= admin;
	}

	Map<String, Object> overview() {
		this.service.sync();
		Map<String, Object>	m		= new LinkedHashMap<>( OverviewData.build( this.service ) );
		Map<String, Object>	reports	= this.service.getReports().snapshot( false );
		Map<String, Object>	session	= Plain.map( reports.get( "session" ) );
		m.remove( "series" );
		m.remove( "attention" );
		m.remove( "lens" );
		Map<String, Object> totals = new LinkedHashMap<>();
		totals.put( "sinceMs", reports.get( "uptimeMs" ) );
		for ( String k : List.of( "requests", "errors", "slow", "exceptions", "avgMs", "maxMs", "p50", "p95", "p99", "status" ) ) {
			totals.put( k, session.get( k ) );
		}
		long reqs = Plain.num( session.get( "requests" ), 0 );
		totals.put( "errorRatePercent", reqs == 0 ? 0 : Math.round( Plain.num( session.get( "errors" ), 0 ) * 1000.0 / reqs ) / 10.0 );
		m.put( "totalsSinceStart", totals );
		m.put( "busiestUrls", first( reports.get( "busiestUrls" ), 5 ) );
		m.put( "slowestUrls", first( reports.get( "slowestUrls" ), 5 ) );
		m.put( "failingUrls", first( reports.get( "failingUrls" ), 5 ) );
		m.put( "server", this.service.getIdentity().get().toMap() );
		m.put( "requestsRunningNow", this.service.inflight().size() );
		return m;
	}

	Object requests( int limit, boolean problemsOnly, String urlContains ) {
		List<Map<String, Object>>	out		= new ArrayList<>();
		String						needle	= urlContains.toLowerCase( Locale.ROOT );
		for ( Map<String, Object> r : this.service.getStore().summaries( true ) ) {
			String url = Plain.str( r.get( "url" ) );
			if ( !needle.isEmpty() && !url.toLowerCase( Locale.ROOT ).contains( needle ) ) {
				continue;
			}
			boolean problem = Plain.num( r.get( "status" ), 0 ) >= 500 || Plain.num( r.get( "issues" ), 0 ) > 0 || "unfinished".equals( r.get( "state" ) )
			    || "crit".equals( r.get( "severity" ) ) || "warn".equals( r.get( "severity" ) );
			if ( problemsOnly && !problem ) {
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			for ( String k : List.of( "id", "method", "url", "status", "ms", "queries", "issues", "severity", "state", "at", "serverId" ) ) {
				m.put( k, r.get( k ) );
			}
			out.add( m );
			if ( out.size() >= limit ) {
				break;
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "requests", out );
		m.put( "inMemory", this.service.getStore().size() );
		m.put( "note", "Newest first. Use requestDetail with an id for the issues and queries of one request." );
		return m;
	}

	Object requestDetail( String id ) {
		this.service.sync();
		RequestStore.Entry e = this.service.getStore().get( id );
		if ( e == null ) {
			throw new IllegalArgumentException( "That request is no longer in memory. Use requests to list the ones that are." );
		}
		Map<String, Object>	full	= Plain.map( Plain.parse( e.consoleJson() ) );
		Map<String, Object>	m		= new LinkedHashMap<>();
		Map<String, Object>	r		= Plain.map( full.get( "request" ) );
		Map<String, Object>	req		= new LinkedHashMap<>();
		for ( String k : List.of( "id", "method", "uri", "query", "status", "type", "remoteAddr", "app", "template", "startedAt", "durationMs", "severity",
		    "state",
		    "serverHost", "serverIp", "serverId" ) ) {
			req.put( k, r.get( k ) );
		}
		m.put( "request", req );
		m.put( "counts", full.get( "counts" ) );
		m.put( "issues", first( full.get( "issues" ), 15 ) );
		List<Map<String, Object>> queries = new ArrayList<>();
		for ( Object q : Plain.list( full.get( "queries" ) ) ) {
			Map<String, Object>	x	= Plain.map( q );
			Map<String, Object>	y	= new LinkedHashMap<>();
			y.put( "sql", Text.maskSql( Plain.str( x.get( "sql" ) ) ) );
			y.put( "ms", x.get( "ms" ) );
			y.put( "rows", x.get( "rows" ) );
			y.put( "datasource", x.get( "datasource" ) );
			y.put( "flag", x.get( "flag" ) );
			queries.add( y );
		}
		queries.sort( Comparator.comparingDouble( ( Map<String, Object> x ) -> x.get( "ms" ) instanceof Number n ? n.doubleValue() : 0 ).reversed() );
		m.put( "slowestQueries", queries.size() > 10 ? queries.subList( 0, 10 ) : queries );
		List<Map<String, Object>> ex = new ArrayList<>();
		for ( Object o : Plain.list( full.get( "exceptions" ) ) ) {
			Map<String, Object>	x	= Plain.map( o );
			Map<String, Object>	y	= new LinkedHashMap<>();
			y.put( "type", x.get( "type" ) );
			y.put( "message", x.get( "message" ) );
			y.put( "origin", x.get( "origin" ) );
			y.put( "file", x.get( "file" ) );
			y.put( "line", x.get( "line" ) );
			ex.add( y );
		}
		m.put( "exceptions", ex.size() > 8 ? ex.subList( 0, 8 ) : ex );
		List<Map<String, Object>> http = new ArrayList<>();
		for ( Object o : Plain.list( full.get( "http" ) ) ) {
			Map<String, Object>	x	= Plain.map( o );
			Map<String, Object>	y	= new LinkedHashMap<>();
			y.put( "method", x.get( "method" ) );
			y.put( "url", Secrets.url( Plain.str( x.get( "url" ) ) ) );
			y.put( "status", x.get( "status" ) );
			y.put( "ms", x.get( "ms" ) );
			http.add( y );
		}
		m.put( "outgoingHttp", http.size() > 8 ? http.subList( 0, 8 ) : http );
		return m;
	}

	Object inFlight() {
		List<Map<String, Object>>	list	= this.service.inflight();
		Map<String, Object>			m		= new LinkedHashMap<>();
		m.put( "running", list.size() );
		m.put( "requests", list.size() > 30 ? list.subList( 0, 30 ) : list );
		return m;
	}

	Object errors( int limit, String id ) {
		this.service.sync();
		if ( !id.isEmpty() ) {
			Map<String, Object> g = this.service.getErrors().get( id );
			if ( g == null ) {
				throw new IllegalArgumentException( "That error group is no longer kept. Use errors without an id to list them." );
			}
			List<Object> samples = Plain.list( g.get( "samples" ) );
			if ( samples.size() > 2 ) {
				g.put( "samples", new ArrayList<>( samples.subList( samples.size() - 2, samples.size() ) ) );
			}
			for ( Object s : Plain.list( g.get( "samples" ) ) ) {
				Map<String, Object> sm = Plain.map( s );
				sm.put( "frames", first( sm.get( "frames" ), 12 ) );
				sm.put( "java", first( sm.get( "java" ), 8 ) );
			}
			return g;
		}
		Map<String, Object>			list	= this.service.getErrors().list();
		List<Map<String, Object>>	groups	= new ArrayList<>();
		for ( Object o : Plain.list( list.get( "groups" ) ) ) {
			Map<String, Object>	g	= Plain.map( o );
			Map<String, Object>	m	= new LinkedHashMap<>();
			for ( String k : List.of( "id", "type", "message", "count", "firstSeen", "lastSeen", "lastStatus", "handled", "file", "line", "urlCount",
			    "serverId" ) ) {
				m.put( k, g.get( k ) );
			}
			groups.add( m );
			if ( groups.size() >= limit ) {
				break;
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "groups", groups );
		m.put( "occurrences", list.get( "occurrences" ) );
		m.put( "note", "Newest first. Use errors with an id for the samples and the stack." );
		return m;
	}

	Object queryStats( int limit, String sort ) {
		this.service.sync();
		Map<String, Object>			snap	= this.service.getQueryStats().snapshot();
		List<Map<String, Object>>	rows	= new ArrayList<>();
		for ( Object o : Plain.list( snap.get( "statements" ) ) ) {
			rows.add( Plain.map( o ) );
		}
		String key = switch ( sort ) {
			case "total" -> "totalMs";
			case "count" -> "count";
			case "failures" -> "failures";
			default -> "maxMs";
		};
		rows.sort( ( a, b ) -> Double.compare( d( b.get( key ) ), d( a.get( key ) ) ) );
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map<String, Object> r : rows ) {
			Map<String, Object> m = new LinkedHashMap<>();
			for ( String k : List.of( "sql", "datasource", "count", "failures", "slow", "avgMs", "maxMs", "totalMs", "rows", "lastError", "file", "line" ) ) {
				m.put( k, r.get( k ) );
			}
			out.add( m );
			if ( out.size() >= limit ) {
				break;
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "statements", out );
		for ( String k : List.of( "executed", "failed", "slow", "totalMs", "avgMs" ) ) {
			m.put( k, snap.get( k ) );
		}
		m.put( "note", "Statements have placeholders and never parameter values." );
		return m;
	}

	@SuppressWarnings( "unchecked" )
	Object executors( boolean onlyProblems ) {
		Map<String, Object>			data	= this.service.getData().executors();
		List<Map<String, Object>>	all		= ( List<Map<String, Object>> ) data.get( "executors" );
		List<Map<String, Object>>	out		= new ArrayList<>();
		for ( Map<String, Object> e : all ) {
			String health = Plain.str( e.get( "healthStatus" ) );
			if ( onlyProblems && ( "healthy".equals( health ) ) ) {
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			for ( String k : List.of( "name", "kind", "healthStatus", "poolSize", "corePoolSize", "maximumPoolSize", "largestPoolSize", "activeCount",
			    "poolUtilization",
			    "threadsUtilization", "queueSize", "queueCapacity", "queueUtilization", "queueIsFull", "taskCount", "completedTaskCount", "taskSubmissionCount",
			    "averageTasksPerSecond", "lastActivitySecondsAgo" ) ) {
				m.put( k, e.get( k ) );
			}
			Map<String, Object> hr = Plain.map( e.get( "healthReport" ) );
			m.put( "healthSummary", hr.get( "summary" ) );
			m.put( "healthIssues", hr.get( "issues" ) );
			m.put( "runtimeRecommendations", hr.get( "recommendations" ) );
			m.put( "alerts", hr.get( "alerts" ) );
			m.put( "scheduledTasks", Plain.list( e.get( "tasks" ) ).size() );
			out.add( m );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "executors", out );
		m.put( "counts", data.get( "counts" ) );
		m.put( "findings", Diagnose.executors( all ).stream().map( Diagnose.Finding::toMap ).toList() );
		m.put( "note",
		    "findings are worked out from the pool numbers and carry concrete sizes. The runtime does not report refused tasks, so a full queue is the sign of them." );
		return m;
	}

	@SuppressWarnings( "unchecked" )
	Object tasks() {
		Map<String, Object>			t			= this.service.getData().tasks();
		List<Map<String, Object>>	schedulers	= new ArrayList<>();
		for ( Object so : Plain.list( t.get( "schedulers" ) ) ) {
			Map<String, Object>			s	= Plain.map( so );
			Map<String, Object>			m	= new LinkedHashMap<>();
			List<Map<String, Object>>	ts	= new ArrayList<>();
			m.put( "name", s.get( "name" ) );
			m.put( "executor", s.get( "executor" ) );
			m.put( "started", s.get( "started" ) );
			for ( Object to : Plain.list( s.get( "tasks" ) ) ) {
				Map<String, Object>	x	= Plain.map( to );
				Map<String, Object>	y	= new LinkedHashMap<>();
				for ( String k : List.of( "name", "group", "status", "schedule", "lastRun", "nextRun", "totalRuns", "totalSuccess", "totalFailures",
				    "lastMs" ) ) {
					y.put( k, x.get( k ) );
				}
				Map<String, Object> outcome = Plain.map( x.get( "outcome" ) );
				if ( Boolean.FALSE.equals( outcome.get( "ok" ) ) ) {
					y.put( "lastError", outcome.get( "message" ) );
				}
				ts.add( y );
			}
			m.put( "tasks", ts.size() > 60 ? ts.subList( 0, 60 ) : ts );
			schedulers.add( m );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "schedulers", schedulers );
		m.put( "total", t.get( "total" ) );
		m.put( "failing", t.get( "failing" ) );
		m.put( "paused", t.get( "paused" ) );
		return m;
	}

	Object datasources() {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Object o : Plain.list( this.service.getDatasources().list().get( "datasources" ) ) ) {
			Map<String, Object>	d	= Plain.map( o );
			Map<String, Object>	m	= new LinkedHashMap<>();
			// No URL and no user: a JDBC URL can carry credentials
			for ( String k : List.of( "name", "application", "driver", "pooling", "state", "pool", "metrics" ) ) {
				m.put( k, d.get( k ) );
			}
			out.add( m );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "datasources", out );
		m.put( "findings",
		    Diagnose.datasources( maps( this.service.getDatasources().list().get( "datasources" ) ) ).stream().map( Diagnose.Finding::toMap ).toList() );
		return m;
	}

	Object caches() {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Object o : Plain.list( this.service.getCaches().list().get( "caches" ) ) ) {
			Map<String, Object>	c	= Plain.map( o );
			Map<String, Object>	m	= new LinkedHashMap<>();
			for ( String k : List.of( "name", "provider", "enabled", "objects", "hits", "misses", "hitRate", "evictions", "reaps", "expired", "lastReap" ) ) {
				m.put( k, c.get( k ) );
			}
			Map<String, Object> cfg = Plain.map( c.get( "config" ) );
			m.put( "maxObjects", cfg.get( "maxObjects" ) );
			m.put( "evictionPolicy", cfg.get( "evictionPolicy" ) );
			out.add( m );
		}
		return Map.of( "caches", out, "note", "Statistics and names only. Cache values are not available to the agent." );
	}

	Object modules() {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Object o : Plain.list( this.service.getEnvironment().modules().get( "modules" ) ) ) {
			Map<String, Object>	d	= Plain.map( o );
			Map<String, Object>	m	= new LinkedHashMap<>();
			for ( String k : List.of( "name", "version", "enabled", "activated", "nested", "parent", "description" ) ) {
				m.put( k, d.get( k ) );
			}
			out.add( m );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "modules", out );
		m.put( "integrations", this.service.getIntegrations().list( this.service.getConfig() ) );
		return m;
	}

	@SuppressWarnings( "unchecked" )
	Object configuration( String filter ) {
		Map<String, Object>			cfg		= this.service.getRuntimeInfo().configuration( this.admin );
		String						needle	= filter.toLowerCase( Locale.ROOT );
		List<Map<String, Object>>	groups	= new ArrayList<>();
		int							shown	= 0;
		for ( Object go : Plain.list( cfg.get( "groups" ) ) ) {
			Map<String, Object>			g		= Plain.map( go );
			List<Map<String, Object>>	entries	= new ArrayList<>();
			for ( Object eo : Plain.list( g.get( "entries" ) ) ) {
				Map<String, Object> e = Plain.map( eo );
				if ( !needle.isEmpty() ) {
					String hay = ( Plain.str( e.get( "key" ) ) + " " + Plain.str( e.get( "value" ) ) ).toLowerCase( Locale.ROOT );
					if ( !hay.contains( needle ) ) {
						continue;
					}
				} else if ( entries.size() >= 12 ) {
					continue;
				}
				if ( shown++ >= 80 ) {
					break;
				}
				entries.add( e );
			}
			if ( !entries.isEmpty() ) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put( "name", g.get( "name" ) );
				m.put( "entries", entries );
				groups.add( m );
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "groups", groups );
		if ( needle.isEmpty() ) {
			m.put( "note", "The first entries of each area. Use filter to search for a setting by name or value." );
		}
		if ( !this.admin ) {
			m.put( "adminOnlyHidden", true );
		}
		return m;
	}

	Object lensInfo() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "server", this.service.getIdentity().get().toMap() );
		m.put( "version", this.service.getVersion() );
		m.put( "collectLevel", this.service.getConfig().collectLevel );
		m.put( "license", this.service.getLicensing().status() );
		m.put( "plusFeatures", this.service.getLicensing().features() );
		Map<String, Object> settings = new LinkedHashMap<>();
		for ( Map<String, Object> row : this.service.settingsView() ) {
			if ( !Boolean.TRUE.equals( row.get( "live" ) ) || "secret".equals( row.get( "type" ) ) ) {
				continue;
			}
			String key = Plain.str( row.get( "key" ) );
			if ( !this.admin && ConsoleRouter.ADMIN_SETTINGS.contains( key.toLowerCase( Locale.ROOT ) ) ) {
				continue;
			}
			settings.put( key, row.get( "value" ) );
		}
		m.put( "liveSettings", settings );
		return m;
	}

	Object logs( String file, String query, int lines, String level ) throws java.io.IOException {
		LogData logs = this.service.getLogs();
		if ( file.isEmpty() ) {
			Map<String, Object>	l		= logs.list();
			List<Object>		files	= Plain.list( l.get( "files" ) );
			return Map.of( "files", files.size() > 40 ? files.subList( 0, 40 ) : files, "note", "Pass file with one of these names to read it." );
		}
		Map<String, Object> r;
		try {
			r = logs.read( file, Math.min( lines, 300 ), query, level );
		} catch ( LogData.Busy e ) {
			throw new IllegalStateException( "Another log read is running. Try again in a moment." );
		}
		if ( r == null ) {
			throw new IllegalArgumentException( "No such log file. Call logs without a file to list them." );
		}
		List<String> out = new ArrayList<>();
		for ( Object o : Plain.list( r.get( "lines" ) ) ) {
			String line = o instanceof Map<?, ?> m ? Plain.str( m.get( "text" ) ) : Plain.str( o );
			out.add( Secrets.text( line.length() > 400 ? line.substring( 0, 400 ) + "..." : line ) );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "file", file );
		m.put( "lines", out );
		return m;
	}

	Object environment() {
		Map<String, Object>	env	= this.service.getEnvironment().environment();
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "info", env.get( "info" ) );
		m.put( "jvmArgs", env.get( "jvmArgs" ) );
		m.put( "env", first( env.get( "env" ), 80 ) );
		m.put( "properties", first( env.get( "properties" ), 60 ) );
		m.put( "note", "Values of secret-looking names are hidden." );
		return m;
	}

	Object dbTables( String datasource, String filter ) throws Exception {
		try ( Connection c = open( datasource ) ) {
			return DbMeta.tables( c, filter );
		}
	}

	Object dbColumns( String datasource, String table ) throws Exception {
		try ( Connection c = open( datasource ) ) {
			return DbMeta.columns( c, table );
		}
	}

	Object dbTest( String datasource ) {
		return this.service.getDatasources().test( datasource );
	}

	private Connection open( String datasource ) {
		Connection c = this.service.getDatasources().open( datasource );
		if ( c == null ) {
			throw new IllegalArgumentException( "Unknown datasource. Use datasources to list them." );
		}
		return c;
	}

	// ---------------------------------------------------------------------------------------------

	static Object first( Object list, int n ) {
		List<Object> l = Plain.list( list );
		return l.size() > n ? new ArrayList<>( l.subList( 0, n ) ) : l;
	}

	@SuppressWarnings( "unchecked" )
	static List<Map<String, Object>> maps( Object o ) {
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Object x : Plain.list( o ) ) {
			if ( x instanceof Map<?, ?> m ) {
				out.add( ( Map<String, Object> ) m );
			}
		}
		return out;
	}

	private static double d( Object o ) {
		return o instanceof Number n ? n.doubleValue() : 0;
	}

}
