package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LensServiceTest extends BaseIntegrationTest {

	private LensService service;

	@BeforeEach
	public void setupService() {
		service = new LensService( runtime );
		service.onStartup();
	}

	@Test
	@DisplayName( "startRequest returns a non-empty UUID string" )
	public void testStartRequestReturnsUUID() {
		String id = service.startRequest();
		assertThat( id ).isNotEmpty();
		// UUID pattern: 8-4-4-4-12
		assertThat( id ).matches( "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" );
	}

	@Test
	@DisplayName( "endRequest increments totalRequests in GlobalStats" )
	public void testEndRequestIncrementsCounter() {
		long before = service.getStats().totalRequests.get();
		service.endRequest( 100L );
		long after = service.getStats().totalRequests.get();
		assertThat( after ).isEqualTo( before + 1 );
	}

	@Test
	@DisplayName( "getMemoryInfo returns map with heapUsed key" )
	public void testGetMemoryInfo() {
		Map<String, Long> info = service.getMemoryInfo();
		assertThat( info ).containsKey( "heapUsed" );
		assertThat( info.get( "heapUsed" ) ).isGreaterThan( 0L );
	}

	@Test
	@DisplayName( "getThreadDump returns non-empty string containing 'Thread'" )
	public void testGetThreadDump() {
		String dump = service.getThreadDump();
		assertThat( dump ).isNotEmpty();
		assertThat( dump ).contains( "Thread" );
	}

	@Test
	@DisplayName( "GlobalStats.snapshot returns expected keys" )
	public void testGlobalStatsSnapshot() {
		service.endRequest( 50L );
		Map<String, Object> snap = service.getStats().snapshot();
		assertThat( snap ).containsKey( "totalRequests" );
		assertThat( snap ).containsKey( "totalQueries" );
		assertThat( snap ).containsKey( "totalExceptions" );
		assertThat( snap ).containsKey( "uptimeMs" );
		assertThat( snap ).containsKey( "avgRequestTimeMs" );
	}
}
