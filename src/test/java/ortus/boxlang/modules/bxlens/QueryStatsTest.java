/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;

public class QueryStatsTest {

	private static Map<String, Object> q( int span, String sql, double ms ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "span", span );
		m.put( "sql", sql );
		m.put( "ms", ms );
		m.put( "rows", 3 );
		m.put( "datasource", "app" );
		return m;
	}

	@Test
	@DisplayName( "the same statement is one entry with count, average, max and slow runs" )
	@SuppressWarnings( "unchecked" )
	void groups() {
		QueryStats	stats	= new QueryStats();
		LensRequest	req		= new LensRequest();
		req.queries.add( q( 1, "SELECT * FROM t WHERE id = ?", 10 ) );
		req.queries.add( q( 2, "SELECT  *  FROM t\n WHERE id = ?", 50 ) );
		req.queries.add( q( 3, "SELECT 1", 1 ) );
		stats.record( req, 25 );
		Map<String, Object>			snap	= stats.snapshot();
		List<Map<String, Object>>	rows	= ( List<Map<String, Object>> ) snap.get( "statements" );
		assertThat( rows ).hasSize( 2 );
		Map<String, Object> t = rows.stream().filter( r -> r.get( "sql" ).toString().contains( "FROM t" ) ).findFirst().get();
		assertThat( t.get( "count" ) ).isEqualTo( 2L );
		assertThat( t.get( "maxMs" ) ).isEqualTo( 50.0 );
		assertThat( t.get( "avgMs" ) ).isEqualTo( 30.0 );
		assertThat( t.get( "slow" ) ).isEqualTo( 1L );
		assertThat( snap.get( "executed" ) ).isEqualTo( 3L );
	}

	@Test
	@DisplayName( "a query that started and never finished counts as a failure" )
	@SuppressWarnings( "unchecked" )
	void failures() {
		QueryStats	stats	= new QueryStats();
		LensRequest	req		= new LensRequest();
		Span		span	= req.begin( Span.QUERY, "SELECT boom", 10 );
		span.detail.put( "sql", "SELECT boom" );
		stats.record( req, 25 );
		Map<String, Object> snap = stats.snapshot();
		assertThat( snap.get( "failed" ) ).isEqualTo( 1L );
		assertThat( ( ( List<Map<String, Object>> ) snap.get( "statements" ) ).get( 0 ).get( "lastError" ) ).isEqualTo( "The query did not finish" );
	}

	@Test
	@DisplayName( "a database exception with its statement counts once, and reset clears" )
	void exceptionFailure() {
		QueryStats			stats	= new QueryStats();
		LensRequest			req		= new LensRequest();
		Map<String, Object>	e		= new LinkedHashMap<>();
		e.put( "sql", "SELECT x FROM nope" );
		e.put( "message", "Table does not exist" );
		req.exceptions.add( e );
		stats.record( req, 25 );
		assertThat( stats.snapshot().get( "failed" ) ).isEqualTo( 1L );
		stats.reset();
		assertThat( stats.snapshot().get( "executed" ) ).isEqualTo( 0L );
	}

}
