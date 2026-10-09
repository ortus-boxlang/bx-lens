/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.util.Collection;
import java.util.Map;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Turns the plain maps and lists Lens works with into BoxLang structs (keeping their order) and arrays, for the BIFs that return data.
 */
final class BoxData {

	private BoxData() {
	}

	static Object of( Object v ) {
		if ( v instanceof Map<?, ?> m ) {
			IStruct s = new Struct( IStruct.TYPES.LINKED );
			for ( Map.Entry<?, ?> e : m.entrySet() ) {
				s.put( Key.of( String.valueOf( e.getKey() ) ), of( e.getValue() ) );
			}
			return s;
		}
		if ( v instanceof Collection<?> c ) {
			Array a = new Array();
			for ( Object o : c ) {
				a.add( of( o ) );
			}
			return a;
		}
		return v;
	}

}
