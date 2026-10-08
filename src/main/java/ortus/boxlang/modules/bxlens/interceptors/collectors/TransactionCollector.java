/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Callers;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Shows database transactions as spans on the waterfall, flagged when they roll back.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class TransactionCollector extends BaseCollector {

	@Override
	public String id() {
		return "transactions";
	}

	@InterceptionPoint
	public void onTransactionBegin( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Span span = req.begin( Span.TX, "transaction", config().collectorInt( id(), "max", 50 ) );
			if ( span != null ) {
				Callers.Location where = Callers.current();
				span.file	= where.file();
				span.line	= where.line();
				span.detail.put( "outcome", "open" );
			}
		} catch ( Throwable t ) {
			fail( "onTransactionBegin", t );
		}
	}

	@InterceptionPoint
	public void onTransactionCommit( IStruct event ) {
		outcome( event, "commit" );
	}

	@InterceptionPoint
	public void onTransactionRollback( IStruct event ) {
		outcome( event, "rollback" );
	}

	@InterceptionPoint
	public void onTransactionEnd( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req != null ) {
				req.end( req.open( Span.TX ) );
			}
		} catch ( Throwable t ) {
			fail( "onTransactionEnd", t );
		}
	}

	private void outcome( IStruct event, String outcome ) {
		try {
			LensRequest	req		= request( event );
			Span		span	= req == null ? null : req.open( Span.TX );
			if ( span != null ) {
				span.detail.put( "outcome", outcome );
				if ( "rollback".equals( outcome ) ) {
					span.flag( "warn", "Rollback" );
				}
			}
		} catch ( Throwable t ) {
			fail( "transaction outcome", t );
		}
	}

}
