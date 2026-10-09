/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AsyncWorkerTest {

	@Test
	@DisplayName( "tasks run on another thread, in the order they were submitted" )
	void ordering() {
		AsyncWorker				w		= new AsyncWorker( true, 1000 );
		List<Integer>			seen	= Collections.synchronizedList( new ArrayList<>() );
		AtomicReference<String>	thread	= new AtomicReference<>();
		for ( int i = 0; i < 500; i++ ) {
			final int n = i;
			w.submit( () -> {
				seen.add( n );
				thread.set( Thread.currentThread().getName() );
			} );
		}
		assertThat( w.barrier( 5000 ) ).isTrue();
		assertThat( seen ).hasSize( 500 );
		for ( int i = 0; i < 500; i++ ) {
			assertThat( seen.get( i ) ).isEqualTo( i );
		}
		assertThat( thread.get() ).isEqualTo( "bxlens-worker" );
		assertThat( w.processed() ).isAtLeast( 500L );
		assertThat( w.dropped() ).isEqualTo( 0L );
		w.shutdown( 1000 );
	}

	@Test
	@DisplayName( "a full queue drops new work and counts it, and submit never blocks" )
	void drops() throws Exception {
		AsyncWorker		w		= new AsyncWorker( true, 5 );
		CountDownLatch	hold	= new CountDownLatch( 1 );
		CountDownLatch	started	= new CountDownLatch( 1 );
		w.submit( () -> {
			started.countDown();
			try {
				hold.await();
			} catch ( InterruptedException e ) {
				// stop
			}
		} );
		assertThat( started.await( 2, TimeUnit.SECONDS ) ).isTrue();
		long	t0			= System.nanoTime();
		int		accepted	= 0;
		for ( int i = 0; i < 200; i++ ) {
			if ( w.submit( () -> {
			} ) ) {
				accepted++;
			}
		}
		long ms = ( System.nanoTime() - t0 ) / 1_000_000L;
		assertThat( accepted ).isEqualTo( 5 );
		assertThat( w.dropped() ).isEqualTo( 195L );
		assertThat( w.depth() ).isEqualTo( 5 );
		assertThat( ms ).isLessThan( 500L );
		hold.countDown();
		assertThat( w.barrier( 5000 ) ).isTrue();
		assertThat( w.depth() ).isEqualTo( 0 );
		w.shutdown( 1000 );
	}

	@Test
	@DisplayName( "a task that throws is counted, never reaches the caller and does not stop the worker" )
	void failures() {
		AsyncWorker	w	= new AsyncWorker( true, 100 );
		int[]		ran	= { 0 };
		w.submit( () -> {
			throw new IllegalStateException( "boom" );
		} );
		w.submit( () -> ran[ 0 ]++ );
		assertThat( w.barrier( 5000 ) ).isTrue();
		assertThat( ran[ 0 ] ).isEqualTo( 1 );
		assertThat( w.failed() ).isEqualTo( 1L );
		w.shutdown( 1000 );
	}

	@Test
	@DisplayName( "shutdown lets the queue drain before the thread stops, and later work is refused" )
	void drainsAtShutdown() throws Exception {
		AsyncWorker		w		= new AsyncWorker( true, 1000 );
		List<Integer>	seen	= Collections.synchronizedList( new ArrayList<>() );
		for ( int i = 0; i < 300; i++ ) {
			final int n = i;
			w.submit( () -> {
				try {
					Thread.sleep( 1 );
				} catch ( InterruptedException e ) {
					// stop
				}
				seen.add( n );
			} );
		}
		assertThat( w.shutdown( 10_000 ) ).isTrue();
		assertThat( seen ).hasSize( 300 );
		assertThat( w.submit( () -> {
		} ) ).isFalse();
		assertThat( w.dropped() ).isEqualTo( 1L );
	}

	@Test
	@DisplayName( "when it is off the work runs in the caller, and a failure still does not reach it" )
	void inline() {
		AsyncWorker	w		= new AsyncWorker( false, 10 );
		String[]	where	= { "" };
		w.submit( () -> where[ 0 ] = Thread.currentThread().getName() );
		assertThat( where[ 0 ] ).isEqualTo( Thread.currentThread().getName() );
		assertThat( w.submit( () -> {
			throw new RuntimeException( "x" );
		} ) ).isTrue();
		assertThat( w.failed() ).isEqualTo( 1L );
		assertThat( w.enabled() ).isFalse();
		assertThat( w.barrier( 10 ) ).isTrue();
	}

}
