/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.ConsoleData;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.ops.Diagnose.Finding;

public class DiagnoseTest {

	private static Map<String, Object> executor( String name, double pool, double threads, double queue, long max, long qsize, long qcap, boolean full,
	    String health ) {
		Map<String, Object> e = new LinkedHashMap<>();
		e.put( "name", name );
		e.put( "poolUtilization", pool );
		e.put( "threadsUtilization", threads );
		e.put( "queueUtilization", queue );
		e.put( "maximumPoolSize", max );
		e.put( "corePoolSize", max );
		e.put( "queueSize", qsize );
		e.put( "queueCapacity", qcap );
		e.put( "queueIsFull", full );
		e.put( "healthStatus", health );
		e.put( "healthReport", Map.of( "summary", "ok", "recommendations", List.of() ) );
		return e;
	}

	@Test
	@DisplayName( "a real deadlock between two threads is found with the lock names and owners" )
	void deadlock() throws Exception {
		ReentrantLock	a		= new ReentrantLock(), b = new ReentrantLock();
		CountDownLatch	bothIn	= new CountDownLatch( 2 );
		Thread			t1		= new Thread( () -> {
									a.lock();
									bothIn.countDown();
									try {
										bothIn.await();
										b.lockInterruptibly();
									} catch ( InterruptedException e ) {
										// Released by the test
									} finally {
										if ( a.isHeldByCurrentThread() ) {
											a.unlock();
										}
									}
								}, "lens-test-dead-1" );
		Thread			t2		= new Thread( () -> {
									b.lock();
									bothIn.countDown();
									try {
										bothIn.await();
										a.lockInterruptibly();
									} catch ( InterruptedException e ) {
										// Released by the test
									} finally {
										if ( b.isHeldByCurrentThread() ) {
											b.unlock();
										}
									}
								}, "lens-test-dead-2" );
		t1.setDaemon( true );
		t2.setDaemon( true );
		t1.start();
		t2.start();
		try {
			Map<String, Object>	data	= null;
			long				until	= System.currentTimeMillis() + 5000;
			while ( System.currentTimeMillis() < until ) {
				data = new ConsoleData( LensService.getInstance() ).threads();
				if ( ! ( ( List<?> ) data.get( "deadlocked" ) ).isEmpty() ) {
					break;
				}
				Thread.sleep( 100 );
			}
			assertThat( ( List<?> ) data.get( "deadlocked" ) ).isNotEmpty();
			List<Finding> findings = Diagnose.threads( data );
			assertThat( findings ).isNotEmpty();
			Finding f = findings.get( 0 );
			assertThat( f.severity() ).isEqualTo( Diagnose.CRITICAL );
			assertThat( f.title() ).contains( "deadlocked" );
			assertThat( f.evidence() ).contains( "lens-test-dead-" );
			assertThat( f.evidence() ).contains( "ReentrantLock" );
			assertThat( f.next() ).contains( "same order" );
		} finally {
			t1.interrupt();
			t2.interrupt();
			t1.join( 2000 );
			t2.join( 2000 );
		}
	}

	@Test
	@DisplayName( "many threads blocked on a monitor are contention, a few are a note" )
	void blocked() {
		List<Map<String, Object>> threads = new ArrayList<>();
		for ( int i = 0; i < 10; i++ ) {
			threads.add( Map.of( "id", ( long ) i, "name", "worker-" + i, "state", i < 5 ? "BLOCKED" : "RUNNABLE", "lock", "java.lang.Object@1", "lockOwner",
			    "holder" ) );
		}
		Map<String, Object>	data	= Map.of( "threads", threads, "states", Map.of( "BLOCKED", 5L ), "deadlocked", List.of() );
		List<Finding>		f		= Diagnose.threads( data );
		assertThat( f ).hasSize( 1 );
		assertThat( f.get( 0 ).severity() ).isEqualTo( Diagnose.CRITICAL );
		assertThat( f.get( 0 ).title() ).contains( "5 of 10" );
		assertThat( Diagnose.threads( Map.of( "threads", List.of(), "states", Map.of(), "deadlocked", List.of() ) ) ).isEmpty();
	}

	@Test
	@DisplayName( "a saturated pool gets a concrete new size, a full queue is critical" )
	void saturatedExecutor() {
		List<Finding> f = Diagnose.executors( List.of( executor( "jobs", 100, 100, 10, 8, 3, 100, false, "critical" ) ) );
		assertThat( f ).hasSize( 1 );
		assertThat( f.get( 0 ).severity() ).isEqualTo( Diagnose.CRITICAL );
		assertThat( f.get( 0 ).title() ).contains( "every thread is busy" );
		assertThat( f.get( 0 ).next() ).contains( "from 8 to about 12" );

		List<Finding> full = Diagnose.executors( List.of( executor( "mail", 50, 50, 100, 4, 100, 100, true, "critical" ) ) );
		assertThat( full.get( 0 ).title() ).contains( "queue is full" );
		assertThat( full.get( 0 ).next() ).contains( "100 to about 200" );

		List<Finding> filling = Diagnose.executors( List.of( executor( "io", 40, 40, 80, 4, 80, 100, false, "degraded" ) ) );
		assertThat( filling.get( 0 ).severity() ).isEqualTo( Diagnose.WARNING );
		assertThat( filling.get( 0 ).title() ).contains( "filling" );

		assertThat( Diagnose.executors( List.of( executor( "ok", 10, 10, 0, 4, 0, 100, false, "healthy" ) ) ) ).isEmpty();
	}

	@Test
	@DisplayName( "a garbage collector that never rests, and a heap that stays full, are critical with a number to try" )
	void memory() {
		List<Finding> gc = Diagnose.memory( 40, 18.5, 60, 2048 );
		assertThat( gc ).hasSize( 1 );
		assertThat( gc.get( 0 ).severity() ).isEqualTo( Diagnose.CRITICAL );
		assertThat( gc.get( 0 ).title() ).contains( "18.5%" );

		List<Finding> heap = Diagnose.memory( 91, 1, 95, 2048 );
		assertThat( heap.get( 0 ).severity() ).isEqualTo( Diagnose.CRITICAL );
		assertThat( heap.get( 0 ).next() ).contains( "3072 MB" );

		assertThat( Diagnose.memory( 72, 1, 80, 2048 ).get( 0 ).severity() ).isEqualTo( Diagnose.WARNING );
		assertThat( Diagnose.memory( 30, 0.5, 40, 2048 ) ).isEmpty();
		assertThat( Diagnose.memory( -1, 0.5, 40, 2048 ) ).isEmpty();
	}

	@Test
	@DisplayName( "a pool with timeouts or no free connection is reported" )
	void datasources() {
		Map<String, Object>	saturated	= Map.of( "name", "main", "state", "saturated", "pool", Map.of( "max", 10L, "active", 10L, "pending", 4L ) );
		List<Finding>		f			= Diagnose.datasources( List.of( saturated ) );
		assertThat( f.get( 0 ).severity() ).isEqualTo( Diagnose.CRITICAL );
		assertThat( f.get( 0 ).evidence() ).contains( "10 of 10" );
		Map<String, Object> timeouts = Map.of( "name", "main", "state", "ok", "pool", Map.of( "max", 10L, "active", 3L, "pending", 0L ), "metrics",
		    Map.of( "timeouts", 7L ) );
		assertThat( Diagnose.datasources( List.of( timeouts ) ).get( 0 ).title() ).contains( "7 times" );
		assertThat( Diagnose.datasources( List.of( Map.of( "name", "idle", "state", "idle" ) ) ) ).isEmpty();
	}

	@Test
	@DisplayName( "error rate, slow requests and requests that run too long" )
	void requests() {
		List<Finding> f = Diagnose.requests( 100, 12, 3000, 500, List.of( Map.of( "uri", "/report", "elapsedMs", 90_000L, "thread", "http-1" ) ) );
		assertThat( f.stream().map( Finding::severity ).toList() ).containsAtLeast( Diagnose.CRITICAL, Diagnose.CRITICAL, Diagnose.WARNING );
		assertThat( f.stream().anyMatch( x -> x.title().contains( "more than 30 seconds" ) ) ).isTrue();
		assertThat( Diagnose.requests( 3, 50, 9000, 500, List.of() ) ).isEmpty();
		assertThat( Diagnose.requests( 100, 0, 20, 500, List.of() ) ).isEmpty();
	}

	@Test
	@DisplayName( "the report puts the worst first and says what was left out" )
	void report() {
		List<Finding>		fs	= List.of( new Finding( Diagnose.INFO, "x", "note", "e", "n" ), new Finding( Diagnose.CRITICAL, "y", "bad", "e", "n" ),
		    new Finding( Diagnose.WARNING, "z", "hmm", "e", "n" ) );
		Map<String, Object>	r	= Diagnose.report( fs, List.of( "executors" ), List.of( "threads (admin role)" ) );
		assertThat( r.get( "verdict" ) ).isEqualTo( "critical" );
		assertThat( ( ( List<?> ) r.get( "findings" ) ).get( 0 ) ).isEqualTo( fs.get( 1 ).toMap() );
		assertThat( r.get( "notChecked" ) ).isEqualTo( List.of( "threads (admin role)" ) );
		Map<String, Object> clean = Diagnose.report( List.of(), List.of( "executors" ), List.of() );
		assertThat( clean.get( "verdict" ) ).isEqualTo( "healthy" );
		assertThat( ( String ) clean.get( "summary" ) ).contains( "No problem" );
	}

	@Test
	@DisplayName( "the real thread data of this JVM can be examined" )
	void realThreads() {
		Map<String, Object> data = new ConsoleData( LensService.getInstance() ).threads();
		assertThat( ( ( List<?> ) data.get( "threads" ) ) ).isNotEmpty();
		TimeUnit.MILLISECONDS.toString();
		Diagnose.threads( data );
	}

}
