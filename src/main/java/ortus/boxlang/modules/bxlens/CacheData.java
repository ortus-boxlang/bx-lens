/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import ortus.boxlang.modules.bxlens.util.Json;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.cache.util.ICacheStats;
import ortus.boxlang.runtime.services.CacheService;
import ortus.boxlang.runtime.types.IStruct;

/**
 * BoxCache caches for the console: statistics, a capped key list with a filter, one value on request, and clear, evict and reap.
 * Keys are listed up to a limit and values are cut to a few KB, so a large cache can never blow up the page.
 */
public final class CacheData {

	public static final int		MAX_KEYS	= 100;
	public static final int		MAX_VALUE	= 2048;

	private final LensService	service;

	public CacheData( LensService service ) {
		this.service = service;
	}

	private CacheService svc() {
		return BoxRuntime.getInstance().getCacheService();
	}

	private ICacheProvider find( String name ) {
		try {
			for ( Object n : svc().getRegisteredCaches() ) {
				if ( n.toString().equalsIgnoreCase( name ) && svc().hasCache( n.toString() ) ) {
					return svc().getCache( n.toString() );
				}
			}
		} catch ( Throwable t ) {
			// Unknown cache
		}
		return null;
	}

	/**
	 * Every cache with its statistics.
	 */
	public Map<String, Object> list() {
		Sanitizer					clean	= new Sanitizer( service.getConfig() );
		List<Map<String, Object>>	out		= new ArrayList<>();
		try {
			for ( Object n : svc().getRegisteredCaches() ) {
				String name = n.toString();
				if ( !svc().hasCache( name ) ) {
					continue;
				}
				try {
					ICacheProvider		c	= svc().getCache( name );
					ICacheStats			s	= c.getStats();
					Map<String, Object>	m	= new LinkedHashMap<>();
					m.put( "name", name );
					m.put( "provider", c.getType() );
					m.put( "enabled", c.isEnabled() );
					m.put( "objects", c.getSize() );
					m.put( "hits", s.hits() );
					m.put( "misses", s.misses() );
					m.put( "hitRate", s.hitRate() );
					m.put( "evictions", s.evictionCount() );
					m.put( "reaps", s.reapCount() );
					m.put( "expired", s.expiredCount() );
					m.put( "gcs", s.garbageCollections() );
					m.put( "started", s.started() == null ? "" : s.started().toString() );
					m.put( "lastReap", s.lastReapDatetime() == null ? "" : s.lastReapDatetime().toString() );
					if ( c.getConfig() != null ) {
						m.put( "config", clean.clean( c.getConfig().properties ) );
					}
					out.add( m );
				} catch ( Throwable t ) {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put( "name", name );
					m.put( "error", String.valueOf( t.getMessage() ) );
					out.add( m );
				}
			}
		} catch ( Throwable t ) {
			// Return what we have
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "caches", out );
		m.put( "maxKeys", MAX_KEYS );
		return m;
	}

	/**
	 * Up to {@link #MAX_KEYS} keys that contain the filter text (case insensitive).
	 */
	public Map<String, Object> keys( String name, String filter ) {
		ICacheProvider c = find( name );
		if ( c == null ) {
			return null;
		}
		String			f	= filter == null ? "" : filter.toLowerCase( Locale.ROOT );
		List<String>	keys;
		try ( var stream = c.getKeysStream() ) {
			keys = stream.filter( k -> f.isEmpty() || k.toLowerCase( Locale.ROOT ).contains( f ) ).limit( MAX_KEYS + 1L ).collect( Collectors.toList() );
		}
		boolean						more	= keys.size() > MAX_KEYS;
		List<Map<String, Object>>	rows	= new ArrayList<>();
		for ( String k : keys.subList( 0, Math.min( keys.size(), MAX_KEYS ) ) ) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put( "key", k );
			try {
				IStruct md = c.getCachedObjectMetadata( k );
				if ( md != null ) {
					for ( String field : List.of( "hits", "created", "lastAccessed", "timeout", "lastAccessTimeout", "isExpired" ) ) {
						Object v = md.get( ortus.boxlang.runtime.scopes.Key.of( field ) );
						if ( v != null ) {
							r.put( field, v instanceof Number || v instanceof Boolean ? v : v.toString() );
						}
					}
				}
			} catch ( Throwable t ) {
				// Metadata is optional
			}
			rows.add( r );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "name", name );
		m.put( "keys", rows );
		m.put( "truncated", more );
		m.put( "limit", MAX_KEYS );
		m.put( "total", c.getSize() );
		return m;
	}

	/**
	 * One value, read without touching the statistics, redacted by key name and cut to {@link #MAX_VALUE} characters.
	 */
	public Map<String, Object> value( String name, String key ) {
		ICacheProvider c = find( name );
		if ( c == null ) {
			return null;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "key", key );
		try {
			var attempt = c.getQuiet( key );
			if ( attempt == null || attempt.isEmpty() ) {
				m.put( "found", false );
				return m;
			}
			Object	v		= attempt.get();
			String	text	= Json.write( new Sanitizer( service.getConfig() ).clean( v ) );
			m.put( "found", true );
			m.put( "type", v == null ? "null" : v.getClass().getSimpleName() );
			m.put( "truncated", text.length() > MAX_VALUE );
			m.put( "value", text.length() > MAX_VALUE ? text.substring( 0, MAX_VALUE ) : text );
		} catch ( Throwable t ) {
			m.put( "found", false );
			m.put( "error", String.valueOf( t.getMessage() ) );
		}
		return m;
	}

	/**
	 * clear, evict or reap.
	 *
	 * @return a result map, or null when the cache is unknown
	 */
	public Map<String, Object> action( String name, String action, String key ) {
		ICacheProvider c = find( name );
		if ( c == null ) {
			return null;
		}
		Map<String, Object>	m		= new LinkedHashMap<>();
		int					before	= c.getSize();
		try {
			switch ( action ) {
				case "clear" :
					c.clearAll();
					break;
				case "evict" :
					m.put( "evicted", key != null && c.clear( key ) );
					break;
				case "reap" :
					c.reap();
					break;
				default :
					m.put( "ok", false );
					m.put( "message", "Unknown action" );
					return m;
			}
			m.put( "ok", true );
			m.put( "removed", Math.max( 0, before - c.getSize() ) );
		} catch ( Throwable t ) {
			m.put( "ok", false );
			m.put( "message", String.valueOf( t.getMessage() ) );
		}
		return m;
	}

}
