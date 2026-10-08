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

public class AiPromptsTest {

	@Test
	@DisplayName( "an error prompt carries the message, request, stack and context, with credentials in URLs hidden and a size cap" )
	void error() {
		Map<String, Object> group = new LinkedHashMap<>();
		group.put( "type", "KeyNotFoundException" );
		group.put( "count", 4L );
		Map<String, Object> sample = new LinkedHashMap<>();
		sample.put( "message", "The key [total] was not found, connecting to postgres://bob:hunter2@db/app" );
		sample.put( "method", "GET" );
		sample.put( "uri", "/orders.bxm" );
		sample.put( "query", "id=5&password=[redacted]" );
		sample.put( "status", 500 );
		sample.put( "ms", 12.5 );
		sample.put( "frames", List.of( Map.of( "file", "/app/orders.bxm", "line", 9 ) ) );
		sample.put( "java", List.of( "ortus.boxlang.Thing.run(Thing.java:1)" ) );
		sample.put( "lastQueries", List.of( "SELECT * FROM orders WHERE id = ?" ) );
		String p = AiPrompts.error( group, sample );
		assertThat( p ).contains( "KeyNotFoundException" );
		assertThat( p ).contains( "GET /orders.bxm?id=5&password=[" );
		assertThat( p ).contains( "bx  /app/orders.bxm:9" );
		assertThat( p ).contains( "SELECT * FROM orders WHERE id = ?" );
		assertThat( p ).doesNotContain( "hunter2" );
		sample.put( "message", "x".repeat( 20_000 ) );
		assertThat( AiPrompts.error( group, sample ).length() ).isAtMost( AiPrompts.MAX + 10 );
	}

	@Test
	@DisplayName( "deadlock, query and ask prompts name what they are about" )
	void others() {
		Map<String, Object> t = new LinkedHashMap<>();
		t.put( "name", "worker-1" );
		t.put( "state", "BLOCKED" );
		t.put( "lock", "java.lang.Object@1" );
		t.put( "lockOwner", "worker-2" );
		assertThat( AiPrompts.deadlock( List.of( t ) ) ).contains( "worker-1" );
		Map<String, Object> q = new LinkedHashMap<>();
		q.put( "sql", "SELECT 1" );
		q.put( "datasource", "app" );
		assertThat( AiPrompts.query( q ) ).contains( "SELECT 1" );
		assertThat( AiPrompts.ask( "why slow?", "Requests 5" ) ).contains( "why slow?" );
	}

}
