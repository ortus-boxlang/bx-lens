/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.jdbc.PendingQuery;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records every SQL query with its bindings, row count, datasource, time and the template line that ran it.
 * N+1, duplicate and slow detection happens later in the {@link ortus.boxlang.modules.bxlens.model.IssueEngine}.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class QueryCollector extends BaseCollector {

	@Override
	public String id() {
		return "queries";
	}

	@InterceptionPoint
	public void preQueryExecute( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			// The statement here still has its placeholders, so identical queries group together even though their values differ
			String	sql		= String.valueOf( event.get( Keys.sql ) );
			Span	span	= req.begin( Span.QUERY, oneLine( sql ), config().collectorInt( id(), "max", 200 ) );
			if ( span != null ) {
				Sanitizer clean = new Sanitizer( config() );
				span.detail.put( "sql", clean.text( sql ) );
				if ( !config().light && config().queriesIncludeParams ) {
					span.detail.put( "params", clean.clean( event.get( Keys.bindings ) ) );
				}
				if ( !config().light && config().queriesCaptureCaller ) {
					Callers.Location where = Callers.current();
					span.file	= where.file();
					span.line	= where.line();
				}
			}
		} catch ( Throwable t ) {
			fail( "preQueryExecute", t );
		}
	}

	@InterceptionPoint
	public void postQueryExecute( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Span span = req.open( Span.QUERY );
			req.end( span );
			if ( span == null ) {
				return;
			}
			Map<String, Object> q = new LinkedHashMap<>();
			q.put( "span", span.id );
			q.put( "sql", span.detail.getOrDefault( "sql", "" ) );
			if ( span.detail.containsKey( "params" ) ) {
				q.put( "params", span.detail.get( "params" ) );
			}
			q.put( "ms", Span.ms( span.durationNs() ) );
			q.put( "rows", rows( event ) );
			q.put( "datasource", datasource( event ) );
			q.put( "file", span.file );
			q.put( "line", span.line );
			req.queries.add( q );
		} catch ( Throwable t ) {
			fail( "postQueryExecute", t );
		}
	}

	private int rows( IStruct event ) {
		Object meta = event.get( Keys.result );
		if ( meta instanceof IStruct s && s.get( Key.recordCount ) instanceof Number n ) {
			return n.intValue();
		}
		return 0;
	}

	private String datasource( IStruct event ) {
		try {
			if ( event.get( Keys.pendingQuery ) instanceof PendingQuery pq && pq.getDataSource() != null ) {
				return pq.getDataSource().getOriginalName();
			}
		} catch ( Throwable t ) {
			// Datasource unknown
		}
		return "";
	}

	private String oneLine( String sql ) {
		String s = ortus.boxlang.modules.bxlens.util.Text.collapseSpaces( sql );
		return s.length() > 160 ? s.substring( 0, 160 ) + "..." : s;
	}

}
