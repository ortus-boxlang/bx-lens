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
		sb.append( "<div id=\"bxlens\" x-data=\"bxLens()\" x-cloak>" ).append( html ).append( "</div>\n" );
		sb.append( "<script type=\"application/json\" id=\"bxlens-data\">" ).append( payloadJson ).append( "</script>\n" );
		sb.append( "<script id=\"bxlens-js\">" ).append( safeScript( js ) ).append( "</script>\n" );
		// Load Alpine only when the host page does not ship its own. lens.js registers with either.
		sb.append( "<script id=\"bxlens-alpine\">if(!window.Alpine){" ).append( safeScript( alpine ) ).append( "}</script>\n" );
		sb.append( "<!-- /BX Lens -->\n" );
		return sb.toString();
	}

	/**
	 * Insert a block before the last closing body tag, or append when the page has none.
	 *
	 * @return the index it was inserted at
	 */
	public static int insert( StringBuffer buffer, String block ) {
		String	lower	= buffer.toString().toLowerCase( java.util.Locale.ROOT );
		int		idx		= lower.lastIndexOf( "</body>" );
		if ( idx < 0 ) {
			idx = buffer.length();
		}
		buffer.insert( idx, block );
		return idx;
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
