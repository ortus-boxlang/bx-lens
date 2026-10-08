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
				l.info( "event={} role={} ip={} {}", event, role == null ? "none" : role, ip, detail == null ? "" : clean( detail ) );
			}
		} catch ( Throwable t ) {
			// Auditing never breaks a request
		}
	}

	private static String clean( String s ) {
		String t = s.replaceAll( "[\\r\\n]+", " " );
		return t.length() > 500 ? t.substring( 0, 500 ) + "..." : t;
	}

}
