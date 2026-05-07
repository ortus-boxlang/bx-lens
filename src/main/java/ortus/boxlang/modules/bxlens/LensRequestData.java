/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.bxlens;

import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Typed per-request tracking POJO. Lazy-loaded into request context as a context attachment.
 * Collectors populate the various lists. LensRender converts it to JSON via toStruct().
 */
public class LensRequestData {

	public final String					requestId;
	public volatile boolean				enabled;
	public final long					startedAt		= System.currentTimeMillis();
	public volatile long				endedAt			= 0L;
	public volatile String				method			= "";
	public volatile String				url				= "";
	public volatile int					statusCode		= 200;
	public volatile String				applicationName	= "";

	// Collector lists - thread-safe for concurrent append
	public final List<Map<String, Object>>	queries			= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	exceptions		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	templates		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	httpCalls		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	soapCalls		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	messages		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	timings			= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	bifCalls		= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	functionCalls	= new CopyOnWriteArrayList<>();
	public final List<Map<String, Object>>	appEvents		= new CopyOnWriteArrayList<>();

	// Pending timers (label/hash -> start data)
	public final Map<String, Map<String, Object>> pendingTimings = Collections.synchronizedMap( new LinkedHashMap<>() );

	// Scopes (populated at request end by ScopesCollector)
	public volatile IStruct formScope		= Struct.of();
	public volatile IStruct sessionScope	= Struct.of();
	public volatile IStruct requestScope	= Struct.of();

	// Global stats snapshot (populated at request end)
	public volatile Map<String, Object> globalStats = new HashMap<>();

	public LensRequestData( String requestId, boolean enabled ) {
		this.requestId	= requestId;
		this.enabled	= enabled;
	}

	/**
	 * Lazy-get from context; returns null if not yet initialized.
	 */
	public static LensRequestData get( IBoxContext ctx ) {
		if ( ctx == null ) return null;
		IBoxContext req = ctx.getRequestContext();
		if ( req == null ) req = ctx;
		return req.getAttachment( KeyDictionary.lensData );
	}

	/**
	 * Lazy-create in context using computeAttachmentIfAbsent.
	 * Safe to call from any collector - only one instance is ever created per request.
	 */
	public static LensRequestData getOrCreate( IBoxContext ctx, String requestId, boolean enabled ) {
		if ( ctx == null ) return null;
		IBoxContext req = ctx.getRequestContext();
		if ( req == null ) req = ctx;
		return req.computeAttachmentIfAbsent( KeyDictionary.lensData, k -> new LensRequestData( requestId, enabled ) );
	}

	/**
	 * Convert to IStruct for JSON serialization in LensRender.
	 */
	public IStruct toStruct() {
		return Struct.linkedOf(
		    "requestId", requestId,
		    "enabled", enabled,
		    "startedAt", startedAt,
		    "endedAt", endedAt,
		    "method", method,
		    "url", url,
		    "statusCode", statusCode,
		    "applicationName", applicationName,
		    "queries", listToArray( queries ),
		    "exceptions", listToArray( exceptions ),
		    "templates", listToArray( templates ),
		    "httpCalls", listToArray( httpCalls ),
		    "soapCalls", listToArray( soapCalls ),
		    "messages", listToArray( messages ),
		    "timings", listToArray( timings ),
		    "bifCalls", listToArray( bifCalls ),
		    "functionCalls", listToArray( functionCalls ),
		    "appEvents", listToArray( appEvents ),
		    "variables", Struct.of(
		        "form", formScope,
		        "session", sessionScope,
		        "request", requestScope
		    ),
		    "globalStats", Struct.fromMap( globalStats )
		);
	}

	private static Array listToArray( List<Map<String, Object>> list ) {
		Array arr = new Array();
		for ( Map<String, Object> m : list ) {
			arr.add( Struct.fromMap( m ) );
		}
		return arr;
	}

}
