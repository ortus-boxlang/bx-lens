/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.Locale;

/**
 * Small hand written text scanners for hot paths, so no regular expression is compiled or run per request: whitespace collapsing, masking of
 * literals in SQL, and collapsing of id-like segments in URL paths.
 */
public final class Text {

	private Text() {
	}

	/**
	 * Trim and collapse every run of whitespace to one space.
	 */
	public static String collapseSpaces( String s ) {
		if ( s == null ) {
			return "";
		}
		int		n		= s.length();
		boolean	clean	= n > 0 && !Character.isWhitespace( s.charAt( 0 ) ) && !Character.isWhitespace( s.charAt( n - 1 ) );
		for ( int i = 0; clean && i < n - 1; i++ ) {
			char c = s.charAt( i );
			if ( Character.isWhitespace( c ) && ( c != ' ' || Character.isWhitespace( s.charAt( i + 1 ) ) ) ) {
				clean = false;
			}
		}
		if ( clean ) {
			return s;
		}
		StringBuilder	sb		= new StringBuilder( n );
		boolean			pending	= false;
		for ( int i = 0; i < n; i++ ) {
			char c = s.charAt( i );
			if ( Character.isWhitespace( c ) ) {
				pending = true;
			} else {
				if ( pending && sb.length() > 0 ) {
					sb.append( ' ' );
				}
				pending = false;
				sb.append( c );
			}
		}
		return sb.toString();
	}

	/**
	 * Whitespace collapsed and lower case, so equivalent statements group together.
	 */
	public static String normalizeSql( String sql ) {
		return sql == null ? "" : collapseSpaces( sql ).toLowerCase( Locale.ROOT );
	}

	/**
	 * Replace the string and number literals of a SQL statement with <code>?</code>. Placeholders, identifiers (also quoted ones) and comments
	 * are kept. A string starts at a single quote and ends at the next single quote that is not doubled, or with a backslash escape. A number
	 * is a run of digits that does not touch a letter, underscore, dollar or dot on its left, with an optional fraction and exponent.
	 */
	public static String maskSql( String sql ) {
		if ( sql == null || sql.isEmpty() ) {
			return "";
		}
		int				n		= sql.length();
		StringBuilder	out		= new StringBuilder( n );
		int				i		= 0;
		char			quote	= 0;
		while ( i < n ) {
			char c = sql.charAt( i );
			if ( quote != 0 ) {
				out.append( c );
				if ( c == quote ) {
					if ( i + 1 < n && sql.charAt( i + 1 ) == quote ) {
						out.append( quote );
						i++;
					} else {
						quote = 0;
					}
				}
				i++;
				continue;
			}
			if ( c == '"' || c == '`' ) {
				quote = c;
				out.append( c );
				i++;
			} else if ( c == '[' ) {
				int	close	= sql.indexOf( ']', i );
				int	end		= close < 0 ? n : close + 1;
				out.append( sql, i, end );
				i = end;
			} else if ( c == '\'' ) {
				i++;
				while ( i < n ) {
					char d = sql.charAt( i );
					if ( d == '\\' && i + 1 < n ) {
						i += 2;
						continue;
					}
					if ( d == '\'' ) {
						if ( i + 1 < n && sql.charAt( i + 1 ) == '\'' ) {
							i += 2;
							continue;
						}
						break;
					}
					i++;
				}
				i = Math.min( n, i + 1 );
				out.append( '?' );
			} else if ( c == '-' && i + 1 < n && sql.charAt( i + 1 ) == '-' ) {
				int end = sql.indexOf( '\n', i );
				end = end < 0 ? n : end;
				out.append( sql, i, end );
				i = end;
			} else if ( c == '/' && i + 1 < n && sql.charAt( i + 1 ) == '*' ) {
				int	close	= sql.indexOf( "*/", i + 2 );
				int	end		= close < 0 ? n : close + 2;
				out.append( sql, i, end );
				i = end;
			} else if ( c >= '0' && c <= '9' && !identChar( i > 0 ? sql.charAt( i - 1 ) : ' ' ) ) {
				i = skipNumber( sql, i );
				out.append( '?' );
			} else {
				out.append( c );
				i++;
			}
		}
		return out.toString();
	}

	private static boolean identChar( char c ) {
		return Character.isLetterOrDigit( c ) || c == '_' || c == '$' || c == '.' || c == ':' || c == '@' || c == '#';
	}

	private static int skipNumber( String s, int i ) {
		int n = s.length();
		if ( s.charAt( i ) == '0' && i + 1 < n && ( s.charAt( i + 1 ) == 'x' || s.charAt( i + 1 ) == 'X' ) ) {
			i += 2;
			while ( i < n && Character.digit( s.charAt( i ), 16 ) >= 0 ) {
				i++;
			}
			return i;
		}
		while ( i < n && Character.isDigit( s.charAt( i ) ) ) {
			i++;
		}
		if ( i + 1 < n && s.charAt( i ) == '.' && Character.isDigit( s.charAt( i + 1 ) ) ) {
			i++;
			while ( i < n && Character.isDigit( s.charAt( i ) ) ) {
				i++;
			}
		}
		if ( i < n && ( s.charAt( i ) == 'e' || s.charAt( i ) == 'E' ) ) {
			int j = i + 1;
			if ( j < n && ( s.charAt( j ) == '+' || s.charAt( j ) == '-' ) ) {
				j++;
			}
			if ( j < n && Character.isDigit( s.charAt( j ) ) ) {
				while ( j < n && Character.isDigit( s.charAt( j ) ) ) {
					j++;
				}
				i = j;
			}
		}
		return i;
	}

	/**
	 * Replace every path segment that is a number, a UUID or a long hex id with <code>:id</code>, so <code>/orders/42</code> and
	 * <code>/orders/43</code> are one URL in the reports. The query string is not part of the path.
	 */
	public static String collapsePath( String uri ) {
		if ( uri == null || uri.isEmpty() ) {
			return "";
		}
		int				n		= uri.length();
		StringBuilder	out		= null;
		int				copied	= 0;
		int				start	= 0;
		while ( start <= n ) {
			int end = uri.indexOf( '/', start );
			if ( end < 0 ) {
				end = n;
			}
			if ( idLike( uri, start, end ) ) {
				if ( out == null ) {
					out = new StringBuilder( n );
				}
				out.append( uri, copied, start ).append( ":id" );
				copied = end;
			}
			start = end + 1;
		}
		if ( out == null ) {
			return uri;
		}
		return out.append( uri, copied, n ).toString();
	}

	private static boolean idLike( String s, int from, int to ) {
		int len = to - from;
		if ( len == 0 ) {
			return false;
		}
		boolean allDigits = true;
		for ( int i = from; i < to; i++ ) {
			if ( !Character.isDigit( s.charAt( i ) ) ) {
				allDigits = false;
				break;
			}
		}
		if ( allDigits ) {
			return true;
		}
		// 8-4-4-4-12 UUID
		if ( len == 36 && s.charAt( from + 8 ) == '-' && s.charAt( from + 13 ) == '-' && s.charAt( from + 18 ) == '-' && s.charAt( from + 23 ) == '-' ) {
			for ( int i = from; i < to; i++ ) {
				int k = i - from;
				if ( k == 8 || k == 13 || k == 18 || k == 23 ) {
					continue;
				}
				if ( Character.digit( s.charAt( i ), 16 ) < 0 ) {
					return false;
				}
			}
			return true;
		}
		// 16 or more hex characters that contain at least one digit: a hash, a Mongo id or a UUID without dashes
		if ( len >= 16 ) {
			boolean digit = false;
			for ( int i = from; i < to; i++ ) {
				char c = s.charAt( i );
				if ( Character.digit( c, 16 ) < 0 ) {
					return false;
				}
				digit |= c >= '0' && c <= '9';
			}
			return digit;
		}
		return false;
	}

	/**
	 * Error message with numbers, ids and quoted values replaced and in lower case, so the same error groups together.
	 */
	public static String generalizeMessage( String message ) {
		if ( message == null || message.isEmpty() ) {
			return "";
		}
		int				n	= message.length();
		StringBuilder	out	= new StringBuilder( n );
		int				i	= 0;
		while ( i < n ) {
			char c = message.charAt( i );
			if ( c == '\'' || c == '"' ) {
				int close = message.indexOf( c, i + 1 );
				if ( close > 0 ) {
					out.append( '?' );
					i = close + 1;
					continue;
				}
			}
			if ( i + 36 <= n && message.charAt( i + 8 ) == '-' && Character.digit( c, 16 ) >= 0 && idLike( message, i, i + 36 ) ) {
				out.append( '#' );
				i += 36;
				continue;
			}
			if ( Character.isDigit( c ) ) {
				while ( i < n && Character.isDigit( message.charAt( i ) ) ) {
					i++;
				}
				out.append( '#' );
				continue;
			}
			out.append( Character.toLowerCase( c ) );
			i++;
		}
		return out.toString();
	}

}
