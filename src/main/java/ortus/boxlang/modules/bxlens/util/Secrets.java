/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.regex.Pattern;

/**
 * Hides secrets in text shown for diagnostics: values whose name looks secret, credentials inside URLs, and encrypted config values.
 * It errs on the side of hiding.
 */
public final class Secrets {

	public static final String		HIDDEN		= "[hidden]";

	private static final Pattern	NAME		= Pattern.compile(
	    "(pass(word|wd)?|pwd|secret|token|api[-_.]?key|access[-_.]?key|private|credential|auth|cookie|session|salt|seed|signature|dsn|connection[-_.]?string|bearer|jwt)",
	    Pattern.CASE_INSENSITIVE );
	private static final Pattern	USERINFO	= Pattern.compile( "(?i)([a-z][a-z0-9+.-]*://)[^/\\s@]+@" );
	private static final Pattern	PARAM		= Pattern.compile( "(?i)([?;&](?:password|pwd|pass|secret|token|apikey|api_key|key)=)[^&;\\s]*" );

	private Secrets() {
	}

	/**
	 * Does a name look like it holds a secret?
	 */
	public static boolean isSecretName( String name ) {
		return name != null && NAME.matcher( name ).find();
	}

	/**
	 * The value to show for a name and value.
	 */
	public static String show( String name, String value ) {
		if ( value == null ) {
			return "";
		}
		if ( isSecretName( name ) ) {
			return value.isEmpty() ? "" : HIDDEN;
		}
		return text( value );
	}

	/**
	 * Hide credentials that appear inside a text value.
	 */
	public static String text( String value ) {
		if ( value == null ) {
			return "";
		}
		if ( value.regionMatches( true, 0, "bxsecret:", 0, 9 ) ) {
			return "[encrypted]";
		}
		String v = USERINFO.matcher( value ).replaceAll( "$1" + HIDDEN + "@" );
		return PARAM.matcher( v ).replaceAll( "$1" + HIDDEN );
	}

}
