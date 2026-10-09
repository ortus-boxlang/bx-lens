/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One question and its answer. The agent thread puts events in, the request that streams the answer takes them out and writes them to the
 * browser. It is also the {@link Sink} the {@link Toolbox} reports to, so a tool call, a question for approval and a result appear in the
 * chat in the order they happened.
 * <p>
 * The turn has a deadline (<code>ai.timeoutSeconds</code>). The time a person takes to decide an approval is added to it, so waiting for a
 * click never makes a turn time out. The turn is cancelled when the browser goes away, when the deadline passes or when the session ends.
 */
public final class ChatTurn implements Sink {

	/** One event for the browser: token, tool_call, approval_request, tool_result, done or error. */
	public record Event( String type, Map<String, Object> data ) {
	}

	/** The most events held for a slow reader. Tokens past it are dropped, the end of the turn never is. */
	private static final int			MAX_EVENTS		= 5000;

	private final BlockingQueue<Event>	events			= new LinkedBlockingQueue<>();
	private final AtomicBoolean			cancelled		= new AtomicBoolean();
	private final AtomicBoolean			finished		= new AtomicBoolean();
	private volatile long				deadline;
	private volatile Future<?>			future;
	private volatile String				cancelReason	= "";
	public final String					id;

	ChatTurn( String id, long timeoutMillis ) {
		this.id			= id;
		this.deadline	= System.currentTimeMillis() + timeoutMillis;
	}

	void future( Future<?> f ) {
		this.future = f;
	}

	// ---- what the agent reports ----

	public void token( String text ) {
		if ( text != null && !text.isEmpty() && this.events.size() < MAX_EVENTS ) {
			this.events.add( new Event( "token", Map.of( "text", text ) ) );
		}
	}

	@Override
	public void toolCall( String name, Map<String, Object> args, boolean readOnly ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "name", name );
		m.put( "args", args );
		m.put( "readOnly", readOnly );
		this.events.add( new Event( "tool_call", m ) );
	}

	@Override
	public void approvalRequest( Approvals.Pending p ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "id", p.id );
		m.put( "tool", p.tool );
		m.put( "args", p.args );
		m.put( "summary", p.summary );
		m.put( "expiresAt", p.expiresAt );
		this.events.add( new Event( "approval_request", m ) );
	}

	@Override
	public void toolResult( String name, boolean ok, String summary ) {
		this.events.add( new Event( "tool_result", Map.of( "name", name, "ok", ok, "summary", summary == null ? "" : summary ) ) );
	}

	@Override
	public void extend( long millis ) {
		this.deadline += Math.max( 0, millis );
	}

	/** The answer is complete. */
	void done() {
		if ( this.finished.compareAndSet( false, true ) ) {
			this.events.add( new Event( "done", Map.of( "cancelled", this.cancelled.get() ) ) );
		}
	}

	/** The turn failed. The message is safe to show. */
	void error( String message ) {
		if ( this.finished.compareAndSet( false, true ) ) {
			this.events.add( new Event( "error", Map.of( "message", message ) ) );
		}
	}

	// ---- control ----

	@Override
	public boolean cancelled() {
		return this.cancelled.get();
	}

	/**
	 * Stop the turn: the tool calls refuse from now on and the thread of the agent is interrupted.
	 *
	 * @param reason what to tell the browser, or an empty string to end quietly
	 */
	public void cancel( String reason ) {
		if ( this.cancelled.compareAndSet( false, true ) ) {
			this.cancelReason = reason == null ? "" : reason;
			Future<?> f = this.future;
			if ( f != null ) {
				f.cancel( true );
			}
			if ( !this.cancelReason.isEmpty() ) {
				error( this.cancelReason );
			}
		}
	}

	boolean expired() {
		return System.currentTimeMillis() > this.deadline;
	}

	public boolean finished() {
		return this.finished.get();
	}

	/**
	 * The next event, waiting up to the given time.
	 */
	public Event poll( long millis ) throws InterruptedException {
		return this.events.poll( millis, TimeUnit.MILLISECONDS );
	}

	/** Everything still queued (tests). */
	public java.util.List<Event> drain() {
		java.util.List<Event> out = new java.util.ArrayList<>();
		this.events.drainTo( out );
		return out;
	}

}
