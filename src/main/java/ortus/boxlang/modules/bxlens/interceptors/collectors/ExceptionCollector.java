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
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxLangException;

/**
 * Records exceptions that pass through functions or reach the application error handler. Caught exceptions can be added by hand with lensException().
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class ExceptionCollector extends BaseCollector {

	private static final String SEEN = "exceptions.seen";

	@Override
	public String id() {
		return "exceptions";
	}

	@InterceptionPoint
	public void onFunctionException( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req != null && event.get( Keys.exception ) instanceof Throwable t ) {
				record( req, t, "function:" + event.get( Keys.name ), config() );
			}
		} catch ( Throwable t ) {
			fail( "onFunctionException", t );
		}
	}

	@InterceptionPoint
	public void onError( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Object args = event.get( Keys.args );
			if ( args instanceof Object[] arr && arr.length > 0 && arr[ 0 ] instanceof Throwable t ) {
				record( req, t, "uncaught", config() );
			}
		} catch ( Throwable t ) {
			fail( "onError", t );
		}
	}

	/**
	 * Record an exception once per request.
	 *
	 * @param origin where it was seen: "uncaught", "function:name" or "manual"
	 */
	@SuppressWarnings( "unchecked" )
	public static void record( LensRequest req, Throwable t, String origin, LensConfig cfg ) {
		if ( !req.reserve( "exception", cfg.collectorInt( "exceptions", "max", 50 ) ) ) {
			return;
		}
		Set<Throwable> seen = ( Set<Throwable> ) req.data.computeIfAbsent( SEEN, k -> Collections.newSetFromMap( new IdentityHashMap<Throwable, Boolean>() ) );
		synchronized ( seen ) {
			if ( !seen.add( t ) ) {
				return;
			}
		}
		Sanitizer			clean	= new Sanitizer( cfg );
		Map<String, Object>	m		= new LinkedHashMap<>();
		String				type	= typeOf( t );
		m.put( "type", type );
		m.put( "class", t.getClass().getName() );
		m.put( "message", clean.text( t.getMessage() ) );
		m.put( "detail", t instanceof BoxLangException b && b.getDetail() != null ? clean.text( b.getDetail() ) : "" );
		m.put( "origin", origin );
		m.put( "at", Span.ms( req.now() ) );
		List<Map<String, Object>> frames = Callers.frames( t, 10 );
		m.put( "frames", frames );
		m.put( "file", frames.isEmpty() ? "" : frames.get( 0 ).get( "file" ) );
		m.put( "line", frames.isEmpty() ? 0 : frames.get( 0 ).get( "line" ) );
		List<String> java = new ArrayList<>();
		for ( StackTraceElement e : t.getStackTrace() ) {
			if ( java.size() >= 12 ) {
				break;
			}
			java.add( e.toString() );
		}
		m.put( "java", java );
		req.exceptions.add( m );
	}

	// Built-in BoxLang types such as Application or Expression say little, so show the Java class. Custom types you throw yourself are kept.
	private static String typeOf( Throwable t ) {
		if ( t instanceof BoxLangException b && b.getType() != null && !b.getType().isEmpty() ) {
			String type = b.getType();
			if ( !GENERIC_TYPES.contains( type.toLowerCase() ) ) {
				return type;
			}
		}
		return t.getClass().getSimpleName();
	}

	private static final Set<String> GENERIC_TYPES = Set.of( "application", "expression", "database", "lock", "security", "missinginclude", "template", "any" );

}
