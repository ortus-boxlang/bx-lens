/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import ortus.boxlang.runtime.logging.BoxLangLogger;

/**
 * The audit trail of the console: who did what, from where, and when. Written to its own log file (<code>bxlens-audit.log</code>) through
 * the BoxLang logging service. Lens keeps nothing else: the log is the record. Secrets are never written.
 */
public final class Audit {

	private final LensService service;

	public Audit( LensService service ) {
		this.service = service;
	}

	/**
	 * @param event  what happened, for example <code>login.ok</code> or <code>settings.change</code>
	 * @param role   admin, viewer or none
	 * @param ip     the client address
	 * @param detail extra context, never a secret
	 */
	public void log( String event, String role, String ip, String detail ) {
		try {
			BoxLangLogger l = service.auditLogger();
			if ( l != null ) {
				l.info( "event={} role={} ip={} {}", clean( event, 64 ), clean( role == null ? "none" : role, 16 ), clean( ip, 64 ),
				    clean( detail, 500 ) );
			}
		} catch ( Throwable t ) {
			// Auditing never breaks a request
		}
	}

	/**
	 * Make a value safe for one log line: control characters (a line break most of all) become spaces, runs of them collapse, and the
	 * value is cut to a length. A client controls several of these fields, so none is written as it came.
	 */
	public static String clean( String s, int max ) {
		if ( s == null ) {
			return "";
		}
		int				n		= Math.min( s.length(), max + 64 );
		StringBuilder	sb		= new StringBuilder( Math.min( n, max ) + 3 );
		boolean			space	= false;
		for ( int i = 0; i < n && sb.length() < max; i++ ) {
			char c = s.charAt( i );
			if ( c < 0x20 || c == 0x7f || c == 0x85 || c == '\u2028' || c == '\u2029' ) {
				space = sb.length() > 0;
				continue;
			}
			if ( space ) {
				sb.append( ' ' );
				space = false;
			}
			sb.append( c );
		}
		if ( s.length() > n || sb.length() >= max && n < s.length() ) {
			sb.append( "..." );
		}
		return sb.toString();
	}

}
