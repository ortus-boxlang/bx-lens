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

/**
 * Builds the HTML block injected into a page and inserts it before the closing body tag.
 * The block carries the request payload as an inert JSON script plus the bar's CSS and JavaScript, so a page needs no extra request.
 */
public final class BarRenderer {

	private final Path		assetsDir;
	private final boolean	reload;
	private String			css;
	private String			html;
	private String			js;
	private String			alpine;
	private String			icons;

	/**
	 * @param assetsDir folder holding lens.css, lens.html, lens.js and alpine.min.js
	 * @param reload    re-read the files on every render, for working on the UI
	 */
	public BarRenderer( Path assetsDir, boolean reload ) {
		this.assetsDir	= assetsDir;
		this.reload		= reload;
		load();
	}

	private void load() {
		this.css	= read( assetsDir, "lens.css" );
		this.html	= read( assetsDir, "lens.html" );
		this.js		= read( assetsDir, "lens.js" );
		this.alpine	= read( assetsDir, "alpine.min.js" );
		this.icons	= read( assetsDir, "bar-icons.svg" );
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
		StringBuilder sb = new StringBuilder( css.length() + html.length() + js.length() + alpine.length() + payloadJson.length() + 512 );
		sb.append( "\n<!-- BX Lens -->\n" );
		sb.append( "<style id=\"bxlens-css\">" ).append( css ).append( "</style>\n" );
		sb.append( "<div id=\"bxlens\" x-data=\"bxLens()\" x-cloak>" ).append( icons ).append( html ).append( "</div>\n" );
		sb.append( "<script type=\"application/json\" id=\"bxlens-data\">" ).append( payloadJson ).append( "</script>\n" );
		sb.append( "<script id=\"bxlens-js\">" ).append( safeScript( js ) ).append( "</script>\n" );
		// Load Alpine only when the host page does not ship its own. lens.js registers with either.
		sb.append( "<script id=\"bxlens-alpine\">if(!window.Alpine){" ).append( safeScript( alpine ) ).append( "}</script>\n" );
		sb.append( "<!-- /BX Lens -->\n" );
		return sb.toString();
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
		return !css.isEmpty() && !html.isEmpty() && !js.isEmpty() && !alpine.isEmpty();
	}

	// A literal closing script tag inside a script would end the element early
	private static String safeScript( String src ) {
		return src.replace( "</script", "<\\/script" );
	}

	private static String read( Path dir, String name ) {
		try {
			return Files.readString( dir.resolve( name ), StandardCharsets.UTF_8 );
		} catch ( IOException e ) {
			return "";
		}
	}

}
