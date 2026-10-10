/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.OrmTotals;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.modules.bxlens.util.Text;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Shows what bx-orm reports through its observability events: <code>onORMQuery</code> (every JDBC statement, selects once their result set is
 * closed), <code>onORMFlush</code> and <code>onORMException</code>. Lens listens to these events only. It never reaches into bx-orm or Hibernate.
 * <p>
 * Each statement becomes a query span and an entry in the request's query list, the same shape as a normal query plus <code>orm: true</code> and
 * the <code>kind</code>, for the request that is current on the thread that ran it. A statement that runs outside a request (startup DDL, a task
 * on its own thread) counts in the ORM totals only. The span is as long as <code>elapsedNanos</code> and ends when the event arrives.
 * <p>
 * This is the collector of the <code>orm</code> integration: the service registers it only while <code>collectors.orm.enabled</code> is on and
 * the bx-orm module is installed. Parameter values come only when the application sets <code>announceQueryParams</code> and
 * <code>collectors.queries.includeParams</code> is on here.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class OrmCollector extends BaseCollector {

	private static final Key	ELAPSED		= Key.of( "elapsedNanos" );
	private static final Key	KIND		= Key.of( "kind" );
	private static final Key	ROWS		= Key.of( "rows" );
	private static final Key	DATASOURCE	= Key.of( "datasource" );
	private static final Key	APP_NAME	= Key.of( "appName" );
	private static final Key	HQL			= Key.of( "hql" );
	private static final Key	ENTITY		= Key.of( "entityName" );
	private static final Key	ERROR		= Key.of( "error" );
	private static final Key	PARAMS		= Key.of( "params" );
	private static final Key	INSERTS		= Key.of( "inserts" );
	private static final Key	UPDATES		= Key.of( "updates" );
	private static final Key	DELETES		= Key.of( "deletes" );

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@Override
	public String id() {
		return "orm";
	}

	@Override
	public String integration() {
		return "orm";
	}

	/** The totals, a hook for tests. */
	protected OrmTotals totals() {
		return service().getOrmTotals();
	}

	/**
	 * A JDBC statement ran.
	 */
	@InterceptionPoint
	public void onORMQuery( IStruct event ) {
		try {
			String		sql		= text( event.get( Keys.sql ) );
			String		kind	= text( event.get( KIND ) );
			String		app		= text( event.get( APP_NAME ) );
			String		ds		= text( event.get( DATASOURCE ) );
			long		nanos	= number( event.get( ELAPSED ), 0 );
			long		rows	= number( event.get( ROWS ), -1 );
			Throwable	error	= event.get( ERROR ) instanceof Throwable t ? t : null;
			boolean		counted	= false;
			LensRequest	req		= request( event );
			if ( req != null ) {
				counted = attach( req, event, sql, kind.isEmpty() ? "other" : kind, ds, nanos, rows, error );
			}
			totals().query( app, ds, kind, nanos, rows, error != null, counted, sql );
		} catch ( Throwable t ) {
			fail( "onORMQuery", t );
		}
	}

	/**
	 * A Hibernate session flushed.
	 */
	@InterceptionPoint
	public void onORMFlush( IStruct event ) {
		try {
			totals().flush( text( event.get( APP_NAME ) ), text( event.get( DATASOURCE ) ), number( event.get( INSERTS ), 0 ),
			    number( event.get( UPDATES ), 0 ),
			    number( event.get( DELETES ), 0 ), number( event.get( ELAPSED ), 0 ) );
		} catch ( Throwable t ) {
			fail( "onORMFlush", t );
		}
	}

	/**
	 * A statement failed. The query event of the same statement carries the error into the request, so this only fills the failure list of the
	 * ORM page.
	 */
	@InterceptionPoint
	public void onORMException( IStruct event ) {
		try {
			Object	err		= event.get( ERROR );
			String	message	= err instanceof Throwable t ? String.valueOf( t.getMessage() ) : text( err );
			totals().exception( text( event.get( APP_NAME ) ), text( event.get( DATASOURCE ) ), text( event.get( Keys.sql ) ), message );
		} catch ( Throwable t ) {
			fail( "onORMException", t );
		}
	}

	/**
	 * Add the statement to the request. Returns false when the cap for queries is reached.
	 */
	private boolean attach( LensRequest req, IStruct event, String sql, String kind, String ds, long nanos, long rows, Throwable error ) {
		LensConfig	cfg		= config();
		long		end		= req.now();
		long		start	= Math.max( 0, end - nanos );
		Span		span	= req.addClosed( Span.QUERY, oneLine( sql ), start, end, cfg.collectorInt( "queries", "max", 200 ) );
		if ( span == null ) {
			return false;
		}
		Sanitizer	clean	= new Sanitizer( cfg );
		String		shown	= clean.text( sql );
		String		hql		= text( event.get( HQL ) );
		String		entity	= text( event.get( ENTITY ) );
		boolean		full	= !cfg.light;
		Object		bound	= null;
		if ( full && cfg.queriesIncludeParams && event.get( PARAMS ) instanceof java.util.List<?> list && !list.isEmpty() ) {
			bound = clean.clean( new ArrayList<>( list ) );
		}
		span.detail.put( "sql", shown );
		span.detail.put( "orm", true );
		span.detail.put( "kind", kind );
		if ( !hql.isEmpty() ) {
			span.detail.put( "hql", clean.text( hql ) );
		}
		if ( !entity.isEmpty() ) {
			span.detail.put( "entity", entity );
		}
		if ( bound != null ) {
			span.detail.put( "params", bound );
		}
		if ( full && cfg.queriesCaptureCaller ) {
			Callers.Location where = Callers.current();
			span.file	= where.file();
			span.line	= where.line();
		}
		Map<String, Object> q = new LinkedHashMap<>();
		q.put( "span", span.id );
		q.put( "sql", shown );
		if ( bound != null ) {
			q.put( "params", bound );
		}
		q.put( "ms", Span.ms( nanos ) );
		// Unknown (DDL) shows as empty, not as -1
		q.put( "rows", rows < 0 ? null : rows );
		q.put( "datasource", ds );
		q.put( "file", span.file );
		q.put( "line", span.line );
		q.put( "orm", true );
		q.put( "kind", kind );
		if ( !hql.isEmpty() ) {
			q.put( "hql", span.detail.get( "hql" ) );
		}
		if ( !entity.isEmpty() ) {
			q.put( "entity", entity );
		}
		if ( error != null ) {
			String message = clean.text( String.valueOf( error.getMessage() ) );
			q.put( "error", message );
			span.detail.put( "error", message );
		}
		req.queries.add( q );
		return true;
	}

	private static String text( Object o ) {
		return o == null ? "" : o.toString();
	}

	private static long number( Object o, long def ) {
		return o instanceof Number n ? n.longValue() : def;
	}

	private static String oneLine( String sql ) {
		String s = Text.collapseSpaces( sql );
		return s.length() > 160 ? s.substring( 0, 160 ) + "..." : s;
	}

}
