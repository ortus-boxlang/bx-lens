/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OrmTotalsTest {

	/** Messages are stored without digits, so tell them apart by a letter. */
	private static String letter( int i ) {
		return String.valueOf( ( char ) ( 'a' + i ) );
	}

	@SuppressWarnings( "unchecked" )
	private static List<Map<String, Object>> rows( OrmTotals t ) {
		return ( List<Map<String, Object>> ) t.snapshot().get( "datasources" );
	}

	@Test
	@DisplayName( "statements are counted per application and datasource by kind, with rows, failures and the slowest one" )
	void counts() {
		OrmTotals t = new OrmTotals();
		t.query( "app", "demo", "select", 2_000_000, 3, false, true, "select * from books where id = 12345" );
		t.query( "app", "demo", "select", 9_000_000, 0, false, false, "select * from authors" );
		t.query( "app", "demo", "insert", 1_000_000, 1, false, true, "insert into books values (?)" );
		t.query( "app", "demo", "ddl", 1_000_000, -1, false, false, "create table t (id int)" );
		t.query( "app", "demo", "update", 1_000_000, 0, true, true, "update books set x = ?" );
		Map<String, Object> m = rows( t ).get( 0 );
		assertThat( m.get( "app" ) ).isEqualTo( "app" );
		assertThat( m.get( "datasource" ) ).isEqualTo( "demo" );
		assertThat( m.get( "queries" ) ).isEqualTo( 5L );
		assertThat( m.get( "selects" ) ).isEqualTo( 2L );
		assertThat( m.get( "inserts" ) ).isEqualTo( 1L );
		assertThat( m.get( "ddl" ) ).isEqualTo( 1L );
		assertThat( m.get( "updates" ) ).isEqualTo( 1L );
		assertThat( m.get( "errors" ) ).isEqualTo( 1L );
		assertThat( m.get( "rows" ) ).isEqualTo( 4L );
		assertThat( m.get( "unattributed" ) ).isEqualTo( 2L );
		assertThat( m.get( "maxMs" ) ).isEqualTo( 9.0 );
		assertThat( m.get( "totalMs" ) ).isEqualTo( 14.0 );
		assertThat( m.get( "slowest" ).toString() ).contains( "authors" );
		assertThat( t.snapshot().get( "queries" ) ).isEqualTo( 5L );
		assertThat( t.snapshot().get( "unattributed" ) ).isEqualTo( 2L );
	}

	@Test
	@DisplayName( "the slowest statement is stored without its literals" )
	void masksSlowest() {
		OrmTotals t = new OrmTotals();
		t.query( "a", "d", "select", 1, 1, false, true, "select * from users where email = 'jo@example.com' and id = 77" );
		String slowest = rows( t ).get( 0 ).get( "slowest" ).toString();
		assertThat( slowest ).doesNotContain( "jo@example.com" );
		assertThat( slowest ).doesNotContain( "77" );
	}

	@Test
	@DisplayName( "flushes add up" )
	void flushes() {
		OrmTotals t = new OrmTotals();
		t.flush( "app", "demo", 2, 1, 0, 3_000_000 );
		t.flush( "app", "demo", 1, 0, 4, 1_000_000 );
		Map<String, Object> m = rows( t ).get( 0 );
		assertThat( m.get( "flushes" ) ).isEqualTo( 2L );
		assertThat( m.get( "flushInserts" ) ).isEqualTo( 3L );
		assertThat( m.get( "flushUpdates" ) ).isEqualTo( 1L );
		assertThat( m.get( "flushDeletes" ) ).isEqualTo( 4L );
		assertThat( m.get( "flushMs" ) ).isEqualTo( 4.0 );
		assertThat( m.get( "maxFlushMs" ) ).isEqualTo( 3.0 );
		assertThat( t.snapshot().get( "flushes" ) ).isEqualTo( 2L );
	}

	@Test
	@DisplayName( "failures keep the newest 20 and hide secrets in the message" )
	void failures() {
		OrmTotals t = new OrmTotals();
		for ( int i = 0; i < 30; i++ ) {
			t.exception( "app", "demo", "select " + i, "boom " + letter( i ) );
		}
		@SuppressWarnings( "unchecked" )
		List<Map<String, Object>> f = ( List<Map<String, Object>> ) t.snapshot().get( "failures" );
		assertThat( f ).hasSize( OrmTotals.MAX_FAILURES );
		assertThat( f.get( 0 ).get( "error" ) ).isEqualTo( "boom " + letter( 29 ) );
		assertThat( rows( t ).get( 0 ).get( "exceptions" ) ).isEqualTo( 30L );
		t.exception( "app", "demo", null, "login failed for jdbc:mysql://root:hunter2@db/x" );
		@SuppressWarnings( "unchecked" )
		List<Map<String, Object>> g = ( List<Map<String, Object>> ) t.snapshot().get( "failures" );
		assertThat( g.get( 0 ).get( "error" ).toString() ).doesNotContain( "hunter2" );
	}

	@Test
	@DisplayName( "the store is bounded: more than 100 datasources drop the least recently seen" )
	void bounded() {
		OrmTotals t = new OrmTotals();
		for ( int i = 0; i < 400; i++ ) {
			t.query( "app", "ds" + i, "select", 1, 1, false, true, "select 1" );
		}
		assertThat( rows( t ).size() ).isAtMost( OrmTotals.MAX_PAIRS + Math.max( 8, OrmTotals.MAX_PAIRS / 10 ) );
	}

	@Test
	@DisplayName( "reset forgets everything" )
	void reset() {
		OrmTotals t = new OrmTotals();
		t.query( "a", "d", "select", 1, 1, false, true, "select 1" );
		t.exception( "a", "d", "x", "y" );
		t.reset();
		assertThat( rows( t ) ).isEmpty();
		assertThat( t.snapshot().get( "failures" ) ).isEqualTo( List.of() );
	}

}
