/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import ortus.boxlang.runtime.BoxRuntime;

/**
 * The BoxLang log files for the console: a list of every file in the logs directory, the tail of one file with a text search and a level
 * filter, the new lines since an offset (for the live tail), and the file itself for download. A file is only ever resolved from a name
 * inside the logs directory, after symbolic links are resolved, so nothing outside the directory can be read.
 */
public final class LogData {

	public static final int				MAX_LINES	= 2000;
	private static final long			MAX_TAIL	= 8L * 1024 * 1024;
	private static final long			MAX_SCAN	= 8L * 1024 * 1024;
	private static final int			BLOCK		= 64 * 1024;
	/** How many reads of a log file may run at once. A search can touch up to {@link #MAX_SCAN} bytes, so a few are enough. */
	public static final int				MAX_READS	= 2;
	private static final int			MAX_LINE	= 4000;
	private static final List<String>	LEVELS		= List.of( "TRACE", "DEBUG", "INFO", "WARN", "ERROR" );

	private final Path					fixed;
	private final Semaphore				reads		= new Semaphore( MAX_READS );

	/**
	 * Thrown when too many reads are running. The console answers 429.
	 */
	public static final class Busy extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public Busy() {
			super( "Too many log reads are running. Try again in a moment.", null, false, false );
		}
	}

	/**
	 * The permits for reads, for tests that need to hold them.
	 */
	Semaphore reads() {
		return reads;
	}

	public LogData() {
		this( null );
	}

	/**
	 * @param fixed a logs directory to use instead of the BoxLang one, for tests
	 */
	public LogData( Path fixed ) {
		this.fixed = fixed;
	}

	/**
	 * The logs directory.
	 */
	public Path dir() {
		try {
			if ( fixed != null ) {
				return fixed.toRealPath();
			}
			return Path.of( BoxRuntime.getInstance().getLoggingService().getLogsDirectory() ).toRealPath();
		} catch ( Throwable t ) {
			return null;
		}
	}

	/**
	 * Every regular file under the logs directory (two levels deep), newest first.
	 */
	public Map<String, Object> list() {
		Path						root	= dir();
		List<Map<String, Object>>	files	= new ArrayList<>();
		if ( root != null && Files.isDirectory( root ) ) {
			try ( Stream<Path> walk = Files.walk( root, 2 ) ) {
				walk.filter( Files::isRegularFile ).forEach( p -> {
					try {
						Map<String, Object> m = new LinkedHashMap<>();
						m.put( "name", root.relativize( p ).toString().replace( '\\', '/' ) );
						m.put( "size", Files.size( p ) );
						m.put( "modified", Files.getLastModifiedTime( p ).toMillis() );
						files.add( m );
					} catch ( IOException e ) {
						// Skip a file that vanished
					}
				} );
			} catch ( IOException e ) {
				// Return what we have
			}
		}
		files.sort( Comparator.comparingLong( ( Map<String, Object> m ) -> ( Long ) m.get( "modified" ) ).reversed() );
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "dir", root == null ? "" : root.toString() );
		out.put( "files", files );
		return out;
	}

	/**
	 * Resolve a name from the listing to a file, or null when it is not a regular file inside the logs directory.
	 */
	public Path resolve( String name ) {
		Path root = dir();
		if ( root == null || name == null || name.isBlank() || name.contains( "\0" ) ) {
			return null;
		}
		try {
			Path p = root.resolve( name ).normalize().toRealPath();
			return p.startsWith( root ) && Files.isRegularFile( p ) ? p : null;
		} catch ( Throwable t ) {
			return null;
		}
	}

	/**
	 * The last lines of a file that match. The file is read from its end in blocks, line by line, and reading stops as soon as enough lines
	 * were found or {@link #MAX_SCAN} bytes were looked at, so a search never loads a whole file.
	 *
	 * @param lines how many lines at most (up to {@link #MAX_LINES})
	 * @param query text a line must contain, case insensitive, or empty
	 * @param level the lowest level to show (TRACE to ERROR), or empty for all. A line without a level (a stack trace) follows the line before it
	 *
	 * @throws Busy when {@link #MAX_READS} reads are already running
	 */
	public Map<String, Object> read( String name, int lines, String query, String level ) throws IOException {
		Path p = resolve( name );
		if ( p == null ) {
			return null;
		}
		if ( !reads.tryAcquire() ) {
			throw new Busy();
		}
		try {
			return readTail( p, name, lines, query, level );
		} finally {
			reads.release();
		}
	}

	private Map<String, Object> readTail( Path p, String name, int lines, String query, String level ) throws IOException {
		int				max		= Math.max( 1, Math.min( lines, MAX_LINES ) );
		String			q		= query == null ? "" : query.toLowerCase( Locale.ROOT );
		int				min		= levelIndex( level );
		boolean			filter	= !q.isEmpty() || min > 0;
		long			size	= Files.size( p );
		long			budget	= Math.min( size, filter ? MAX_SCAN : MAX_TAIL );
		long			stop	= size - budget;
		// Lines are collected newest first; a group is a line with a level and the lines under it that have none
		List<String>	found	= new ArrayList<>();
		List<String>	group	= new ArrayList<>();
		boolean			full	= false;
		boolean			first	= true;
		try ( RandomAccessFile f = new RandomAccessFile( p.toFile(), "r" ) ) {
			long	pos		= size;
			byte[]	carry	= new byte[ 0 ];
			while ( pos > stop && !full ) {
				int		len		= ( int ) Math.min( BLOCK, pos - stop );
				byte[]	block	= new byte[ len + carry.length ];
				f.seek( pos - len );
				f.readFully( block, 0, len );
				System.arraycopy( carry, 0, block, len, carry.length );
				pos -= len;
				int end = block.length;
				for ( int i = block.length - 1; i >= 0 && !full; i-- ) {
					if ( block[ i ] != '\n' ) {
						continue;
					}
					if ( first && i == end - 1 ) {
						// The newline that ends the file
						first	= false;
						end		= i;
						continue;
					}
					first	= false;
					full	= line( block, i + 1, end, group, found, q, min, max );
					end		= i;
				}
				carry = java.util.Arrays.copyOfRange( block, 0, end );
				if ( carry.length > 4 * MAX_LINE ) {
					carry = java.util.Arrays.copyOfRange( carry, carry.length - 4 * MAX_LINE, carry.length );
				}
			}
			if ( !full && size > 0 && pos <= stop && stop == 0 ) {
				// The first line of the file
				full = line( carry, 0, carry.length, group, found, q, min, max );
			}
			if ( !full && size > 0 && stop == 0 ) {
				flush( group, found, q, min, 0, max );
			}
		}
		boolean more = found.size() > max || stop > 0 || full;
		java.util.Collections.reverse( found );
		List<String>		out	= found.size() > max ? new ArrayList<>( found.subList( found.size() - max, found.size() ) ) : found;
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "name", name );
		m.put( "lines", out );
		m.put( "offset", size );
		m.put( "size", size );
		m.put( "cut", more );
		return m;
	}

	/**
	 * Take one line (bytes from to end, going backwards). A line without a level joins the group under the line above it, which is not read
	 * yet; a line with a level closes the group.
	 *
	 * @return true when enough lines were found
	 */
	private boolean line( byte[] b, int from, int end, List<String> group, List<String> found, String q, int min, int max ) {
		if ( end > from && b[ end - 1 ] == '\r' ) {
			end--;
		}
		String	l	= new String( b, from, Math.max( 0, end - from ), StandardCharsets.UTF_8 );
		int		lv	= levelOf( l );
		group.add( l.length() > MAX_LINE ? l.substring( 0, MAX_LINE ) + "..." : l );
		if ( lv < 0 ) {
			return false;
		}
		return flush( group, found, q, min, lv, max );
	}

	/** The group holds the lines of one entry, last line first. */
	private boolean flush( List<String> group, List<String> found, String q, int min, int level, int max ) {
		if ( level >= min ) {
			for ( String l : group ) {
				if ( q.isEmpty() || l.toLowerCase( Locale.ROOT ).contains( q ) ) {
					found.add( l );
				}
			}
		}
		group.clear();
		return found.size() > max;
	}

	/**
	 * Lines added after an offset, for the live tail. Returns the new offset. If the file shrank (rotated) it starts again from the top.
	 */
	public Map<String, Object> since( String name, long offset ) throws IOException {
		Path p = resolve( name );
		if ( p == null ) {
			return null;
		}
		long			size	= Files.size( p );
		boolean			reset	= offset > size;
		long			from	= reset ? 0 : Math.max( offset, size - 256 * 1024 );
		List<String>	lines	= new ArrayList<>();
		if ( size > from ) {
			try ( RandomAccessFile f = new RandomAccessFile( p.toFile(), "r" ) ) {
				f.seek( from );
				byte[] buf = new byte[ ( int ) ( size - from ) ];
				f.readFully( buf );
				for ( String l : splitLines( new String( buf, StandardCharsets.UTF_8 ) ) ) {
					lines.add( l.length() > MAX_LINE ? l.substring( 0, MAX_LINE ) + "..." : l );
				}
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "lines", lines );
		m.put( "offset", size );
		m.put( "rotated", reset );
		return m;
	}

	/** Split on line breaks (\n or \r\n), dropping the empty text after a final break. */
	static List<String> splitLines( String text ) {
		List<String>	out		= new ArrayList<>();
		int				start	= 0;
		int				n		= text.length();
		while ( start < n ) {
			int end = text.indexOf( '\n', start );
			if ( end < 0 ) {
				end = n;
			}
			int e = end > start && text.charAt( end - 1 ) == '\r' ? end - 1 : end;
			out.add( text.substring( start, e ) );
			start = end + 1;
		}
		return out;
	}

	/**
	 * The level index of a log line that has <code>[LEVEL]</code> in it (blanks allowed inside the brackets), or -1.
	 */
	static int levelOf( String l ) {
		int at = l.indexOf( '[' );
		while ( at >= 0 ) {
			int i = at + 1;
			while ( i < l.length() && Character.isWhitespace( l.charAt( i ) ) ) {
				i++;
			}
			for ( int k = 0; k < LEVELS.size(); k++ ) {
				String lv = LEVELS.get( k );
				if ( l.startsWith( lv, i ) ) {
					int j = i + lv.length();
					while ( j < l.length() && Character.isWhitespace( l.charAt( j ) ) ) {
						j++;
					}
					if ( j < l.length() && l.charAt( j ) == ']' ) {
						return k;
					}
				}
			}
			at = l.indexOf( '[', at + 1 );
		}
		return -1;
	}

	private static int levelIndex( String level ) {
		if ( level == null ) {
			return 0;
		}
		int i = LEVELS.indexOf( level.trim().toUpperCase( Locale.ROOT ) );
		return Math.max( 0, i );
	}

}
