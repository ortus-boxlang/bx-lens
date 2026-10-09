/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.cache.util.ICacheStats;
import ortus.boxlang.runtime.services.CacheService;
import ortus.boxlang.runtime.types.Array;

/**
 * Shows every registered BoxCache cache with its lifetime statistics and what this request did to it (hits, misses and hit rate during the request).
 * Core announces no cache read events, so the per-request numbers are the difference between the cache statistics at request start and request end.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class CacheCollector extends BaseCollector {

	@Override
	public String id() {
		return "cache";
	}

	private static final long												TTL		= 60_000L;
	/** The sanitized configuration of each cache, with the time it was read. It rarely changes, so it is not rebuilt for every request. */
	private final java.util.concurrent.ConcurrentHashMap<String, Object[]>	configs	= new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public boolean snapshotOnly() {
		return true;
	}

	private Object configOf( String name, ICacheProvider c, Sanitizer clean ) {
		long		now	= System.currentTimeMillis();
		Object[]	hit	= configs.get( name );
		if ( hit == null || now - ( Long ) hit[ 0 ] > TTL ) {
			hit = new Object[] { now, c.getConfig() == null ? null : clean.clean( c.getConfig().properties ) };
			configs.put( name, hit );
		}
		return hit[ 1 ];
	}

	@Override
	public void onRequestStart( LensRequest req ) {
		Map<String, long[]> before = new LinkedHashMap<>();
		for ( Map.Entry<String, ICacheProvider> e : caches().entrySet() ) {
			try {
				ICacheStats s = e.getValue().getStats();
				before.put( e.getKey(), new long[] { s.hits(), s.misses(), s.evictionCount(), s.reapCount() } );
			} catch ( Throwable t ) {
				// Provider without stats
			}
		}
		req.data.put( "cache.before", before );
	}

	@Override
	@SuppressWarnings( "unchecked" )
	public void onRequestFinish( LensRequest req ) {
		Map<String, long[]>	before	= req.data.get( "cache.before" ) instanceof Map<?, ?> m ? ( Map<String, long[]> ) m : Map.of();
		Sanitizer			clean	= new Sanitizer( config() );
		List<Object>		out		= new ArrayList<>();
		for ( Map.Entry<String, ICacheProvider> e : caches().entrySet() ) {
			try {
				ICacheProvider		c	= e.getValue();
				ICacheStats			s	= c.getStats();
				long[]				b	= before.getOrDefault( e.getKey(), new long[] { s.hits(), s.misses(), s.evictionCount(), s.reapCount() } );
				long				dh	= Math.max( 0, s.hits() - b[ 0 ] );
				long				dm	= Math.max( 0, s.misses() - b[ 1 ] );
				Map<String, Object>	m	= new LinkedHashMap<>();
				m.put( "name", e.getKey() );
				m.put( "provider", c.getType() );
				m.put( "enabled", c.isEnabled() );
				m.put( "objects", c.getSize() );
				m.put( "hits", s.hits() );
				m.put( "misses", s.misses() );
				m.put( "hitRate", s.hitRate() );
				m.put( "evictions", s.evictionCount() );
				m.put( "reaps", s.reapCount() );
				m.put( "gcs", s.garbageCollections() );
				m.put( "expired", s.expiredCount() );
				m.put( "requestHits", dh );
				m.put( "requestMisses", dm );
				m.put( "requestHitRate", dh + dm == 0 ? -1 : Math.round( dh * 100.0 / ( dh + dm ) ) );
				m.put( "requestEvictions", Math.max( 0, s.evictionCount() - b[ 2 ] ) );
				m.put( "started", s.started() == null ? "" : s.started().toString() );
				Object cfg = configOf( e.getKey(), c, clean );
				if ( cfg != null ) {
					m.put( "config", cfg );
				}
				out.add( m );
			} catch ( Throwable t ) {
				fail( "stats for " + e.getKey(), t );
			}
		}
		req.data.put( "cache", out );
		req.data.remove( "cache.before" );
	}

	private Map<String, ICacheProvider> caches() {
		Map<String, ICacheProvider> out = new LinkedHashMap<>();
		try {
			CacheService	svc		= BoxRuntime.getInstance().getCacheService();
			Array			names	= svc.getRegisteredCaches();
			for ( Object n : names ) {
				String name = n.toString();
				if ( svc.hasCache( name ) ) {
					out.put( name, svc.getCache( name ) );
				}
			}
		} catch ( Throwable t ) {
			fail( "list caches", t );
		}
		return out;
	}

}
