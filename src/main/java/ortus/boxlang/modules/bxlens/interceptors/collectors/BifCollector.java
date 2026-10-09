/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Time spent in built-in functions, summed per function name: calls, total, slowest call and errors. Core announces <code>postBIFInvocation</code>
 * (with <code>elapsedNanos</code>) and <code>onBIFException</code> only when a listener exists, and a listener makes every BIF call allocate an
 * event,
 * so this collector is heavy and off by default. A request with nothing slow shows nothing here.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class BifCollector extends BaseCollector {

	private static final String	AGG			= "bifs.agg";
	private static final Key	ELAPSED		= Key.of( "elapsedNanos" );
	private static final int	MAX_NAMES	= 300;

	@Override
	public String id() {
		return "bifs";
	}

	@Override
	public boolean heavy() {
		return true;
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@InterceptionPoint
	public void postBIFInvocation( IStruct event ) {
		record( event, false );
	}

	@InterceptionPoint
	public void onBIFException( IStruct event ) {
		record( event, true );
	}

	@SuppressWarnings( "unchecked" )
	private void record( IStruct event, boolean error ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			Object	n	= event.get( Key._name );
			String	bif	= n == null ? "" : n instanceof Key k ? k.getName() : n.toString();
			// Lens's own functions are not interesting, and not worth the time they take to measure
			if ( bif.isEmpty() || bif.regionMatches( true, 0, "lens", 0, 4 ) ) {
				return;
			}
			long				nanos	= event.get( ELAPSED ) instanceof Number x ? x.longValue() : 0;
			Map<String, long[]>	agg		= ( Map<String, long[]> ) req.data.computeIfAbsent( AGG, k -> new ConcurrentHashMap<String, long[]>() );
			String				key		= bif.toLowerCase();
			if ( agg.size() >= MAX_NAMES && !agg.containsKey( key ) ) {
				return;
			}
			long[] a = agg.computeIfAbsent( key, k -> new long[ 5 ] );
			synchronized ( a ) {
				a[ 0 ]++;
				a[ 1 ]	+= nanos;
				a[ 2 ]	= Math.max( a[ 2 ], nanos );
				a[ 3 ]	+= error ? 1 : 0;
				a[ 4 ]	= 1;
			}
		} catch ( Throwable t ) {
			fail( "bif timing", t );
		}
	}

	@Override
	@SuppressWarnings( "unchecked" )
	public void onRequestFinish( LensRequest req ) {
		Object o = req.data.remove( AGG );
		if ( ! ( o instanceof Map<?, ?> agg ) ) {
			return;
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map.Entry<String, long[]> e : ( ( Map<String, long[]> ) agg ).entrySet() ) {
			long[] a = e.getValue();
			synchronized ( a ) {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put( "name", e.getKey() );
				m.put( "calls", a[ 0 ] );
				m.put( "totalMs", Math.round( a[ 1 ] / 10_000.0 ) / 100.0 );
				m.put( "avgMs", a[ 0 ] == 0 ? 0 : Math.round( a[ 1 ] / a[ 0 ] / 10_000.0 ) / 100.0 );
				m.put( "maxMs", Math.round( a[ 2 ] / 10_000.0 ) / 100.0 );
				m.put( "errors", a[ 3 ] );
				out.add( m );
			}
		}
		out.sort( Comparator.comparingDouble( ( Map<String, Object> m ) -> ( ( Number ) m.get( "totalMs" ) ).doubleValue() ).reversed() );
		req.data.put( "bifs", out.size() > 60 ? new ArrayList<>( out.subList( 0, 60 ) ) : out );
	}

}
