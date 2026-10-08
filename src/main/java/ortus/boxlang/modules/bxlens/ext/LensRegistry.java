/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passed to the <code>onLensRegister</code> interception point so applications and modules can declare their panels once.
 * A declared panel keeps its tab (label, icon, order) even on requests where it has no data.
 */
public class LensRegistry {

	private final Map<String, Map<String, Object>> panels = new LinkedHashMap<>();

	/**
	 * Declare a panel.
	 *
	 * @param id       unique id, also used for the tab hotkey order
	 * @param label    tab title
	 * @param icon     icon key from the Lens icon set (box, database, cache, globe, bolt, mail, cpu, list)
	 * @param order    tab order, built-in tabs use 0-90
	 * @param renderer table, kv, tree, spans, messages, json or text
	 */
	public LensRegistry panel( String id, String label, String icon, Number order, String renderer ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "id", id );
		m.put( "label", label == null || label.isBlank() ? id : label );
		m.put( "icon", icon == null || icon.isBlank() ? "box" : icon );
		m.put( "order", order == null ? 100 : order.intValue() );
		m.put( "renderer", renderer == null || renderer.isBlank() ? "table" : renderer );
		synchronized ( panels ) {
			panels.put( id, m );
		}
		return this;
	}

	/**
	 * Declare a panel with defaults for icon and order.
	 */
	public LensRegistry panel( String id, String label ) {
		return panel( id, label, "box", 100, "table" );
	}

	/**
	 * Declared panels in declaration order.
	 */
	public List<Map<String, Object>> list() {
		synchronized ( panels ) {
			return new ArrayList<>( panels.values() );
		}
	}

	public void clear() {
		synchronized ( panels ) {
			panels.clear();
		}
	}

}
