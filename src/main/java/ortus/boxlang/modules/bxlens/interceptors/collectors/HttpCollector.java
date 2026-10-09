/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records outgoing HTTP calls made with the http component or BoxHttpClient.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class HttpCollector extends BaseCollector {

	private static final Key	STATUS_CODE		= Key.of( "statusCode" );
	private static final Key	STATUS_CODE_ALT	= Key.of( "status_code" );
	private static final Key	FILE_CONTENT	= Key.of( "fileContent" );
	private static final Key	REQUEST_BUILDER	= Key.of( "httpRequestBuilder" );

	@Override
	public String id() {
		return "http";
	}

	@InterceptionPoint
	public void onHTTPRequest( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			String	method	= "GET";
			String	url		= "";
			if ( event.get( Keys.httpRequest ) instanceof HttpRequest hr ) {
				method	= hr.method();
				url		= hr.uri().toString();
			}
			// Credentials in the URL (user info, secret parameters) never reach the page, so Copy as cURL cannot expose them
			url = Secrets.text( url );
			Span span = req.begin( Span.HTTP, method + " " + stripQuery( url ), config().collectorInt( id(), "max", 100 ) );
			if ( span != null ) {
				if ( !config().light ) {
					Callers.Location where = Callers.current();
					span.file	= where.file();
					span.line	= where.line();
				}
				span.detail.put( "method", method );
				span.detail.put( "url", url );
				propagate( event, req );
			}
		} catch ( Throwable t ) {
			fail( "onHTTPRequest", t );
		}
	}

	@InterceptionPoint
	public void onHTTPResponse( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Span span = req.open( Span.HTTP );
			req.end( span );
			if ( span == null ) {
				return;
			}
			int		status	= 0;
			long	size	= 0;
			if ( event.get( Keys.result ) instanceof IStruct r ) {
				Object s = firstNonNull( r.get( STATUS_CODE ), r.get( STATUS_CODE_ALT ) );
				status = s instanceof Number n ? n.intValue() : parseInt( s );
				Object content = r.get( FILE_CONTENT );
				size = content == null ? 0 : content.toString().length();
			}
			span.detail.put( "status", status );
			span.detail.put( "size", size );
			if ( status >= 500 ) {
				span.flag( "crit", String.valueOf( status ) );
			} else if ( status >= 400 ) {
				span.flag( "warn", String.valueOf( status ) );
			}
			Map<String, Object> h = new LinkedHashMap<>();
			h.put( "span", span.id );
			h.put( "method", span.detail.get( "method" ) );
			h.put( "url", span.detail.get( "url" ) );
			h.put( "status", status );
			h.put( "size", size );
			h.put( "ms", Span.ms( span.durationNs() ) );
			h.put( "file", span.file );
			h.put( "line", span.line );
			req.http.add( h );
		} catch ( Throwable t ) {
			fail( "onHTTPResponse", t );
		}
	}

	@InterceptionPoint
	public void onHTTPError( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Span span = req.open( Span.HTTP );
			req.end( span );
			if ( span != null ) {
				span.flag( "crit", "Error" );
				span.detail.put( "status", 0 );
				Map<String, Object> h = new LinkedHashMap<>();
				h.put( "span", span.id );
				h.put( "method", span.detail.get( "method" ) );
				h.put( "url", span.detail.get( "url" ) );
				h.put( "status", 0 );
				h.put( "size", 0 );
				h.put( "ms", Span.ms( span.durationNs() ) );
				h.put( "file", span.file );
				h.put( "line", span.line );
				h.put( "error", true );
				req.http.add( h );
			}
		} catch ( Throwable t ) {
			fail( "onHTTPError", t );
		}
	}

	/**
	 * Opt in (<code>collectors.http.propagateId</code>): add the request id as <code>X-Request-Id</code> to the outgoing call. Core hands the
	 * collector an immutable request, so this only works when the event carries a request builder. Nothing is added by default.
	 */
	private void propagate( IStruct event, LensRequest req ) {
		try {
			if ( config().collectorBool( id(), "propagateId", false ) && event.get( REQUEST_BUILDER ) instanceof HttpRequest.Builder b ) {
				b.setHeader( "X-Request-Id", req.id );
			}
		} catch ( Throwable t ) {
			fail( "propagate", t );
		}
	}

	private static Object firstNonNull( Object a, Object b ) {
		return a != null ? a : b;
	}

	private static int parseInt( Object o ) {
		try {
			return o == null ? 0 : Integer.parseInt( o.toString().trim() );
		} catch ( NumberFormatException e ) {
			return 0;
		}
	}

	private static String stripQuery( String url ) {
		int q = url.indexOf( '?' );
		return q > 0 ? url.substring( 0, q ) : url;
	}

}
