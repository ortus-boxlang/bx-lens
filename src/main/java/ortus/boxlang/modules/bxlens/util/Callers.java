/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.ExceptionUtil;

/**
 * Finds the BoxLang source location (template and line) of the code that is running or of an exception.
 */
public final class Callers {

	private Callers() {
	}

	/**
	 * A source location.
	 */
	public record Location( String file, int line ) {

		public static final Location NONE = new Location( "", 0 );
	}

	/**
	 * The BoxLang template and line that triggered the current call, such as the line that ran a query.
	 *
	 * @return the location or {@link Location#NONE}
	 */
	public static Location current() {
		try {
			Array ctx = ExceptionUtil.getTagContext( 1 );
			if ( ctx != null && !ctx.isEmpty() && ctx.get( 0 ) instanceof IStruct s ) {
				return from( s );
			}
		} catch ( Throwable t ) {
			// Best effort only
		}
		return Location.NONE;
	}

	/**
	 * First locations of an exception's BoxLang tag context.
	 *
	 * @param max the most frames to return
	 */
	public static List<Map<String, Object>> frames( Throwable t, int max ) {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			Array ctx = ExceptionUtil.buildTagContext( t, max );
			for ( Object o : ctx ) {
				if ( o instanceof IStruct s ) {
					Location			l	= from( s );
					Map<String, Object>	m	= new LinkedHashMap<>();
					m.put( "file", l.file() );
					m.put( "line", l.line() );
					out.add( m );
				}
			}
		} catch ( Throwable ignored ) {
			// Best effort only
		}
		return out;
	}

	private static Location from( IStruct s ) {
		Object	template	= s.get( Key.template );
		Object	line		= s.get( Key.line );
		return new Location( template == null ? "" : template.toString(), line instanceof Number n ? n.intValue() : 0 );
	}

}
