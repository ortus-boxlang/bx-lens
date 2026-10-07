/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

}
