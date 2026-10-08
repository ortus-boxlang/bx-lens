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
package ortus.boxlang.modules.bxlens.web;

import ortus.boxlang.modules.bxlens.ClientIp;
import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.LensService;
import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.web.context.WebRequestBoxContext;
import ortus.boxlang.web.exchange.BoxCookie;
import ortus.boxlang.web.exchange.IBoxHTTPExchange;

/**
 * Thin adapter over the web-support HTTP exchange. Web-support classes are compile-only dependencies, so this class is only
 * ever touched after {@link #of(IBoxContext)} proved a web context exists. Outside a web runtime (CLI, tests) {@code of} returns null.
 */
public final class WebExchange {

	private final IBoxHTTPExchange exchange;

	private WebExchange( IBoxHTTPExchange exchange ) {
		this.exchange = exchange;
	}

	/**
	 * Find the HTTP exchange of a request context.
	 *
	 * @return the adapter or null when this is not a web request
	 */
	public static WebExchange of( IBoxContext requestContext ) {
		try {
			if ( requestContext instanceof WebRequestBoxContext web ) {
				IBoxHTTPExchange ex = web.getHTTPExchange();
				return ex == null ? null : new WebExchange( ex );
			}
		} catch ( Throwable t ) {
			// Web support missing or context shut down: not a web request for our purposes
		}
		return null;
	}

	public String method() {
		return safe( exchange.getRequestMethod() );
	}

	public String url() {
		StringBuffer sb = exchange.getRequestURL();
		return sb == null ? "" : sb.toString();
	}

	public String uri() {
		return safe( exchange.getRequestURI() );
	}

	public String queryString() {
		return safe( exchange.getRequestQueryString() );
	}

	/**
	 * The address of the direct connection, whatever a proxy header says.
	 */
	public String peerAddr() {
		return safe( exchange.getRequestRemoteAddr() );
	}

	/**
	 * The client address. Behind a trusted proxy this is the address the proxy header names.
	 */
	public String remoteAddr() {
		String		peer	= peerAddr();
		LensConfig	cfg		= LensService.getInstance().getConfig();
		if ( !cfg.getBool( "access.trustProxyHeader", true ) ) {
			return peer;
		}
		return ClientIp.resolve( cfg, peer, exchange.getRequestHeader( cfg.getString( "access.proxyHeader", "X-Forwarded-For" ) ) );
	}

	public String host() {
		return safe( exchange.getRequestHeader( "Host" ) );
	}

	public String requestHeader( String name ) {
		return name == null || name.isEmpty() ? null : exchange.getRequestHeader( name );
	}

	/**
	 * Request headers with multiple values joined by comma.
	 */
	public Map<String, Object> requestHeaders() {
		Map<String, Object>		out	= new LinkedHashMap<>();
		Map<String, String[]>	map	= exchange.getRequestHeaderMap();
		if ( map != null ) {
			map.forEach( ( k, v ) -> out.put( k, v == null ? "" : String.join( ", ", v ) ) );
		}
		return out;
	}

	/**
	 * Response headers with multiple values joined by comma.
	 */
	public Map<String, Object> responseHeaders() {
		Map<String, Object>		out	= new LinkedHashMap<>();
		Map<String, String[]>	map	= exchange.getResponseHeaderMap();
		if ( map != null ) {
			map.forEach( ( k, v ) -> out.put( k, v == null ? "" : String.join( ", ", v ) ) );
		}
		return out;
	}

	/**
	 * Cookies the response sets.
	 */
	public java.util.List<ortus.boxlang.modules.bxlens.model.SecurityChecks.Cookie> responseCookies() {
		java.util.List<ortus.boxlang.modules.bxlens.model.SecurityChecks.Cookie>	out	= new java.util.ArrayList<>();
		BoxCookie[]																	cs	= exchange.getResponseCookies();
		if ( cs != null ) {
			for ( BoxCookie c : cs ) {
				out.add( new ortus.boxlang.modules.bxlens.model.SecurityChecks.Cookie( c.getName(), c.isHttpOnly(), c.isSecure(), c.isSameSite() ) );
			}
		}
		return out;
	}

	public int status() {
		return exchange.getResponseStatus();
	}

	public String responseHeader( String name ) {
		return exchange.getResponseHeader( name );
	}

	public boolean responseStarted() {
		return exchange.isResponseStarted();
	}

	public void setResponseHeader( String name, String value ) {
		exchange.setResponseHeader( name, value );
	}

	/**
	 * Path after the script name, for example <code>/api/requests</code> in <code>/~bxlens/index.bxm/api/requests</code>.
	 */
	public String pathInfo() {
		return safe( exchange.getRequestPathInfo() );
	}

	public boolean secure() {
		try {
			if ( exchange.isRequestSecure() ) {
				return true;
			}
			return ClientIp.trustsProxy( LensService.getInstance().getConfig(), peerAddr() )
			    && "https".equalsIgnoreCase( exchange.getRequestHeader( "X-Forwarded-Proto" ) );
		} catch ( Throwable t ) {
			return false;
		}
	}

	public String urlParam( String name ) {
		Map<String, String[]>	m	= exchange.getRequestURLMap();
		String[]				v	= m == null ? null : m.get( name );
		return v == null || v.length == 0 ? null : v[ 0 ];
	}

	public String formParam( String name ) {
		Map<String, String[]>	m	= exchange.getRequestFormMap();
		String[]				v	= m == null ? null : m.get( name );
		return v == null || v.length == 0 ? null : v[ 0 ];
	}

	/**
	 * The request body as text.
	 */
	public String body() {
		Object b = exchange.getRequestBody();
		if ( b == null ) {
			return "";
		}
		if ( b instanceof byte[] bytes ) {
			return new String( bytes, java.nio.charset.StandardCharsets.UTF_8 );
		}
		return b.toString();
	}

	/**
	 * The request body exactly as web support hands it over: text, bytes or an already parsed map.
	 */
	public Object rawBody() {
		return exchange.getRequestBody();
	}

	public String cookie( String name ) {
		BoxCookie c = exchange.getRequestCookie( name );
		return c == null ? null : c.getValue();
	}

	/**
	 * Set a cookie. A negative max age makes it a session cookie, zero deletes it.
	 */
	public void setCookie( String name, String value, boolean httpOnly, boolean secure, String sameSite, int maxAgeSeconds, String path ) {
		BoxCookie c = new BoxCookie( name, value );
		c.setPath( path );
		c.setHttpOnly( httpOnly );
		c.setSecure( secure );
		if ( sameSite != null ) {
			c.setSameSite( true );
			c.setSameSiteMode( sameSite );
		}
		if ( maxAgeSeconds >= 0 ) {
			c.setMaxAge( maxAgeSeconds );
		}
		exchange.addResponseCookie( c );
	}

	/**
	 * Has a write to the browser failed? True once the browser closed the connection.
	 */
	public boolean writeFailed() {
		try {
			java.io.PrintWriter w = exchange.getResponseWriter();
			return w != null && w.checkError();
		} catch ( Throwable t ) {
			return true;
		}
	}

	/**
	 * Stream a file as the response body.
	 */
	public void sendFile( java.io.File f ) {
		exchange.sendResponseFile( f );
	}

	/**
	 * Send bytes as the response body.
	 */
	public void sendBinary( byte[] data ) {
		exchange.sendResponseBinary( data );
	}

	public void setStatus( int code ) {
		exchange.setResponseStatus( code );
	}

	private static String safe( String s ) {
		return s == null ? "" : s;
	}

}
