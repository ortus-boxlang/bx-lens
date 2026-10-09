/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.List;
import java.util.Map;

import ortus.boxlang.modules.bxlens.util.Plain;
import ortus.boxlang.modules.bxlens.util.Secrets;
import ortus.boxlang.modules.bxlens.util.Text;

/**
 * Builds the text sent to a model, or copied by the user to paste into one. Only data Lens already redacted goes in: stack frames, statement
 * text (no parameter values), query strings with secret parameters hidden, and short messages. Capped so a prompt stays a reasonable size.
 */
public final class AiPrompts {

	public static final int		MAX		= 8000;

	private static final String	INTRO	= "You are helping debug a BoxLang application (a dynamic JVM language, CFML compatible). Explain the most likely cause, "
	    + "say how to confirm it, and suggest a fix. Be concise. Lines starting with 'bx' are BoxLang source frames; the others are Java.\n\n";

	private AiPrompts() {
	}

	/**
	 * An error group sample.
	 */
	public static String error( Map<String, Object> group, Map<String, Object> sample ) {
		StringBuilder sb = new StringBuilder( INTRO );
		sb.append( "ERROR: " ).append( Plain.str( group.get( "type" ) ) ).append( ": " ).append( safe( sample.get( "message" ) ) ).append( '\n' );
		if ( !Plain.str( sample.get( "detail" ) ).isEmpty() ) {
			sb.append( "DETAIL: " ).append( safe( sample.get( "detail" ) ) ).append( '\n' );
		}
		if ( !Plain.str( sample.get( "requestId" ) ).isEmpty() ) {
			sb.append( "REQUEST ID: " ).append( Plain.str( sample.get( "requestId" ) ) ).append( '\n' );
		}
		sb.append( "SEEN: " ).append( Plain.str( group.get( "count" ) ) ).append( " times\n" );
		// The path only: numbers and ids in it are replaced, and there is no host and no query string
		sb.append( "REQUEST: " ).append( Plain.str( sample.get( "method" ) ) ).append( ' ' ).append( Text.collapsePath( Plain.str( sample.get( "uri" ) ) ) );
		sb.append( " -> status " ).append( Plain.str( sample.get( "status" ) ) ).append( " after " ).append( Plain.str( sample.get( "ms" ) ) )
		    .append( " ms\n" );
		if ( !Plain.str( sample.get( "sql" ) ).isEmpty() ) {
			sb.append( "STATEMENT: " ).append( safe( sample.get( "sql" ) ) ).append( '\n' );
		}
		sb.append( "\nSTACK:\n" );
		for ( Object f : Plain.list( sample.get( "frames" ) ) ) {
			Map<String, Object> m = Plain.map( f );
			sb.append( "bx  " ).append( Plain.str( m.get( "file" ) ) ).append( ':' ).append( Plain.str( m.get( "line" ) ) ).append( '\n' );
		}
		for ( Object j : Plain.list( sample.get( "java" ) ) ) {
			sb.append( "    " ).append( Plain.str( j ) ).append( '\n' );
		}
		list( sb, "\nLAST QUERIES:\n", masked( Plain.list( sample.get( "lastQueries" ) ) ) );
		list( sb, "\nMESSAGES BEFORE IT FAILED:\n", masked( Plain.list( sample.get( "messages" ) ) ) );
		return cap( sb );
	}

	/**
	 * Threads that deadlocked, each with the lock it waits for and the thread that holds it.
	 */
	public static String deadlock( List<Map<String, Object>> threads ) {
		StringBuilder sb = new StringBuilder( INTRO ).append( "The JVM reports a DEADLOCK between these threads. Explain the cycle and how to break it.\n\n" );
		for ( Map<String, Object> t : threads ) {
			sb.append( "THREAD " ).append( Plain.str( t.get( "name" ) ) ).append( " (" ).append( Plain.str( t.get( "state" ) ) ).append( ")\n  waits for " )
			    .append( Plain.str( t.get( "lock" ) ) ).append( " held by " ).append( Plain.str( t.get( "lockOwner" ) ) ).append( '\n' );
			int n = 0;
			for ( Object f : Plain.list( t.get( "frames" ) ) ) {
				if ( n++ >= 14 ) {
					break;
				}
				Map<String, Object> m = Plain.map( f );
				sb.append( Boolean.TRUE.equals( m.get( "bx" ) ) ? "  bx  " : "      " ).append( Plain.str( m.get( "text" ) ) ).append( '\n' );
			}
			sb.append( '\n' );
		}
		return cap( sb );
	}

	/**
	 * A statement with its numbers.
	 */
	public static String query( Map<String, Object> q ) {
		StringBuilder sb = new StringBuilder( INTRO ).append( "This SQL statement is slow or failing. Say why, and how to make it faster or fix it. " )
		    .append( "Suggest an index if it would help.\n\n" );
		sb.append( "STATEMENT (placeholders, no values):\n" ).append( safe( q.get( "sql" ) ) ).append( "\n\n" );
		sb.append( "DATASOURCE: " ).append( Plain.str( q.get( "datasource" ) ) ).append( '\n' );
		sb.append( "RUNS: " ).append( Plain.str( q.get( "count" ) ) ).append( ", FAILED: " ).append( Plain.str( q.get( "failures" ) ) ).append( ", SLOW: " )
		    .append( Plain.str( q.get( "slow" ) ) ).append( '\n' );
		sb.append( "TIME: min " ).append( Plain.str( q.get( "minMs" ) ) ).append( " ms, avg " ).append( Plain.str( q.get( "avgMs" ) ) ).append( " ms, max " )
		    .append( Plain.str( q.get( "maxMs" ) ) ).append( " ms, rows returned " ).append( Plain.str( q.get( "rows" ) ) ).append( '\n' );
		if ( !Plain.str( q.get( "lastError" ) ).isEmpty() ) {
			sb.append( "LAST ERROR: " ).append( safe( q.get( "lastError" ) ) ).append( '\n' );
		}
		if ( !Plain.str( q.get( "file" ) ).isEmpty() ) {
			sb.append( "CALLED FROM: " ).append( Plain.str( q.get( "file" ) ) ).append( ':' ).append( Plain.str( q.get( "line" ) ) ).append( '\n' );
		}
		return cap( sb );
	}

	/**
	 * A question about the server with a short summary as context.
	 */
	public static String ask( String question, String context ) {
		return cap( new StringBuilder( "You are an assistant for operators of a BoxLang server. Answer from the summary below and say when it does not contain "
		    + "the answer. Be concise.\n\nSERVER SUMMARY (redacted):\n" ).append( context ).append( "\n\nQUESTION: " ).append( question ) );
	}

	/**
	 * Text for a prompt: credentials hidden, and the string and number literals of any SQL in it replaced.
	 */
	public static String safe( Object value ) {
		return Text.maskSql( Secrets.text( Plain.str( value ) ) );
	}

	private static List<Object> masked( List<Object> in ) {
		List<Object> out = new java.util.ArrayList<>();
		for ( Object o : in ) {
			out.add( safe( o ) );
		}
		return out;
	}

	private static void list( StringBuilder sb, String title, List<Object> items ) {
		if ( items.isEmpty() ) {
			return;
		}
		sb.append( title );
		for ( Object o : items ) {
			sb.append( "  " ).append( Plain.str( o ) ).append( '\n' );
		}
	}

	private static String cap( StringBuilder sb ) {
		String s = Secrets.text( sb.toString() );
		return s.length() > MAX ? s.substring( 0, MAX ) + "\n[cut]" : s;
	}

}
