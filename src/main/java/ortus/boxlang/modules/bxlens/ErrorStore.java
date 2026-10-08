/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Secrets;

/**
 * Requests that went wrong, grouped by what went wrong. An error is the same error when its type, message pattern (numbers, ids and
 * quoted values removed) and top frames match. Each group keeps a count, when it was first and last seen, the most affected URLs and a
 * few full samples with the stack trace and the request context. Parameter values and secrets are redacted before anything is stored.
 * <p>
 * Held in memory. When the Plus disk store is on, the groups are saved with {@link #toPersist()} and restored with {@link #load(List)}.
 */
public final class ErrorStore {

	public static final int							MAX_GROUPS	= 200;
	public static final int							MAX_SAMPLES	= 5;

	private final Map<String, Map<String, Object>>	groups		= new ConcurrentHashMap<>();
	private volatile boolean						dirty;

	/**
	 * Look at a finished request and file every error it had.
	 */
	public void record( LensRequest req, LensConfig cfg ) {
		try {
			List<Map<String, Object>> errors;
			synchronized ( req.exceptions ) {
				errors = new ArrayList<>( req.exceptions );
			}
			if ( errors.isEmpty() && req.status >= 500 ) {
				Map<String, Object> e = new LinkedHashMap<>();
				e.put( "type", "HTTP " + req.status );
				e.put( "message", "The response status was " + req.status );
				e.put( "origin", "status" );
				errors.add( e );
			}
			for ( Map<String, Object> e : errors ) {
				file( req, e, cfg );
			}
		} catch ( Throwable t ) {
			// Error tracking never breaks a request
		}
	}

	@SuppressWarnings( "unchecked" )
	private void file( LensRequest req, Map<String, Object> e, LensConfig cfg ) {
		String						type	= Plain.str( e.get( "type" ) );
		String						message	= Plain.str( e.get( "message" ) );
		List<Map<String, Object>>	frames	= e.get( "frames" ) instanceof List<?> l ? ( List<Map<String, Object>> ) l : List.of();
		String						id		= fingerprint( type, message, frames, req );
		long						now		= System.currentTimeMillis();
		Map<String, Object>			g		= groups.get( id );
		if ( g == null ) {
			if ( groups.size() >= MAX_GROUPS ) {
				groups.entrySet().stream().min( Comparator.comparingLong( x -> Plain.num( x.getValue().get( "lastSeen" ), 0 ) ) )
				    .ifPresent( x -> groups.remove( x.getKey() ) );
			}
			g = new LinkedHashMap<>();
			g.put( "id", id );
			g.put( "type", type );
			g.put( "message", cut( message, 400 ) );
			g.put( "count", 0L );
			g.put( "firstSeen", now );
			g.put( "urls", new LinkedHashMap<String, Object>() );
			g.put( "samples", new ArrayList<Object>() );
			Map<String, Object> prior = groups.putIfAbsent( id, g );
			if ( prior != null ) {
				g = prior;
			}
		}
		synchronized ( g ) {
			g.put( "count", Plain.num( g.get( "count" ), 0 ) + 1 );
			g.put( "lastSeen", now );
			g.put( "lastStatus", req.status );
			g.put( "handled", ! ( "uncaught".equals( e.get( "origin" ) ) || "status".equals( e.get( "origin" ) ) ) );
			g.put( "file", Plain.str( e.get( "file" ) ) );
			g.put( "line", Plain.num( e.get( "line" ), 0 ) );
			Map<String, Object> urls = ( Map<String, Object> ) g.get( "urls" );
			urls.merge( req.method + " " + req.uri, 1L, ( a, b ) -> ( ( Number ) a ).longValue() + 1 );
			if ( urls.size() > 20 ) {
				urls.entrySet().stream().min( Comparator.comparingLong( x -> ( ( Number ) x.getValue() ).longValue() ) )
				    .ifPresent( x -> urls.remove( x.getKey() ) );
			}
			List<Object> samples = ( List<Object> ) g.get( "samples" );
			samples.add( sample( req, e, cfg ) );
			while ( samples.size() > MAX_SAMPLES ) {
				samples.remove( 0 );
			}
		}
		dirty = true;
	}

	private Map<String, Object> sample( LensRequest req, Map<String, Object> e, LensConfig cfg ) {
		Map<String, Object> s = new LinkedHashMap<>();
		s.put( "at", System.currentTimeMillis() );
		s.put( "requestId", req.id );
		s.put( "method", req.method );
		s.put( "uri", req.uri );
		s.put( "query", redactQuery( req.queryString, cfg ) );
		s.put( "status", req.status );
		s.put( "remoteAddr", req.remoteAddr );
		s.put( "userAgent", cut( req.userAgent, 200 ) );
		s.put( "app", req.appName );
		s.put( "template", req.template );
		s.put( "ms", Math.round( req.durationNs() / 10_000.0 ) / 100.0 );
		s.put( "message", cut( Plain.str( e.get( "message" ) ), 1000 ) );
		s.put( "detail", cut( Plain.str( e.get( "detail" ) ), 1000 ) );
		s.put( "origin", e.get( "origin" ) );
		s.put( "frames", e.get( "frames" ) );
		s.put( "java", e.get( "java" ) );
		if ( e.get( "sql" ) != null ) {
			s.put( "sql", e.get( "sql" ) );
		}
		List<String> sql = new ArrayList<>();
		synchronized ( req.queries ) {
			for ( int i = Math.max( 0, req.queries.size() - 3 ); i < req.queries.size(); i++ ) {
				sql.add( cut( Plain.str( req.queries.get( i ).get( "sql" ) ), 300 ) );
			}
		}
		s.put( "lastQueries", sql );
		List<String> msgs = new ArrayList<>();
		synchronized ( req.messages ) {
			for ( int i = Math.max( 0, req.messages.size() - 5 ); i < req.messages.size(); i++ ) {
				msgs.add( cut( Secrets.text( Plain.str( req.messages.get( i ).get( "text" ) ) ), 300 ) );
			}
		}
		s.put( "messages", msgs );
		return s;
	}

	/**
	 * Query string with the values of secret-looking parameters hidden.
	 */
	static String redactQuery( String q, LensConfig cfg ) {
		if ( q == null || q.isEmpty() ) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for ( String part : q.split( "&" ) ) {
			int eq = part.indexOf( '=' );
			if ( out.length() > 0 ) {
				out.append( '&' );
			}
			if ( eq > 0 && ( Secrets.isSecretName( part.substring( 0, eq ) ) || cfg.shouldRedact( part.substring( 0, eq ) ) ) ) {
				out.append( part, 0, eq + 1 ).append( cfg.redactMask );
			} else {
				out.append( part );
			}
		}
		return cut( out.toString(), 500 );
	}

	/**
	 * The same error gets the same id: type, message with numbers and quoted values removed, and the top three frames.
	 */
	static String fingerprint( String type, String message, List<Map<String, Object>> frames, LensRequest req ) {
		String			norm	= message.replaceAll( "'[^']*'|\"[^\"]*\"", "?" ).replaceAll( "[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}", "#" ).replaceAll( "\\d+", "#" )
		    .toLowerCase();
		StringBuilder	sb		= new StringBuilder( type ).append( '|' ).append( cut( norm, 200 ) );
		int				n		= 0;
		for ( Map<String, Object> f : frames ) {
			if ( n++ >= 3 ) {
				break;
			}
			sb.append( '|' ).append( Plain.str( f.get( "file" ) ) ).append( ':' ).append( Plain.str( f.get( "line" ) ) );
		}
		if ( frames.isEmpty() ) {
			sb.append( '|' ).append( req.uri );
		}
		try {
			byte[]			d	= MessageDigest.getInstance( "SHA-1" ).digest( sb.toString().getBytes( StandardCharsets.UTF_8 ) );
			StringBuilder	hex	= new StringBuilder();
			for ( int i = 0; i < 6; i++ ) {
				hex.append( String.format( "%02x", d[ i ] ) );
			}
			return hex.toString();
		} catch ( Exception ex ) {
			return Integer.toHexString( sb.toString().hashCode() );
		}
	}

	private static String cut( String s, int max ) {
		return s == null ? "" : s.length() > max ? s.substring( 0, max ) + "..." : s;
	}

	/**
	 * Groups without samples, newest first, for the list.
	 */
	public Map<String, Object> list() {
		List<Map<String, Object>>	rows	= new ArrayList<>();
		long						total	= 0;
		for ( Map<String, Object> g : groups.values() ) {
			synchronized ( g ) {
				Map<String, Object> m = new LinkedHashMap<>( g );
				m.remove( "samples" );
				m.put( "urlCount", ( ( Map<?, ?> ) g.get( "urls" ) ).size() );
				m.put( "urls", new LinkedHashMap<>( ( Map<?, ?> ) g.get( "urls" ) ) );
				rows.add( m );
				total += Plain.num( g.get( "count" ), 0 );
			}
		}
		rows.sort( Comparator.comparingLong( ( Map<String, Object> m ) -> Plain.num( m.get( "lastSeen" ), 0 ) ).reversed() );
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "groups", rows );
		out.put( "occurrences", total );
		out.put( "max", MAX_GROUPS );
		return out;
	}

	/**
	 * One group with its samples, or null.
	 */
	public Map<String, Object> get( String id ) {
		Map<String, Object> g = groups.get( id );
		if ( g == null ) {
			return null;
		}
		synchronized ( g ) {
			Map<String, Object> m = new LinkedHashMap<>( g );
			m.put( "samples", new ArrayList<>( ( List<?> ) g.get( "samples" ) ) );
			return m;
		}
	}

	public int size() {
		return groups.size();
	}

	public void reset() {
		groups.clear();
		dirty = true;
	}

	// ---- persistence for the Plus disk store ----

	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Everything, for saving. Clears the dirty flag.
	 */
	public List<Map<String, Object>> toPersist() {
		dirty = false;
		List<Map<String, Object>> out = new ArrayList<>();
		for ( Map<String, Object> g : groups.values() ) {
			synchronized ( g ) {
				out.add( deepCopy( g ) );
			}
		}
		return out;
	}

	@SuppressWarnings( "unchecked" )
	private static Map<String, Object> deepCopy( Map<String, Object> g ) {
		return ( Map<String, Object> ) Plain.parse( ortus.boxlang.modules.bxlens.util.Json.write( g ) );
	}

	/**
	 * Restore saved groups, dropping any older than the cutoff.
	 */
	public void load( List<?> saved, long oldestAllowed ) {
		for ( Object o : saved ) {
			Map<String, Object>	g	= Plain.map( o );
			String				id	= Plain.str( g.get( "id" ) );
			if ( id.isEmpty() || Plain.num( g.get( "lastSeen" ), 0 ) < oldestAllowed || groups.size() >= MAX_GROUPS ) {
				continue;
			}
			g.putIfAbsent( "urls", new LinkedHashMap<String, Object>() );
			g.putIfAbsent( "samples", new ArrayList<Object>() );
			groups.put( id, g );
		}
	}

	/**
	 * Drop groups not seen since the cutoff.
	 */
	public void prune( long oldestAllowed ) {
		if ( groups.values().removeIf( g -> Plain.num( g.get( "lastSeen" ), 0 ) < oldestAllowed ) ) {
			dirty = true;
		}
	}

}
