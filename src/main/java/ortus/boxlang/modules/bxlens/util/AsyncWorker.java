/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One daemon thread with a bounded queue, for the work that does not have to happen on the request thread: statistics, the history, the audit
 * log. The rule: a collector never blocks and never throws into a request, and heavy aggregation is async.
 * <ul>
 * <li>{@link #submit(Runnable)} never blocks. When the queue is full the new task is dropped and counted, because an overloaded server must
 * not slow its requests down to keep statistics.</li>
 * <li>A task that throws is counted and forgotten. The worker never stops.</li>
 * <li>Tasks run in the order they were submitted.</li>
 * <li>{@link #shutdown(long)} lets the queue drain before the thread stops.</li>
 * <li>With {@code enabled} false, tasks run at once on the calling thread (the old behavior).</li>
 * </ul>
 */
public final class AsyncWorker {

	private final boolean					enabled;
	private final BlockingQueue<Runnable>	queue;
	private final Thread					thread;
	private final AtomicLong				dropped		= new AtomicLong();
	private final AtomicLong				processed	= new AtomicLong();
	private final AtomicLong				failed		= new AtomicLong();
	private volatile boolean				running		= true;

	/**
	 * @param enabled   run tasks on the worker thread; false runs them inline
	 * @param queueSize most tasks waiting at once
	 */
	public AsyncWorker( boolean enabled, int queueSize ) {
		this.enabled	= enabled;
		this.queue		= new ArrayBlockingQueue<>( Math.max( 1, queueSize ) );
		if ( enabled ) {
			this.thread = new Thread( this::loop, "bxlens-worker" );
			this.thread.setDaemon( true );
			this.thread.start();
		} else {
			this.thread = null;
		}
	}

	private void loop() {
		while ( running || !queue.isEmpty() ) {
			Runnable task;
			try {
				task = queue.poll( 200, TimeUnit.MILLISECONDS );
			} catch ( InterruptedException e ) {
				if ( !running && queue.isEmpty() ) {
					return;
				}
				continue;
			}
			if ( task != null ) {
				run( task );
			}
		}
	}

	private void run( Runnable task ) {
		try {
			task.run();
		} catch ( Throwable t ) {
			failed.incrementAndGet();
		} finally {
			processed.incrementAndGet();
		}
	}

	/**
	 * Queue a task. Never blocks and never throws.
	 *
	 * @return false when the task was dropped because the queue is full or the worker is stopped
	 */
	public boolean submit( Runnable task ) {
		try {
			if ( !enabled ) {
				run( task );
				return true;
			}
			if ( !running || !queue.offer( task ) ) {
				dropped.incrementAndGet();
				return false;
			}
			return true;
		} catch ( Throwable t ) {
			dropped.incrementAndGet();
			return false;
		}
	}

	/**
	 * Wait until every task queued so far has run, but at most <code>maxMillis</code>. Readers call it before they look at the statistics, so a
	 * console page never misses the request that just ended.
	 *
	 * @return true when the queue was caught up
	 */
	public boolean barrier( long maxMillis ) {
		if ( !enabled || !running ) {
			return true;
		}
		CountDownLatch latch = new CountDownLatch( 1 );
		if ( !submit( latch::countDown ) ) {
			return false;
		}
		try {
			return latch.await( maxMillis, TimeUnit.MILLISECONDS );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Stop accepting tasks, run what is queued and stop the thread.
	 *
	 * @param maxMillis the longest to wait for the queue to drain
	 *
	 * @return true when everything queued has run
	 */
	public boolean shutdown( long maxMillis ) {
		running = false;
		if ( thread == null ) {
			return true;
		}
		try {
			thread.join( maxMillis );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
		boolean drained = queue.isEmpty() && !thread.isAlive();
		if ( thread.isAlive() ) {
			thread.interrupt();
		}
		return drained;
	}

	public boolean enabled() {
		return enabled;
	}

	public int depth() {
		return queue.size();
	}

	public int capacity() {
		return queue.size() + queue.remainingCapacity();
	}

	public long dropped() {
		return dropped.get();
	}

	public long processed() {
		return processed.get();
	}

	public long failed() {
		return failed.get();
	}

}
