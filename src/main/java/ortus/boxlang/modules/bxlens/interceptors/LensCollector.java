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
 */
package ortus.boxlang.modules.bxlens.interceptors;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.util.KeyDictionary;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.IScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * bx-lens Data Collector Interceptor
 *
 * Listens to BoxLang runtime events and populates the per-request lens data struct
 * stored as a context attachment under the key "__bxLensData__".
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class LensCollector extends BaseInterceptor {

	@Override
	public void configure( IStruct properties ) {
		// Nothing special needed
	}

	private IStruct getSettings() {
		return getRuntime().getModuleService().getModuleRecord( KeyDictionary.moduleName ).settings;
	}

	private LensService getLensService() {
		return KeyDictionary.getLensService();
	}

	/**
	 * Fired before the request template is executed.
	 * Initialises the lens data attachment for this request.
	 */
	@InterceptionPoint
	public void onRequestStart( IStruct event ) {
		IBoxContext ctx = getContext( event );
		if ( ctx == null ) return;

		IStruct		settings	= getSettings();
		LensService	svc			= getLensService();
		String		reqId		= svc.startRequest();

		IStruct variables = Struct.of(
		    "form", Struct.of(),
		    "url", Struct.of(),
		    "cgi", Struct.of(),
		    "request", Struct.of(),
		    "session", Struct.of(),
		    "application", Struct.of()
		);

		IStruct lensData = Struct.of(
		    "requestId", reqId,
		    "enabled", settings.getAsBoolean( Key.of( "enabled" ) ),
		    "startedAt", System.currentTimeMillis(),
		    "endedAt", 0L,
		    "method", "",
		    "url", "",
		    "statusCode", 200,
		    "applicationName", "",
		    "queries", new Array(),
		    "exceptions", new Array(),
		    "templates", new Array(),
		    "httpCalls", new Array(),
		    "soapCalls", new Array(),
		    "messages", new Array(),
		    "timings", new Array(),
		    "_pendingTimings", Struct.of(),
		    "variables", variables,
		    "globalStats", Struct.of()
		);

		// Capture HTTP method/URL safely (not available in CLI context)
		try {
			Object exchange = ctx.getClass().getMethod( "getHTTPExchange" ).invoke( ctx );
			if ( exchange != null ) {
				Object method = exchange.getClass().getMethod( "getRequestMethod" ).invoke( exchange );
				Object url = exchange.getClass().getMethod( "getRequestURL" ).invoke( exchange );
				if ( method != null ) lensData.put( "method", method.toString() );
				if ( url != null ) lensData.put( "url", url.toString() );
			}
		} catch ( Exception ignored ) {}

		ctx.putAttachment( KeyDictionary.lensData, lensData );
	}

	/**
	 * Fired after the request completes.
	 * Captures scope snapshots and finalises timing.
	 */
	@InterceptionPoint
	public void onRequestEnd( IStruct event ) {
		IBoxContext ctx = getContext( event );
		if ( ctx == null ) return;
		IStruct lensData = getLensData( ctx );
		if ( lensData == null ) return;

		long now = System.currentTimeMillis();
		lensData.put( "endedAt", now );
		long duration = now - lensData.getAsLong( Key.of( "startedAt" ) );
		getLensService().endRequest( duration );

		IStruct settings	= getSettings();
		IStruct scopes		= settings.getAsStruct( Key.of( "scopes" ) );
		IStruct variables	= lensData.getAsStruct( Key.of( "variables" ) );

		// Scope snapshots — only for enabled scopes
		captureScope( ctx, scopes, variables, "form", Key.of( "form" ) );
		captureScope( ctx, scopes, variables, "url", Key.of( "url" ) );
		captureScope( ctx, scopes, variables, "cgi", Key.of( "cgi" ) );
		captureScope( ctx, scopes, variables, "request", Key.of( "request" ) );
		captureScope( ctx, scopes, variables, "session", Key.of( "session" ) );
		captureScope( ctx, scopes, variables, "application", Key.of( "application" ) );

		lensData.put( "globalStats", Struct.fromMap( getLensService().getStats().snapshot() ) );
	}

	/**
	 * Fired after a SQL query executes.
	 */
	@InterceptionPoint
	public void postQueryExecute( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		IStruct settings = getSettings();
		Array queries = lensData.getAsArray( Key.of( "queries" ) );
		if ( queries.size() >= settings.getAsInteger( Key.of( "maxQueries" ) ) ) return;

		getLensService().getStats().totalQueries.incrementAndGet();
		long startedAt = lensData.getAsLong( Key.of( "startedAt" ) );

		queries.add( Struct.of(
		    "sql", event.getOrDefault( Key.of( "sql" ), "" ),
		    "executionTime", event.getOrDefault( Key.of( "executionTime" ), 0L ),
		    "recordCount", event.getOrDefault( Key.of( "recordCount" ), 0 ),
		    "params", event.getOrDefault( Key.of( "params" ), Struct.of() ),
		    "offset", System.currentTimeMillis() - startedAt
		) );
	}

	/**
	 * Fired before a template/page is invoked.
	 */
	@InterceptionPoint
	public void preTemplateInvoke( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		Array	templates	= lensData.getAsArray( Key.of( "templates" ) );
		long	startedAt	= lensData.getAsLong( Key.of( "startedAt" ) );
		long	now			= System.currentTimeMillis();

		// Nesting depth = number of currently pending template entries
		int depth = 0;
		for ( Object obj : templates ) {
			if ( Boolean.TRUE.equals( ( ( IStruct ) obj ).getAsBoolean( Key.of( "_pending" ) ) ) ) depth++;
		}

		templates.add( Struct.of(
		    "templatePath", event.getOrDefault( Key.of( "templatePath" ), "" ),
		    "executionTime", 0L,
		    "offset", now - startedAt,
		    "depth", depth,
		    "_pending", Boolean.TRUE,
		    "_startTick", now
		) );
	}

	/**
	 * Fired after a template/page finishes executing.
	 */
	@InterceptionPoint
	public void postTemplateInvoke( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		IStruct	settings	= getSettings();
		Array	templates	= lensData.getAsArray( Key.of( "templates" ) );
		if ( templates.size() >= settings.getAsInteger( Key.of( "maxTemplates" ) ) ) return;

		String	path		= ( String ) event.getOrDefault( Key.of( "templatePath" ), "" );
		long	now			= System.currentTimeMillis();
		Object	rawExecTime	= event.get( Key.of( "executionTime" ) );

		// Walk backwards to find the most-recent pending entry for this path
		for ( int i = templates.size() - 1; i >= 0; i-- ) {
			IStruct t = ( IStruct ) templates.get( i );
			if ( Boolean.TRUE.equals( t.getAsBoolean( Key.of( "_pending" ) ) )
			    && path.equals( t.getAsString( Key.of( "templatePath" ) ) ) ) {

				long execTime = rawExecTime != null
				    ? ( ( Number ) rawExecTime ).longValue()
				    : now - t.getAsLong( Key.of( "_startTick" ) );

				t.put( "executionTime", execTime );
				t.put( "_pending", Boolean.FALSE );
				t.remove( Key.of( "_startTick" ) );
				break;
			}
		}
	}

	/**
	 * Fired when an exception occurs.
	 */
	@InterceptionPoint
	public void onException( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		IStruct	settings	= getSettings();
		Array	exceptions	= lensData.getAsArray( Key.of( "exceptions" ) );
		if ( exceptions.size() >= settings.getAsInteger( Key.of( "maxExceptions" ) ) ) return;

		Object rawEx = event.get( Key.of( "exception" ) );
		if ( rawEx == null ) return;
		IStruct ex = rawEx instanceof IStruct ? ( IStruct ) rawEx : Struct.of();

		getLensService().getStats().totalExceptions.incrementAndGet();
		long startedAt = lensData.getAsLong( Key.of( "startedAt" ) );

		exceptions.add( Struct.of(
		    "type", ex.getOrDefault( Key.of( "type" ), "unknown" ),
		    "message", ex.getOrDefault( Key.of( "message" ), "" ),
		    "detail", ex.getOrDefault( Key.of( "detail" ), "" ),
		    "stackTrace", ex.getOrDefault( Key.of( "stackTrace" ), "" ),
		    "offset", System.currentTimeMillis() - startedAt
		) );
	}

	/**
	 * Fired before an outgoing HTTP request is made.
	 */
	@InterceptionPoint
	public void onHTTPRequest( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		long now = System.currentTimeMillis();
		lensData.getAsArray( Key.of( "httpCalls" ) ).add( Struct.of(
		    "method", event.getOrDefault( Key.of( "method" ), "GET" ),
		    "url", event.getOrDefault( Key.of( "url" ), "" ),
		    "statusCode", 0,
		    "executionTime", 0L,
		    "requestSize", event.getOrDefault( Key.of( "bodyLength" ), 0 ),
		    "responseSize", 0,
		    "offset", now - lensData.getAsLong( Key.of( "startedAt" ) ),
		    "_pending", Boolean.TRUE,
		    "_startTick", now
		) );
	}

	/**
	 * Fired after an outgoing HTTP response is received.
	 */
	@InterceptionPoint
	public void onHTTPResponse( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		getLensService().getStats().totalHttpCalls.incrementAndGet();
		String	url			= ( String ) event.getOrDefault( Key.of( "url" ), "" );
		long	now			= System.currentTimeMillis();
		Object	rawExecTime	= event.get( Key.of( "executionTime" ) );
		Array	httpCalls	= lensData.getAsArray( Key.of( "httpCalls" ) );

		for ( int i = httpCalls.size() - 1; i >= 0; i-- ) {
			IStruct h = ( IStruct ) httpCalls.get( i );
			if ( Boolean.TRUE.equals( h.getAsBoolean( Key.of( "_pending" ) ) )
			    && url.equals( h.getAsString( Key.of( "url" ) ) ) ) {
				h.put( "statusCode", event.getOrDefault( Key.of( "statusCode" ), 0 ) );
				h.put( "responseSize", event.getOrDefault( Key.of( "responseSize" ), 0 ) );
				h.put( "executionTime", rawExecTime != null ? ( ( Number ) rawExecTime ).longValue() : now - h.getAsLong( Key.of( "_startTick" ) ) );
				h.put( "_pending", Boolean.FALSE );
				h.remove( Key.of( "_startTick" ) );
				break;
			}
		}
	}

	/**
	 * Fired before an outgoing SOAP request.
	 */
	@InterceptionPoint
	public void onSOAPRequest( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		long now = System.currentTimeMillis();
		lensData.getAsArray( Key.of( "soapCalls" ) ).add( Struct.of(
		    "endpoint", event.getOrDefault( Key.of( "endpoint" ), "" ),
		    "action", event.getOrDefault( Key.of( "action" ), "" ),
		    "statusCode", 0,
		    "executionTime", 0L,
		    "responseSize", 0,
		    "offset", now - lensData.getAsLong( Key.of( "startedAt" ) ),
		    "_pending", Boolean.TRUE,
		    "_startTick", now
		) );
	}

	/**
	 * Fired after an outgoing SOAP response is received.
	 */
	@InterceptionPoint
	public void onSOAPResponse( IStruct event ) {
		IBoxContext ctx = getContext( event );
		IStruct lensData = getLensData( ctx );
		if ( lensData == null || !Boolean.TRUE.equals( lensData.getAsBoolean( Key.of( "enabled" ) ) ) ) return;

		getLensService().getStats().totalSoapCalls.incrementAndGet();
		long	now			= System.currentTimeMillis();
		Object	rawExecTime	= event.get( Key.of( "executionTime" ) );
		Array	soapCalls	= lensData.getAsArray( Key.of( "soapCalls" ) );

		for ( int i = soapCalls.size() - 1; i >= 0; i-- ) {
			IStruct s = ( IStruct ) soapCalls.get( i );
			if ( Boolean.TRUE.equals( s.getAsBoolean( Key.of( "_pending" ) ) ) ) {
				s.put( "statusCode", event.getOrDefault( Key.of( "statusCode" ), 0 ) );
				s.put( "responseSize", event.getOrDefault( Key.of( "responseSize" ), 0 ) );
				s.put( "executionTime", rawExecTime != null ? ( ( Number ) rawExecTime ).longValue() : now - s.getAsLong( Key.of( "_startTick" ) ) );
				s.put( "_pending", Boolean.FALSE );
				s.remove( Key.of( "_startTick" ) );
				break;
			}
		}
	}

	/**
	 * Track active sessions in GlobalStats.
	 */
	@InterceptionPoint
	public void onSessionStart( IStruct event ) {
		getLensService().getStats().activeSessions.incrementAndGet();
	}

	@InterceptionPoint
	public void onSessionEnd( IStruct event ) {
		getLensService().getStats().activeSessions.decrementAndGet();
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private IBoxContext getContext( IStruct event ) {
		Object ctx = event.get( Key.of( "context" ) );
		return ctx instanceof IBoxContext ? ( IBoxContext ) ctx : null;
	}

	private IStruct getLensData( IBoxContext ctx ) {
		if ( ctx == null ) return null;
		return ctx.getAttachment( KeyDictionary.lensData );
	}

	private void captureScope( IBoxContext ctx, IStruct scopes, IStruct variables, String scopeName, Key scopeKey ) {
		Object enabled = scopes.get( Key.of( scopeName ) );
		if ( !Boolean.TRUE.equals( enabled ) ) return;
		try {
			IScope scope = ctx.getScopeNearby( scopeKey );
			if ( scope != null ) {
				variables.put( scopeKey, Struct.fromMap( scope ) );
			}
		} catch ( Exception ignored ) {}
	}

}
