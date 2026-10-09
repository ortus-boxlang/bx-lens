/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the HTML block injected into a page and inserts it before the closing body tag.
 * <p>
 * The block is small (about a kilobyte): a stylesheet link, an empty element, the request payload as an inert JSON script and a deferred script.
 * The styles, the script, Alpine.js, the markup and the icons are separate files, served by the console route <code>assets/</code> with a content
 * hash
 * in the URL, so the browser fetches each once and keeps it. The files hold no data of any request and no secret.
 */
public final class BarRenderer {

	/** Where the files are served from, relative to the server root. */
	public static final String			ASSET_BASE	= "/~bxlens/index.bxm/assets/";

	/** The files of the bar. */
	public static final List<String>	NAMES		= List.of( "lens.css", "lens.js", "lens.html", "bar-icons.svg", "alpine.min.js" );

	/**
	 * One file of the bar: its text and the hash of its text.
	 */
	public record Asset( String name, String text, String hash, String type ) {
	}

	private final Path					assetsDir;
	private final boolean				reload;
	private volatile Map<String, Asset>	assets	= Map.of();

	/**
	 * @param assetsDir folder holding lens.css, lens.html, lens.js, bar-icons.svg and alpine.min.js
	 * @param reload    read the files and compute their hashes again on every render, for working on the UI. Such files are never cached by the browser
	 */
	public BarRenderer( Path assetsDir, boolean reload ) {
		this.assetsDir	= assetsDir;
		this.reload		= reload;
		load();
	}

	private synchronized void load() {
		Map<String, Asset> next = new LinkedHashMap<>();
		for ( String n : NAMES ) {
			String text = read( assetsDir, n );
			next.put( n, new Asset( n, text, hash( text ), typeOf( n ) ) );
		}
		this.assets = next;
	}

	/**
	 * Is this the name of a file of the bar?
	 */
	public static boolean isBarAsset( String name ) {
		return NAMES.contains( name );
	}

	/**
	 * A file of the bar, or null when the name is not one of them or the file is empty. Re-read when the renderer reloads.
	 */
	public Asset asset( String name ) {
		if ( reload ) {
			load();
		}
		Asset a = assets.get( name );
		return a == null || a.text().isEmpty() ? null : a;
	}

	/**
	 * Does the browser keep this URL for a year? Only when the renderer does not reload and the URL carries the current hash.
	 */
	public boolean immutable( String name, String version ) {
		Asset a = assets.get( name );
		return !reload && a != null && a.hash().equals( version );
	}

	/**
	 * Render the block.
	 *
	 * @param payloadJson JSON for the page, already safe for embedding in a script element
	 */
	public String render( String payloadJson ) {
		if ( reload ) {
			// Contributor mode: edit the assets and refresh the page
			load();
		}
		Map<String, Asset>	a	= assets;
		StringBuilder		sb	= new StringBuilder( payloadJson.length() + 1024 );
		sb.append( "\n<!-- BX Lens -->\n" );
		sb.append( "<link rel=\"stylesheet\" id=\"bxlens-css\" href=\"" ).append( url( a, "lens.css" ) ).append( "\">\n" );
		sb.append( "<div id=\"bxlens\" x-cloak data-base=\"" ).append( ASSET_BASE ).append( "\" data-html=\"" ).append( a.get( "lens.html" ).hash() )
		    .append( "\" data-icons=\"" ).append( a.get( "bar-icons.svg" ).hash() ).append( "\" data-alpine=\"" ).append( a.get( "alpine.min.js" ).hash() )
		    .append( "\"></div>\n" );
		sb.append( "<script type=\"application/json\" id=\"bxlens-data\">" ).append( payloadJson ).append( "</script>\n" );
		// Alpine is loaded by lens.js, and only when the host page has none of its own
		sb.append( "<script defer id=\"bxlens-js\" src=\"" ).append( url( a, "lens.js" ) ).append( "\"></script>\n" );
		sb.append( "<!-- /BX Lens -->\n" );
		return sb.toString();
	}

	private static String url( Map<String, Asset> a, String name ) {
		return ASSET_BASE + name + "?v=" + a.get( name ).hash();
	}

	private static final String	BODY_CLOSE	= "</body>";
	/** How much of the end of the page is searched for the closing body tag. */
	static final int			TAIL		= 1024;

	/**
	 * Insert a block before the closing body tag (any case) when it is in the last kilobyte of the page, else append at the end. The page is
	 * never scanned or copied as a whole.
	 *
	 * @return the index it was inserted at
	 */
	public static int insert( StringBuffer buffer, String block ) {
		int idx = lastBodyClose( buffer );
		if ( idx < 0 ) {
			idx = buffer.length();
		}
		buffer.insert( idx, block );
		return idx;
	}

	/**
	 * Index of the last case insensitive closing body tag within the tail of the buffer, or -1.
	 */
	static int lastBodyClose( StringBuffer b ) {
		int	n		= b.length();
		int	tagLen	= BODY_CLOSE.length();
		int	from	= Math.max( 0, n - TAIL );
		if ( n - from < tagLen ) {
			return -1;
		}
		char[] buf = new char[ n - from ];
		b.getChars( from, n, buf, 0 );
		for ( int at = buf.length - tagLen; at >= 0; at-- ) {
			if ( buf[ at ] == '<' && closes( buf, at ) ) {
				return from + at;
			}
		}
		return -1;
	}

	private static boolean closes( char[] buf, int at ) {
		for ( int i = 1; i < BODY_CLOSE.length(); i++ ) {
			if ( Character.toLowerCase( buf[ at + i ] ) != BODY_CLOSE.charAt( i ) ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Is the asset set complete? Used by the service to warn at startup.
	 */
	public boolean isComplete() {
		for ( Asset x : assets.values() ) {
			if ( x.text().isEmpty() ) {
				return false;
			}
		}
		return !assets.isEmpty();
	}

	private static String typeOf( String name ) {
		if ( name.endsWith( ".css" ) ) {
			return "text/css; charset=UTF-8";
		} else if ( name.endsWith( ".js" ) ) {
			return "text/javascript; charset=UTF-8";
		} else if ( name.endsWith( ".svg" ) ) {
			return "image/svg+xml; charset=UTF-8";
		}
		return "text/html; charset=UTF-8";
	}

	private static String hash( String text ) {
		try {
			byte[]			d	= MessageDigest.getInstance( "SHA-256" ).digest( text.getBytes( StandardCharsets.UTF_8 ) );
			StringBuilder	sb	= new StringBuilder( 12 );
			for ( int i = 0; i < 6; i++ ) {
				sb.append( Character.forDigit( d[ i ] >> 4 & 15, 16 ) ).append( Character.forDigit( d[ i ] & 15, 16 ) );
			}
			return sb.toString();
		} catch ( Exception e ) {
			return Integer.toHexString( text.hashCode() );
		}
	}

	private static String read( Path dir, String name ) {
		try {
			return Files.readString( dir.resolve( name ), StandardCharsets.UTF_8 );
		} catch ( IOException e ) {
			return "";
		}
	}

}
