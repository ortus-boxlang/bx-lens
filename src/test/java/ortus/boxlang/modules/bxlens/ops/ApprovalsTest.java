/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.ops.Approvals.Decision;
import ortus.boxlang.modules.bxlens.ops.Approvals.State;

public class ApprovalsTest {

	private final AtomicLong	clock		= new AtomicLong( 1_000_000L );
	private final Approvals		approvals	= new Approvals( clock::get );

	private Approvals.Pending open( String session ) {
		return this.approvals.open( session, "runGc", Map.of(), "Run a garbage collection" );
	}

	@Test
	@DisplayName( "a request is held with its tool and arguments and can be approved once" )
	void approveOnce() {
		Approvals.Pending p = this.approvals.open( "s1", "cacheAction", Map.of( "cache", "default", "action", "clear" ), "Clear the cache default" );
		assertThat( p.id ).isNotEmpty();
		assertThat( p.tool ).isEqualTo( "cacheAction" );
		assertThat( p.args ).containsEntry( "cache", "default" );
		assertThat( p.state() ).isEqualTo( State.PENDING );
		assertThat( this.approvals.pendingCount( "s1" ) ).isEqualTo( 1 );
		assertThat( this.approvals.decide( p.id, "s1", true ) ).isEqualTo( Decision.OK );
		assertThat( p.state() ).isEqualTo( State.APPROVED );
		assertThat( this.approvals.decide( p.id, "s1", true ) ).isEqualTo( Decision.ALREADY_DECIDED );
		assertThat( this.approvals.decide( p.id, "s1", false ) ).isEqualTo( Decision.ALREADY_DECIDED );
		assertThat( p.state() ).isEqualTo( State.APPROVED );
	}

	@Test
	@DisplayName( "a denial is final too" )
	void deny() {
		Approvals.Pending p = open( "s1" );
		assertThat( this.approvals.decide( p.id, "s1", false ) ).isEqualTo( Decision.OK );
		assertThat( p.state() ).isEqualTo( State.DENIED );
		assertThat( this.approvals.decide( p.id, "s1", true ) ).isEqualTo( Decision.ALREADY_DECIDED );
	}

	@Test
	@DisplayName( "another session cannot decide it, and the request stays pending" )
	void wrongSession() {
		Approvals.Pending p = open( "s1" );
		assertThat( this.approvals.decide( p.id, "s2", true ) ).isEqualTo( Decision.WRONG_SESSION );
		assertThat( p.state() ).isEqualTo( State.PENDING );
		assertThat( this.approvals.decide( p.id, null, true ) ).isEqualTo( Decision.WRONG_SESSION );
	}

	@Test
	@DisplayName( "an unknown id is unknown" )
	void unknown() {
		assertThat( this.approvals.decide( "nope", "s1", true ) ).isEqualTo( Decision.UNKNOWN );
		assertThat( this.approvals.decide( null, "s1", true ) ).isEqualTo( Decision.UNKNOWN );
	}

	@Test
	@DisplayName( "it expires after five minutes and can no longer be approved" )
	void expires() {
		Approvals.Pending p = open( "s1" );
		this.clock.addAndGet( Approvals.TTL_MS - 1 );
		assertThat( this.approvals.decide( p.id, "s1", true ) ).isEqualTo( Decision.OK );
		Approvals.Pending q = open( "s1" );
		this.clock.addAndGet( Approvals.TTL_MS );
		assertThat( this.approvals.decide( q.id, "s1", true ) ).isEqualTo( Decision.EXPIRED );
		assertThat( q.state() ).isEqualTo( State.EXPIRED );
		assertThat( Approvals.TTL_MS ).isEqualTo( 5 * 60_000L );
	}

	@Test
	@DisplayName( "the tool call that waits learns the outcome" )
	void awaitOutcomes() throws Exception {
		Approvals.Pending	p		= open( "s1" );
		State[]				got		= new State[ 1 ];
		Thread				waiter	= new Thread( () -> got[ 0 ] = this.approvals.await( p, () -> false ) );
		waiter.start();
		Thread.sleep( 150 );
		this.approvals.decide( p.id, "s1", true );
		waiter.join( 3000 );
		assertThat( got[ 0 ] ).isEqualTo( State.APPROVED );

		Approvals.Pending late = open( "s1" );
		this.clock.addAndGet( Approvals.TTL_MS + 1 );
		assertThat( this.approvals.await( late, () -> false ) ).isEqualTo( State.EXPIRED );

		Approvals.Pending gone = open( "s1" );
		assertThat( this.approvals.await( gone, () -> true ) ).isEqualTo( State.CANCELLED );
	}

	@Test
	@DisplayName( "ending a session cancels what it still waits for" )
	void cancelSession() {
		Approvals.Pending a = open( "s1" ), b = open( "s2" );
		this.approvals.cancelSession( "s1" );
		assertThat( a.state() ).isEqualTo( State.CANCELLED );
		assertThat( b.state() ).isEqualTo( State.PENDING );
		assertThat( this.approvals.decide( a.id, "s1", true ) ).isEqualTo( Decision.ALREADY_DECIDED );
	}

	@Test
	@DisplayName( "the number of held requests is capped" )
	void capped() {
		for ( int i = 0; i < Approvals.MAX_HELD; i++ ) {
			open( "s1" );
		}
		try {
			open( "s1" );
			throw new AssertionError( "expected a refusal" );
		} catch ( IllegalStateException e ) {
			assertThat( e.getMessage() ).contains( "Too many" );
		}
	}

	@Test
	@DisplayName( "ids are random and not guessable" )
	void ids() {
		assertThat( open( "s1" ).id ).isNotEqualTo( open( "s1" ).id );
		assertThat( open( "s1" ).id.length() ).isAtLeast( 20 );
	}

}
