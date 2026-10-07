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
package ortus.boxlang.modules.bxlens.ext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.util.Sanitizer;

/**
 * Fluent builder for a data-only panel contributed by an application or another module. It is available to BoxLang through
 * <code>lensPanel()</code> and <code>event.panel( id )</code> in the <code>onLensCollect</code> interception point.
 * <p>
 * The panel renders with one of the built-in renderers (table, kv, tree, spans, messages, json, text), so a contributing module ships no JavaScript.
 * Every value passes through the {@link Sanitizer}, so redaction and size caps apply to contributions too.
 */
public class LensPanelBuilder {

	private final String					id;
	private volatile String					label;
	private volatile String					renderer;
	private volatile String					icon			= "box";
	private volatile int					order			= 100;
	private volatile int					badgeCount		= -1;
	private volatile String					badgeSeverity	= "none";
	private final Map<String, Object>		content			= new LinkedHashMap<>();
	private final List<Map<String, Object>>	issues			= new ArrayList<>();

	public LensPanelBuilder( String id, String label, String renderer ) {
		this.id			= id;
		this.label		= label == null || label.isBlank() ? id : label;
		this.renderer	= renderer == null || renderer.isBlank() ? "table" : renderer.toLowerCase();
	}

	public String getId() {
		return id;
	}

	/**
	 * Panel title on its tab.
	 */
	public LensPanelBuilder label( String label ) {
		this.label = label;
		return this;
	}

	/**
	 * Icon key from the Lens icon set.
	 */
	public LensPanelBuilder icon( String icon ) {
		this.icon = icon;
		return this;
	}

	/**
	 * Tab order. Built-in tabs use 0-90, custom panels default to 100.
	 */
	public LensPanelBuilder order( int order ) {
		this.order = order;
		return this;
	}

	/**
	 * Tab badge. Severity is none, warn or crit.
	 */
	public LensPanelBuilder badge( int count, String severity ) {
		this.badgeCount		= count;
		this.badgeSeverity	= severity == null ? "none" : severity;
		return this;
	}

	/**
	 * Column titles for the table renderer.
	 */
	public LensPanelBuilder columns( Object columns ) {
		this.renderer = "table";
		content.put( "columns", clean( columns ) );
		return this;
	}

	/**
	 * Rows for the table renderer. Each row is an array of cell values, or a struct keyed by column title.
	 */
	public LensPanelBuilder rows( Object rows ) {
		this.renderer = "table";
		content.put( "rows", clean( rows ) );
		return this;
	}

	/**
	 * Key/value pairs for the kv renderer.
	 */
	public LensPanelBuilder kv( Object pairs ) {
		this.renderer = "kv";
		content.put( "kv", clean( pairs ) );
		return this;
	}

	/**
	 * Nodes for the tree renderer: an array of structs with label, optional ms, and optional children.
	 */
	public LensPanelBuilder tree( Object nodes ) {
		this.renderer = "tree";
		content.put( "tree", clean( nodes ) );
		return this;
	}

	/**
	 * Spans for the waterfall: an array of structs with label, start and dur (milliseconds from request start) and optional type.
	 * These also appear on the Timeline tab with their own filter chip.
	 */
	public LensPanelBuilder spans( Object spans ) {
		this.renderer = "spans";
		content.put( "spans", clean( spans ) );
		return this;
	}

	/**
	 * Messages for the messages renderer: an array of structs with level, text and optional ms.
	 */
	public LensPanelBuilder messages( Object messages ) {
		this.renderer = "messages";
		content.put( "messages", clean( messages ) );
		return this;
	}

	/**
	 * Any value, shown as a collapsible JSON tree.
	 */
	public LensPanelBuilder json( Object value ) {
		this.renderer = "json";
		content.put( "json", clean( value ) );
		return this;
	}

	/**
	 * Plain text, always escaped by the UI.
	 */
	public LensPanelBuilder text( Object text ) {
		this.renderer = "text";
		content.put( "text", clean( text ) );
		return this;
	}

	/**
	 * Report a problem. It appears on the Issues tab and colors the strip.
	 *
	 * @param severity crit or warn
	 */
	public LensPanelBuilder issue( String severity, String title, String detail, String file, Number line ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "severity", "crit".equalsIgnoreCase( severity ) ? "crit" : "warn" );
		m.put( "title", title == null ? "" : title );
		m.put( "detail", detail == null ? "" : detail );
		m.put( "file", file == null ? "" : file );
		m.put( "line", line == null ? 0 : line.intValue() );
		synchronized ( issues ) {
			issues.add( m );
		}
		return this;
	}

	/**
	 * Issues reported through this panel.
	 */
	public List<Map<String, Object>> getIssues() {
		synchronized ( issues ) {
			return new ArrayList<>( issues );
		}
	}

	/**
	 * JSON-ready shape for the UI.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "id", id );
		m.put( "label", label );
		m.put( "icon", icon );
		m.put( "order", order );
		m.put( "renderer", renderer );
		Map<String, Object> badge = new LinkedHashMap<>();
		badge.put( "count", badgeCount );
		badge.put( "severity", badgeSeverity );
		m.put( "badge", badge );
		synchronized ( content ) {
			m.put( "content", new LinkedHashMap<>( content ) );
		}
		return m;
	}

	private Object clean( Object value ) {
		return new Sanitizer( LensService.getInstance().getConfig() ).clean( value );
	}

}
