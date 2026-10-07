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
				if ( c.getConfig() != null ) {
					m.put( "config", clean.clean( c.getConfig().properties ) );
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
