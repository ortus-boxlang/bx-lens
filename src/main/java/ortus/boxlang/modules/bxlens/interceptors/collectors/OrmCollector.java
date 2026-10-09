/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Shows the SQL that bx-orm (Hibernate) runs, the same way as a normal query: statement, bindings, time, rows, datasource and the BoxLang line,
 * attributed to the request that ran it. Hibernate gets its SQL to the database without any BoxLang event, so Lens swaps the
 * <code>connectionProvider</code> of each Hibernate session factory for a JDK proxy that wraps the connection, statement and result set. Everything
 * is
 * reached by reflection: Lens never imports bx-orm or Hibernate classes, because they live in another module's class loader.
 * <p>
 * The private Hibernate 5 field is the fragile part. If it cannot be changed, a Logback appender on <code>org.hibernate.SQL</code> gives the SQL text
 * only (no time or rows). Without bx-orm nothing happens. Global statistics are read by {@link OrmData}.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class OrmCollector extends BaseCollector {

	/** Session factories that were hooked (weak, so an ORM reload does not keep the old one alive) and how. */
	public static final Map<Object, Map<String, Object>>	FACTORIES	= Collections.synchronizedMap( new WeakHashMap<>() );
	private static volatile boolean							logbackOn;

	@Override
	public String id() {
		return "orm";
	}

	@InterceptionPoint
	public void onRequestStart( IStruct event ) {
		install();
	}

	@InterceptionPoint
	public void onApplicationStart( IStruct event ) {
		install();
	}

	@InterceptionPoint
	public void onSessionStart( IStruct event ) {
		install();
	}

	/**
	 * Find the session factories of every ORM application and hook the new ones. Cheap when nothing is new: bx-orm may load after Lens or an
	 * application may reload, so it runs on request, application and session start.
	 */
	@SuppressWarnings( "unchecked" )
	public void install() {
		try {
			Object		svc	= BoxRuntime.getInstance().getGlobalService( Key.of( "ORMService" ) );
			IBoxContext	ctx	= RequestBoxContext.getCurrent();
			if ( svc == null || ctx == null ) {
				return;
			}
			for ( String name : ( List<String> ) svc.getClass().getMethod( "getORMAppNames" ).invoke( svc ) ) {
				Object app = svc.getClass().getMethod( "getORMApp", Key.class ).invoke( svc, Key.of( name ) );
				if ( app == null ) {
					continue;
				}
				for ( Object ds : ( List<Object> ) app.getClass().getMethod( "getDatasources" ).invoke( app ) ) {
					Object sf = sessionFactory( app, ds, ctx );
					if ( sf != null && !FACTORIES.containsKey( sf ) ) {
						hook( sf, name, String.valueOf( ds ) );
					}
				}
			}
		} catch ( Throwable t ) {
			fail( "install", t );
		}
	}

	private Object sessionFactory( Object app, Object ds, IBoxContext ctx ) throws Exception {
		try {
			try {
				return app.getClass().getMethod( "getSessionFactoryOrThrow", Key.class, IBoxContext.class ).invoke( app, ds, ctx );
			} catch ( NoSuchMethodException e ) {
				// bx-orm 1.3.0 has only getSessionFactoryOrThrow(Key)
				return app.getClass().getMethod( "getSessionFactoryOrThrow", Key.class ).invoke( app, ds );
			}
		} catch ( InvocationTargetException e ) {
			// The factory is not built yet
			return null;
		}
	}

	private void hook( Object sf, String appName, String dsName ) {
		Map<String, Object> info = new LinkedHashMap<>();
		info.put( "app", appName );
		info.put( "datasource", dsName );
		info.put( "mode", "none" );
		FACTORIES.put( sf, info );
		LensConfig cfg = config();
		try {
			if ( cfg.collectorBool( "orm", "statistics", true ) ) {
				Object stats = sf.getClass().getMethod( "getStatistics" ).invoke( sf );
				stats.getClass().getMethod( "setStatisticsEnabled", boolean.class ).invoke( stats, true );
			}
		} catch ( Throwable t ) {
			fail( "statistics", t );
		}
		try {
			Object	fast	= sf.getClass().getMethod( "getFastSessionServices" ).invoke( sf );
			Field	cp		= fast.getClass().getDeclaredField( "connectionProvider" );
			cp.setAccessible( true );
			Object		orig	= cp.get( fast );
			Class<?>	iface	= Class.forName( "org.hibernate.engine.jdbc.connections.spi.ConnectionProvider", true, orig.getClass().getClassLoader() );
			cp.set( fast, Proxy.newProxyInstance( orig.getClass().getClassLoader(), new Class<?>[] { iface }, new ProviderHandler( orig, dsName ) ) );
			info.put( "mode", "proxy" );
			LensService.getInstance().getLogger().info( "bx-lens: showing the SQL of bx-orm app [{}], datasource [{}]", appName, dsName );
		} catch ( Throwable t ) {
			fail( "hook", t );
			attachLogback();
			info.put( "mode", logbackOn ? "log" : "none" );
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Fallback: the Hibernate SQL log gives the statement text only
	// ---------------------------------------------------------------------------------------------

	private static synchronized void attachLogback() {
		if ( logbackOn ) {
			return;
		}
		try {
			ch.qos.logback.classic.LoggerContext										lc	= ( ch.qos.logback.classic.LoggerContext ) org.slf4j.LoggerFactory
			    .getILoggerFactory();
			ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>	app	= new ch.qos.logback.core.AppenderBase<>() {

																								@Override
																								protected void append(
																								    ch.qos.logback.classic.spi.ILoggingEvent ev ) {
																									try {
																										Recorder r = Recorder.start( "" );
																										if ( r != null ) {
																											r.finish( ev.getFormattedMessage(),
																											    new ArrayList<>(), 0, null );
																										}
																									} catch ( Throwable t ) {
																										// Never break logging
																									}
																								}
																							};
			app.setContext( lc );
			app.start();
			ch.qos.logback.classic.Logger l = lc.getLogger( "org.hibernate.SQL" );
			l.addAppender( app );
			l.setLevel( ch.qos.logback.classic.Level.DEBUG );
			logbackOn = true;
		} catch ( Throwable t ) {
			// No fallback available
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Recording a statement as a Lens query
	// ---------------------------------------------------------------------------------------------

	/**
	 * One statement being recorded for the current request.
	 */
	static final class Recorder {

		final LensRequest			req;
		final Span					span;
		final Map<String, Object>	entry	= new LinkedHashMap<>();
		final String				ds;
		final LensConfig			cfg;

		private Recorder( LensRequest req, Span span, String ds, LensConfig cfg ) {
			this.req	= req;
			this.span	= span;
			this.ds		= ds;
			this.cfg	= cfg;
		}

		/**
		 * Open a query span on the current request, or null when the request is not tracked or the cap for queries is reached.
		 */
		static Recorder start( String ds ) {
			RequestBoxContext rc = RequestBoxContext.getCurrent() == null ? null : RequestBoxContext.getCurrent().getRequestContext();
			if ( rc == null ) {
				return null;
			}
			LensRequest req = rc.getAttachment( Keys.requestAttach );
			if ( req == null || !req.enabled || req.finished.get() ) {
				return null;
			}
			LensConfig cfg = LensService.getInstance().getConfig();
			if ( !cfg.isCollectorEnabled( "orm", true ) ) {
				return null;
			}
			Span span = req.begin( Span.QUERY, "orm", cfg.collectorInt( "queries", "max", 200 ) );
			return span == null ? null : new Recorder( req, span, ds, cfg );
		}

		void finish( String sql, List<Object> params, int rows, Throwable error ) {
			req.end( span );
			Sanitizer	clean	= new Sanitizer( cfg );
			String		text	= clean.text( sql );
			span.detail.put( "sql", text );
			span.detail.put( "orm", true );
			boolean	full	= !cfg.light;
			Object	bound	= full && cfg.collectorBool( "queries", "includeParams", true ) && !params.isEmpty() ? clean.clean( params ) : null;
			if ( bound != null ) {
				span.detail.put( "params", bound );
			}
			if ( full && cfg.collectorBool( "queries", "captureCaller", true ) ) {
				Callers.Location where = Callers.current();
				span.file	= where.file();
				span.line	= where.line();
			}
			entry.put( "span", span.id );
			entry.put( "sql", text );
			if ( bound != null ) {
				entry.put( "params", bound );
			}
			entry.put( "ms", Span.ms( span.durationNs() ) );
			entry.put( "rows", rows );
			entry.put( "datasource", ds );
			entry.put( "file", span.file );
			entry.put( "line", span.line );
			entry.put( "orm", true );
			if ( error != null ) {
				entry.put( "error", String.valueOf( error.getMessage() ) );
			}
			req.queries.add( entry );
		}
	}

	// ---------------------------------------------------------------------------------------------
	// JDBC proxies (JDK interfaces only, so class loaders do not matter)
	// ---------------------------------------------------------------------------------------------

	static Object call( Method m, Object target, Object[] args ) throws Throwable {
		try {
			return m.invoke( target, args );
		} catch ( InvocationTargetException e ) {
			throw e.getCause();
		}
	}

	record ProviderHandler( Object target, String ds ) implements InvocationHandler {

		@Override
		public Object invoke( Object p, Method m, Object[] a ) throws Throwable {
			if ( m.getName().equals( "getConnection" ) ) {
				Connection real = ( Connection ) call( m, target, a );
				return Proxy.newProxyInstance( Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, new ConnHandler( real, ds ) );
			}
			if ( m.getName().equals( "closeConnection" ) && a != null && a[ 0 ] != null && Proxy.isProxyClass( a[ 0 ].getClass() )
			    && Proxy.getInvocationHandler( a[ 0 ] ) instanceof ConnHandler ch ) {
				a = new Object[] { ch.real };
			}
			return call( m, target, a );
		}
	}

	record ConnHandler( Connection real, String ds ) implements InvocationHandler {

		@Override
		public Object invoke( Object p, Method m, Object[] a ) throws Throwable {
			Object	out	= call( m, real, a );
			String	n	= m.getName();
			if ( n.equals( "prepareStatement" ) || n.equals( "prepareCall" ) ) {
				boolean callable = n.equals( "prepareCall" );
				return Proxy.newProxyInstance( Connection.class.getClassLoader(),
				    callable ? new Class<?>[] { CallableStatement.class } : new Class<?>[] { PreparedStatement.class },
				    new StmtHandler( ( Statement ) out, ( String ) a[ 0 ], ds ) );
			}
			if ( n.equals( "createStatement" ) ) {
				return Proxy.newProxyInstance( Connection.class.getClassLoader(), new Class<?>[] { Statement.class },
				    new StmtHandler( ( Statement ) out, null, ds ) );
			}
			return out;
		}
	}

	static final class StmtHandler implements InvocationHandler {

		final Statement				real;
		final String				sql;
		final String				ds;
		final Map<Integer, Object>	params	= new TreeMap<>();

		StmtHandler( Statement real, String sql, String ds ) {
			this.real	= real;
			this.sql	= sql;
			this.ds		= ds;
		}

		@Override
		public Object invoke( Object p, Method m, Object[] a ) throws Throwable {
			String n = m.getName();
			if ( n.startsWith( "set" ) && a != null && a.length >= 2 && a[ 0 ] instanceof Integer i ) {
				params.put( i, a[ 1 ] );
			} else if ( n.equals( "clearParameters" ) ) {
				params.clear();
			}
			boolean exec = n.equals( "executeQuery" ) || n.equals( "executeUpdate" ) || n.equals( "execute" ) || n.equals( "executeBatch" )
			    || n.equals( "executeLargeUpdate" );
			if ( !exec ) {
				return call( m, real, a );
			}
			String		text	= a != null && a.length > 0 && a[ 0 ] instanceof String s ? s : this.sql;
			Recorder	rec		= null;
			try {
				rec = Recorder.start( ds );
			} catch ( Throwable t ) {
				// Recording is optional
			}
			Object out;
			try {
				out = call( m, real, a );
			} catch ( Throwable t ) {
				if ( rec != null ) {
					rec.finish( text, new ArrayList<>( params.values() ), 0, t );
				}
				throw t;
			}
			if ( rec == null ) {
				return out;
			}
			List<Object>	bound	= new ArrayList<>( params.values() );
			int				rows	= 0;
			if ( out instanceof Integer i ) {
				rows = i;
			} else if ( out instanceof int[] arr ) {
				for ( int x : arr ) {
					rows += Math.max( x, 0 );
				}
			} else if ( out instanceof ResultSet rs ) {
				rec.finish( text, bound, 0, null );
				final Recorder		r		= rec;
				final AtomicInteger	counted	= new AtomicInteger();
				return Proxy.newProxyInstance( Connection.class.getClassLoader(), new Class<?>[] { ResultSet.class }, ( pp, mm, aa ) -> {
					Object v = call( mm, rs, aa );
					if ( mm.getName().equals( "next" ) && Boolean.TRUE.equals( v ) ) {
						r.entry.put( "rows", counted.incrementAndGet() );
					}
					return v;
				} );
			} else if ( out instanceof Boolean b && !b ) {
				rows = real.getUpdateCount();
			}
			rec.finish( text, bound, rows, null );
			return out;
		}
	}

}
