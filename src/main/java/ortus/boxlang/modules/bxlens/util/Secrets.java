/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.modules.bxlens.LensConfig;

/**
 * Hides secrets in text shown for diagnostics: values whose name looks secret, credentials inside URLs, secret parameters and
 * <code>-Dname=value</code> or <code>--flag=value</code> pairs inside a longer value, and encrypted config values. It errs on the side of hiding.
 * <p>
 * This is the one secret-name matcher of Lens. It is hand written (no regular expressions) because it runs on hot paths. A name is secret when
 * its letters and digits, without separators, contain one of the {@link #ROOTS}, or when one of its words (split on separators, camelCase and
 * digits) is one of the {@link #WORDS}. Short words such as <code>key</code> and <code>pin</code> only count as a whole word, so
 * <code>shipping</code> and <code>monkey</code> are not hidden.
 */
public final class Secrets {

	public static final String								HIDDEN		= "[hidden]";

	/** Hidden when they appear anywhere in the name (separators removed, lower case). */
	private static final String[]							ROOTS		= { "password", "passwd", "passphrase", "passcode", "pwd", "pass", "secret", "token",
	    "apikey", "accesskey",
	    "privatekey", "private", "credential", "auth", "cookie", "session", "salt", "seed", "signature", "dsn", "connectionstring", "bearer", "jwt", "csrf",
	    "xsrf", "webhook", "cardnumber", "creditcard", "clientsecret", "truststorepassword", "sslpassword" };

	/** Hidden only as a whole word of the name. */
	private static final Set<String>						WORDS		= Set.of( "key", "keys", "pin", "cvv", "cvc", "jwt", "auth", "dsn", "sk", "pass", "pwd",
	    "sso", "otp" );

	private static final ConcurrentHashMap<String, Boolean>	CACHE		= new ConcurrentHashMap<>();
	private static final int								CACHE_MAX	= 4096;

	private Secrets() {
	}

	/**
	 * Does a name look like it holds a secret?
	 */
	public static boolean isSecretName( String name ) {
		if ( name == null || name.isEmpty() || name.length() > 256 ) {
			return false;
		}
		Boolean known = CACHE.get( name );
		if ( known != null ) {
			return known;
		}
		boolean secret = compute( name );
		if ( CACHE.size() < CACHE_MAX ) {
			CACHE.put( name, secret );
		}
		return secret;
	}

	private static boolean compute( String name ) {
		StringBuilder	flat	= new StringBuilder( name.length() );
		StringBuilder	word	= new StringBuilder( 16 );
		boolean			found	= false;
		for ( int i = 0; i < name.length(); i++ ) {
			char c = name.charAt( i );
			if ( !Character.isLetterOrDigit( c ) ) {
				found |= endWord( word );
				continue;
			}
			char prev = i > 0 ? name.charAt( i - 1 ) : ' ';
			if ( word.length() > 0 ) {
				boolean	lowerToUpper	= Character.isLowerCase( prev ) && Character.isUpperCase( c );
				boolean	upperRunEnds	= Character.isUpperCase( prev ) && Character.isUpperCase( c ) && i + 1 < name.length()
				    && Character.isLowerCase( name.charAt( i + 1 ) );
				boolean	digitEdge		= Character.isDigit( prev ) != Character.isDigit( c );
				if ( lowerToUpper || upperRunEnds || digitEdge ) {
					found |= endWord( word );
				}
			}
			char lower = Character.toLowerCase( c );
			word.append( lower );
			flat.append( lower );
		}
		found |= endWord( word );
		if ( found ) {
			return true;
		}
		String f = flat.toString();
		for ( String root : ROOTS ) {
			if ( f.contains( root ) ) {
				return true;
			}
		}
		return false;
	}

	private static boolean endWord( StringBuilder word ) {
		boolean hit = word.length() > 0 && WORDS.contains( word.toString() );
		word.setLength( 0 );
		return hit;
	}

	/**
	 * The value to show for a name and value.
	 */
	public static String show( String name, String value ) {
		if ( value == null ) {
			return "";
		}
		if ( isSecretName( name ) ) {
			return value.isEmpty() ? "" : HIDDEN;
		}
		return text( value );
	}

	/**
	 * Hide credentials that appear inside a text value: user info in URLs, secret parameters after <code>? ; &amp;</code>, and secret
	 * <code>-Dname=value</code> or <code>--flag=value</code> pairs.
	 */
	public static String text( String value ) {
		if ( value == null ) {
			return "";
		}
		if ( value.regionMatches( true, 0, "bxsecret:", 0, 9 ) ) {
			return "[encrypted]";
		}
		return pairs( params( userInfo( value ) ) );
	}

	/**
	 * A connection URL with the user info removed and secret parameters hidden.
	 */
	public static String url( String url ) {
		return text( url ).replace( "//" + HIDDEN + "@", "//" );
	}

	/**
	 * Query string with the values of secret-looking parameters hidden. Parameters on the redact list of the settings are masked with the
	 * configured mask.
	 *
	 * @param max the longest result in characters
	 */
	public static String redactQuery( String q, LensConfig cfg, int max ) {
		if ( q == null || q.isEmpty() ) {
			return "";
		}
		StringBuilder	out		= new StringBuilder( q.length() );
		int				start	= 0;
		int				n		= q.length();
		while ( start <= n ) {
			int end = q.indexOf( '&', start );
			if ( end < 0 ) {
				end = n;
			}
			int eq = q.indexOf( '=', start );
			if ( out.length() > 0 ) {
				out.append( '&' );
			}
			if ( eq > start && eq < end ) {
				String name = q.substring( start, eq );
				if ( isSecretName( name ) || cfg != null && cfg.shouldRedact( name ) ) {
					out.append( q, start, eq + 1 ).append( cfg == null ? HIDDEN : cfg.redactMask );
				} else {
					out.append( q, start, end );
				}
			} else {
				out.append( q, start, end );
			}
			start = end + 1;
		}
		return out.length() > max ? out.substring( 0, max ) + "..." : out.toString();
	}

	// ---------------------------------------------------------------------------------------------
	// Scanners
	// ---------------------------------------------------------------------------------------------

	/** scheme://user:password@host becomes scheme://[hidden]@host. */
	private static String userInfo( String v ) {
		int at = v.indexOf( "://" );
		if ( at < 0 ) {
			return v;
		}
		StringBuilder	out		= null;
		int				copied	= 0;
		while ( at >= 0 ) {
			int s = at;
			while ( s > 0 && isSchemeChar( v.charAt( s - 1 ) ) ) {
				s--;
			}
			int	auth	= at + 3;
			int	j		= auth;
			while ( j < v.length() ) {
				char c = v.charAt( j );
				if ( c == '/' || c == '@' || Character.isWhitespace( c ) ) {
					break;
				}
				j++;
			}
			if ( s < at && Character.isLetter( v.charAt( s ) ) && j < v.length() && v.charAt( j ) == '@' && j > auth ) {
				if ( out == null ) {
					out = new StringBuilder( v.length() + 16 );
				}
				out.append( v, copied, auth ).append( HIDDEN );
				copied = j;
			}
			at = v.indexOf( "://", Math.max( j, at + 3 ) );
		}
		if ( out == null ) {
			return v;
		}
		return out.append( v, copied, v.length() ).toString();
	}

	private static boolean isSchemeChar( char c ) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '+' || c == '.' || c == '-';
	}

	/** ?name=value, ;name=value and &amp;name=value where the name is secret. */
	private static String params( String v ) {
		StringBuilder	out		= null;
		int				copied	= 0;
		int				n		= v.length();
		for ( int i = 0; i < n; i++ ) {
			char d = v.charAt( i );
			if ( d != '?' && d != ';' && d != '&' ) {
				continue;
			}
			int j = i + 1;
			while ( j < n && j - i <= 64 ) {
				char c = v.charAt( j );
				if ( c == '=' || c == '&' || c == ';' || c == '?' || c == '/' || c == '#' || c == '"' || c == '\'' || Character.isWhitespace( c ) ) {
					break;
				}
				j++;
			}
			if ( j < n && v.charAt( j ) == '=' && j > i + 1 && isSecretName( v.substring( i + 1, j ) ) ) {
				int e = j + 1;
				while ( e < n ) {
					char c = v.charAt( e );
					if ( c == '&' || c == ';' || Character.isWhitespace( c ) ) {
						break;
					}
					e++;
				}
				if ( out == null ) {
					out = new StringBuilder( n + 16 );
				}
				out.append( v, copied, j + 1 ).append( HIDDEN );
				copied	= e;
				i		= e - 1;
			}
		}
		if ( out == null ) {
			return v;
		}
		return out.append( v, copied, n ).toString();
	}

	/** -Dname=value and --flag=value at the start of a word, where the name is secret. The value may be quoted. */
	private static String pairs( String v ) {
		StringBuilder	out		= null;
		int				copied	= 0;
		int				n		= v.length();
		for ( int i = 0; i < n - 2; i++ ) {
			if ( v.charAt( i ) != '-' || i > 0 && !Character.isWhitespace( v.charAt( i - 1 ) ) ) {
				continue;
			}
			int nameStart;
			if ( v.charAt( i + 1 ) == 'D' ) {
				nameStart = i + 2;
			} else if ( v.charAt( i + 1 ) == '-' ) {
				nameStart = i + 2;
			} else {
				continue;
			}
			int j = nameStart;
			while ( j < n && v.charAt( j ) != '=' && !Character.isWhitespace( v.charAt( j ) ) ) {
				j++;
			}
			if ( j >= n || v.charAt( j ) != '=' || j == nameStart || !isSecretName( v.substring( nameStart, j ) ) ) {
				i = Math.max( i, j - 1 );
				continue;
			}
			int e = j + 1;
			if ( e < n && ( v.charAt( e ) == '"' || v.charAt( e ) == '\'' ) ) {
				int close = v.indexOf( v.charAt( e ), e + 1 );
				e = close < 0 ? n : close + 1;
			} else {
				while ( e < n && !Character.isWhitespace( v.charAt( e ) ) ) {
					e++;
				}
			}
			if ( out == null ) {
				out = new StringBuilder( n + 16 );
			}
			out.append( v, copied, j + 1 ).append( HIDDEN );
			copied	= e;
			i		= e - 1;
		}
		if ( out == null ) {
			return v;
		}
		return out.append( v, copied, n ).toString();
	}

}
