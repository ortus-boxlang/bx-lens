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
package ortus.boxlang.modules.bxlens.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One timed unit of work on the request waterfall: a template, function, query, HTTP call, transaction, timer or custom span.
 * Times are nanoseconds relative to the request start.
 */
public final class Span {

	public static final String			TEMPLATE	= "template";
	public static final String			FUNCTION	= "func";
	public static final String			QUERY		= "query";
	public static final String			HTTP		= "http";
	public static final String			TX			= "tx";
	public static final String			TIMER		= "timer";
	public static final String			CUSTOM		= "custom";

	public final int					id;
	public final String					type;
	public final String					label;
	public final long					startNs;
	public final int					depth;
	public volatile long				endNs		= -1;
	public volatile String				file		= "";
	public volatile int					line		= 0;
	public volatile String				flagSeverity;
	public volatile String				flagLabel;
	public final Map<String, Object>	detail		= new LinkedHashMap<>();

	public Span( int id, String type, String label, long startNs, int depth ) {
		this.id			= id;
		this.type		= type;
		this.label		= label;
		this.startNs	= startNs;
		this.depth		= depth;
	}

	public boolean isOpen() {
		return endNs < 0;
	}

	/**
	 * Duration in nanoseconds. Open spans report zero.
	 */
	public long durationNs() {
		return endNs < 0 ? 0 : endNs - startNs;
	}

	/**
	 * Flag the span with a severity (warn, crit) and a short label such as "Slow" or "N+1".
	 */
	public void flag( String severity, String text ) {
		// A crit flag is never downgraded by a later warn
		if ( this.flagSeverity == null || "warn".equals( this.flagSeverity ) && "crit".equals( severity ) ) {
			this.flagSeverity	= severity;
			this.flagLabel		= text;
		}
	}

	/**
	 * Convert to the JSON-ready shape the UI reads. Milliseconds with microsecond precision.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "id", id );
		m.put( "type", type );
		m.put( "label", label );
		m.put( "start", ms( startNs ) );
		m.put( "dur", ms( durationNs() ) );
		m.put( "depth", depth );
		m.put( "file", file );
		m.put( "line", line );
		if ( flagSeverity != null ) {
			m.put( "flag", java.util.List.of( flagSeverity, flagLabel ) );
		}
		if ( !detail.isEmpty() ) {
			m.put( "detail", detail );
		}
		return m;
	}

	/**
	 * Nanoseconds to milliseconds rounded to 3 decimals.
	 */
	public static double ms( long ns ) {
		return Math.round( ns / 1000.0 ) / 1000.0;
	}

}
