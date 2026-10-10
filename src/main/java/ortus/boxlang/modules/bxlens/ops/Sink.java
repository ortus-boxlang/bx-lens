/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.Map;

/**
 * Where the things that happen during one chat turn are reported: the tool calls, the questions for approval and the results. The chat
 * writes them to the browser as events. A test can hold them in a list.
 */
public interface Sink {

	/**
	 * A tool is about to run.
	 *
	 * @param args     the arguments, redacted and shortened for display
	 * @param readOnly true for a LOOK tool, false for an ACT tool
	 */
	void toolCall( String name, Map<String, Object> args, boolean readOnly );

	/**
	 * An ACT tool waits for a person.
	 */
	void approvalRequest( Approvals.Pending pending );

	/**
	 * A tool finished, was refused or failed.
	 *
	 * @param summary one line for the display, never the whole result
	 */
	void toolResult( String name, boolean ok, String summary );

	/**
	 * Has the person gone away or the turn run out of time? Work stops when this is true.
	 */
	boolean cancelled();

	/**
	 * More time for the turn: the seconds a person took to decide do not count against the timeout.
	 */
	default void extend( long millis ) {
	}

	/** A sink that only counts, for tests and for calls outside a chat. */
	Sink NONE = new Sink() {

		@Override
		public void toolCall( String name, Map<String, Object> args, boolean readOnly ) {
		}

		@Override
		public void approvalRequest( Approvals.Pending pending ) {
		}

		@Override
		public void toolResult( String name, boolean ok, String summary ) {
		}

		@Override
		public boolean cancelled() {
			return false;
		}
	};
}
