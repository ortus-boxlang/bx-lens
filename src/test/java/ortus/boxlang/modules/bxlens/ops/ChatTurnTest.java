/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ChatTurnTest {

	@Test
	@DisplayName( "events come out in the order they were put in, with their data" )
	void order() {
		ChatTurn t = new ChatTurn( "t1", 60_000 );
		t.token( "Hel" );
		t.toolCall( "overview", Map.of(), true );
		t.toolResult( "overview", true, "3 fields" );
		t.token( "lo" );
		t.done();
		List<ChatTurn.Event> e = t.drain();
		assertThat( e.stream().map( ChatTurn.Event::type ).toList() ).containsExactly( "token", "tool_call", "tool_result", "token", "done" ).inOrder();
		assertThat( e.get( 1 ).data() ).containsEntry( "readOnly", true );
		assertThat( t.finished() ).isTrue();
	}

	@Test
	@DisplayName( "the end of a turn is reported once" )
	void once() {
		ChatTurn t = new ChatTurn( "t1", 60_000 );
		t.error( "first" );
		t.error( "second" );
		t.done();
		assertThat( t.drain() ).hasSize( 1 );
	}

	@Test
	@DisplayName( "the time a person takes to approve is added to the deadline" )
	void extend() throws Exception {
		ChatTurn t = new ChatTurn( "t1", 50 );
		t.extend( 10_000 );
		Thread.sleep( 120 );
		assertThat( t.expired() ).isFalse();
		ChatTurn u = new ChatTurn( "t2", 50 );
		Thread.sleep( 120 );
		assertThat( u.expired() ).isTrue();
	}

	@Test
	@DisplayName( "cancel stops the turn, tells the browser why, and interrupts the thread of the agent" )
	void cancel() throws Exception {
		ChatTurn	t		= new ChatTurn( "t1", 60_000 );
		boolean[]	stopped	= new boolean[ 1 ];
		Thread		th		= new Thread( () -> {
								try {
									Thread.sleep( 30_000 );
								} catch ( InterruptedException e ) {
									stopped[ 0 ] = true;
								}
							} );
		th.start();
		java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>( () -> null ) {

			@Override
			public boolean cancel( boolean mayInterrupt ) {
				th.interrupt();
				return super.cancel( mayInterrupt );
			}
		};
		t.future( task );
		t.cancel( "Out of time" );
		th.join( 2000 );
		assertThat( stopped[ 0 ] ).isTrue();
		assertThat( t.cancelled() ).isTrue();
		List<ChatTurn.Event> e = t.drain();
		assertThat( e.get( 0 ).type() ).isEqualTo( "error" );
		assertThat( e.get( 0 ).data() ).containsEntry( "message", "Out of time" );
	}

	@Test
	@DisplayName( "a quiet cancel ends without an error event" )
	void quiet() {
		ChatTurn t = new ChatTurn( "t1", 60_000 );
		t.cancel( "" );
		t.done();
		assertThat( t.drain().stream().map( ChatTurn.Event::type ).toList() ).containsExactly( "done" );
	}

	@Test
	@DisplayName( "tokens are dropped for a reader that never reads, the end never is" )
	void bounded() {
		ChatTurn t = new ChatTurn( "t1", 60_000 );
		for ( int i = 0; i < 20_000; i++ ) {
			t.token( "x" );
		}
		t.done();
		List<ChatTurn.Event> e = t.drain();
		assertThat( e.size() ).isLessThan( 5100 );
		assertThat( e.get( e.size() - 1 ).type() ).isEqualTo( "done" );
	}

}
