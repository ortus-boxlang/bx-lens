/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
	private static final long			MAX_SCAN	= 64L * 1024 * 1024;
	private static final int			MAX_LINE	= 4000;
	private static final Pattern		LEVEL		= Pattern.compile( "\\[\\s*(TRACE|DEBUG|INFO|WARN|ERROR)\\s*]" );
	private static final List<String>	LEVELS		= List.of( "TRACE", "DEBUG", "INFO", "WARN", "ERROR" );

	private final Path					fixed;

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
	 * The last lines of a file that match.
	 *
	 * @param lines how many lines at most (up to {@link #MAX_LINES})
	 * @param query text a line must contain, case insensitive, or empty
	 * @param level the lowest level to show (TRACE to ERROR), or empty for all. A line without a level (a stack trace) follows the line before it
	 */
	public Map<String, Object> read( String name, int lines, String query, String level ) throws IOException {
		Path p = resolve( name );
		if ( p == null ) {
			return null;
		}
		int		max		= Math.max( 1, Math.min( lines, MAX_LINES ) );
		String	q		= query == null ? "" : query.toLowerCase( Locale.ROOT );
		int		min		= levelIndex( level );
		boolean	filter	= !q.isEmpty() || min > 0;
		long	size	= Files.size( p );
		// Read a window from the end: more when searching, so a match further back can be found
		long	window	= Math.min( size, filter ? MAX_SCAN : MAX_TAIL );
		String	text;
		try ( RandomAccessFile f = new RandomAccessFile( p.toFile(), "r" ) ) {
			f.seek( size - window );
			byte[] buf = new byte[ ( int ) Math.min( window, Integer.MAX_VALUE - 16 ) ];
			f.readFully( buf );
			text = new String( buf, StandardCharsets.UTF_8 );
		}
		String[]		all		= text.split( "\r?\n", -1 );
		int				start	= window < size ? 1 : 0;
		List<String>	out		= new ArrayList<>();
		int				cur		= 0;
		for ( int i = start; i < all.length; i++ ) {
			String l = all[ i ];
			if ( l.isEmpty() && i == all.length - 1 ) {
				continue;
			}
			Matcher m = LEVEL.matcher( l );
			if ( m.find() ) {
				cur = LEVELS.indexOf( m.group( 1 ) );
			}
			if ( min > 0 && cur < min ) {
				continue;
			}
			if ( !q.isEmpty() && !l.toLowerCase( Locale.ROOT ).contains( q ) ) {
				continue;
			}
			out.add( l.length() > MAX_LINE ? l.substring( 0, MAX_LINE ) + "..." : l );
		}
		boolean more = out.size() > max;
		if ( more ) {
			out = out.subList( out.size() - max, out.size() );
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "name", name );
		m.put( "lines", out );
		m.put( "offset", size );
		m.put( "size", size );
		m.put( "cut", more || window < size );
		return m;
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
				for ( String l : new String( buf, StandardCharsets.UTF_8 ).split( "\r?\n" ) ) {
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

	private static int levelIndex( String level ) {
		if ( level == null ) {
			return 0;
		}
		int i = LEVELS.indexOf( level.trim().toUpperCase( Locale.ROOT ) );
		return Math.max( 0, i );
	}

}
