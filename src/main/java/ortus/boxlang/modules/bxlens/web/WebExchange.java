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

import java.util.LinkedHashMap;
import java.util.Map;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.web.context.WebRequestBoxContext;
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

	public String remoteAddr() {
		return safe( exchange.getRequestRemoteAddr() );
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

	private static String safe( String s ) {
		return s == null ? "" : s;
	}

}
