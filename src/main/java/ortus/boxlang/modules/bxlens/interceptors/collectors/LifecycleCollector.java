/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Starts and finishes tracking of every web request. Always registered; it is what creates the
 * {@link ortus.boxlang.modules.bxlens.model.LensRequest}.
 * <p>
 * Core skips <code>onRequestEnd</code> when a request ends in an uncaught exception or an abort, so <code>onError</code> and <code>onAbort</code>
 * also finish the request. Those requests are recorded in History, but the bar cannot be injected into core's own error page.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class LifecycleCollector extends BaseCollector {

	@Override
	public String id() {
		return "request";
	}

	@InterceptionPoint
	public void onRequestStart( IStruct event ) {
		try {
			RequestBoxContext rc = requestContext( event );
			if ( rc != null ) {
				LensService.getInstance().begin( rc );
			}
		} catch ( Throwable t ) {
			fail( "onRequestStart", t );
		}
	}

	@InterceptionPoint
	public void onRequestEnd( IStruct event ) {
		finish( event, LensService.Trigger.END );
	}

	@InterceptionPoint
	public void onError( IStruct event ) {
		// This collector runs before the exception collector, so file the exception first or it would miss the finished request
		try {
			RequestBoxContext	rc		= requestContext( event );
			LensRequest			req		= rc == null ? null : rc.getAttachment( Keys.requestAttach );
			Object				args	= event.get( Keys.args );
			if ( req != null && config().isCollectorEnabled( "exceptions", true ) && args instanceof Object[] arr && arr.length > 0
			    && arr[ 0 ] instanceof Throwable t ) {
				ExceptionCollector.record( req, t, "uncaught", config() );
			}
		} catch ( Throwable t ) {
			fail( "onError", t );
		}
		finish( event, LensService.Trigger.ERROR );
	}

	@InterceptionPoint
	public void onAbort( IStruct event ) {
		finish( event, LensService.Trigger.ABORT );
	}

	@InterceptionPoint
	public void onSessionCreated( IStruct event ) {
		LensService.getInstance().getStats().activeSessions.incrementAndGet();
	}

	@InterceptionPoint
	public void onSessionDestroyed( IStruct event ) {
		LensService.getInstance().getStats().activeSessions.decrementAndGet();
	}

	/**
	 * A module finished loading: an integration whose module just arrived starts listening, without a restart.
	 */
	@InterceptionPoint
	public void postModuleLoad( IStruct event ) {
		reconcileIntegrations();
	}

	/**
	 * A module was unloaded: an integration whose module left stops listening.
	 */
	@InterceptionPoint
	public void postModuleUnload( IStruct event ) {
		reconcileIntegrations();
	}

	private void reconcileIntegrations() {
		try {
			LensService.getInstance().reconcileIntegrations();
		} catch ( Throwable t ) {
			fail( "reconcileIntegrations", t );
		}
	}

	private void finish( IStruct event, LensService.Trigger trigger ) {
		try {
			RequestBoxContext rc = requestContext( event );
			if ( rc != null ) {
				LensService.getInstance().finish( rc, trigger );
			}
		} catch ( Throwable t ) {
			fail( "finish", t );
		}
	}

}
