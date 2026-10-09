/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LensRequestTest {

	@Test
	@DisplayName( "Spans nest by depth and close in order" )
	public void nesting() {
		LensRequest	req		= new LensRequest();
		Span		outer	= req.begin( Span.TEMPLATE, "outer", 100 );
		Span		inner	= req.begin( Span.QUERY, "select", 100 );
		assertThat( outer.depth ).isEqualTo( 0 );
		assertThat( inner.depth ).isEqualTo( 1 );
		req.end( inner );
		req.end( outer );
		assertThat( inner.isOpen() ).isFalse();
		assertThat( outer.isOpen() ).isFalse();
		assertThat( outer.durationNs() ).isAtLeast( inner.durationNs() );
	}

	@Test
	@DisplayName( "Closing a parent closes children that never ended, such as after an exception" )
	public void unwinds() {
		LensRequest	req		= new LensRequest();
		Span		outer	= req.begin( Span.TEMPLATE, "outer", 100 );
		Span		orphan	= req.begin( Span.FUNCTION, "fn()", 100 );
		req.end( outer );
		assertThat( orphan.isOpen() ).isFalse();
		assertThat( req.open( Span.FUNCTION ) ).isNull();
	}

	@Test
	@DisplayName( "Open spans are found by type and by type and label" )
	public void findOpen() {
		LensRequest	req	= new LensRequest();
		Span		a	= req.begin( Span.TEMPLATE, "a.bxm", 100 );
		Span		b	= req.begin( Span.TEMPLATE, "b.bxm", 100 );
		assertThat( req.open( Span.TEMPLATE ) ).isSameInstanceAs( b );
		assertThat( req.open( Span.TEMPLATE, "a.bxm" ) ).isSameInstanceAs( a );
		assertThat( req.open( Span.QUERY ) ).isNull();
	}

	@Test
	@DisplayName( "Caps drop extra spans instead of growing without limit" )
	public void caps() {
		LensRequest req = new LensRequest();
		assertThat( req.begin( Span.QUERY, "1", 2 ) ).isNotNull();
		assertThat( req.begin( Span.QUERY, "2", 2 ) ).isNotNull();
		assertThat( req.begin( Span.QUERY, "3", 2 ) ).isNull();
		assertThat( req.begin( Span.TEMPLATE, "t", 2 ) ).isNotNull();
	}

	@Test
	@DisplayName( "closeAll finishes anything still open" )
	public void closeAll() {
		LensRequest	req	= new LensRequest();
		Span		s	= req.begin( Span.HTTP, "call", 10 );
		req.closeAll();
		assertThat( s.isOpen() ).isFalse();
	}

	@Test
	@DisplayName( "A crit flag is never downgraded to warn" )
	public void flags() {
		Span s = new Span( 1, Span.QUERY, "q", 0, 0 );
		s.flag( "crit", "Slow" );
		s.flag( "warn", "N+1" );
		assertThat( s.flagSeverity ).isEqualTo( "crit" );
		assertThat( s.flagLabel ).isEqualTo( "Slow" );
	}

	@Test
	@DisplayName( "A child whose end event never came ends when the last thing happened, not when its parent ended" )
	public void skippedPostEvent() throws Exception {
		LensRequest	req		= new LensRequest();
		Span		parent	= req.begin( Span.TEMPLATE, "/outer.bxm", 10 );
		Span		child	= req.begin( Span.TEMPLATE, "/inner.bxm", 10 );
		Thread.sleep( 5 );
		req.noteException();
		long exceptionAt = req.now();
		Thread.sleep( 30 );
		// The parent ends normally, the child's post event was skipped because an exception went through it
		req.end( parent );
		assertThat( child.interrupted ).isTrue();
		assertThat( parent.interrupted ).isFalse();
		assertThat( child.endNs ).isAtMost( exceptionAt );
		assertThat( child.endNs ).isLessThan( parent.endNs );
		assertThat( child.toMap().get( "interrupted" ) ).isEqualTo( true );
		assertThat( parent.toMap().containsKey( "interrupted" ) ).isFalse();
	}

	@Test
	@DisplayName( "Without an exception the child ends at the last activity before its parent closed" )
	public void lastActivity() throws Exception {
		LensRequest	req		= new LensRequest();
		Span		parent	= req.begin( Span.TEMPLATE, "/outer.bxm", 10 );
		Span		child	= req.begin( Span.QUERY, "SELECT 1", 10 );
		Thread.sleep( 25 );
		req.end( parent );
		assertThat( child.interrupted ).isTrue();
		assertThat( child.endNs ).isAtMost( child.startNs + 1_000_000L );
		assertThat( child.endNs ).isAtLeast( child.startNs );
	}

	@Test
	@DisplayName( "closeAll marks spans still open at the end of the request, and leaves closed ones alone" )
	public void closeAllMarks() throws Exception {
		LensRequest	req		= new LensRequest();
		Span		done	= req.begin( Span.TEMPLATE, "/done.bxm", 10 );
		req.end( done );
		Span open = req.begin( Span.HTTP, "GET /slow", 10 );
		Thread.sleep( 20 );
		req.closeAll();
		assertThat( open.isOpen() ).isFalse();
		assertThat( open.interrupted ).isTrue();
		assertThat( done.interrupted ).isFalse();
		assertThat( req.hasInterrupted() ).isTrue();
		assertThat( open.endNs ).isAtLeast( open.startNs );
	}

	@Test
	@DisplayName( "A request whose spans all closed has none interrupted" )
	public void cleanRequest() {
		LensRequest	req	= new LensRequest();
		Span		a	= req.begin( Span.TEMPLATE, "/a.bxm", 10 );
		Span		b	= req.begin( Span.QUERY, "SELECT 1", 10 );
		req.end( b );
		req.end( a );
		req.closeAll();
		assertThat( req.hasInterrupted() ).isFalse();
	}

}
