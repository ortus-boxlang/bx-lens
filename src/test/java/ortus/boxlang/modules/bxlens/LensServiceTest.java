package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Tests for LensService.
 * Creates a fresh local LensService instance to avoid classloader isolation issues
 * when BoxLang loads the module with its own classloader.
 */
public class LensServiceTest extends BaseIntegrationTest {

	private LensService service;

	@BeforeEach
	public void setupService() {
		// Create a fresh instance using the test classloader — avoids module classloader cast issues
		service = new LensService( runtime );
		service.onStartup();
	}

	@Test
	@DisplayName( "LensService NAME key equals 'bxLensService'" )
	public void testServiceName() {
		assertThat( service.getName() ).isEqualTo( LensService.NAME );
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

	@Test
	@DisplayName( "activateCollectors registers collectors based on settings" )
	public void testActivateCollectors() {
		service.activateCollectors( moduleService.getModuleRecord( MODULE_NAME ).settings );
		assertThat( service.getCollectorCount() ).isGreaterThan( 0 );
		assertThat( service.hasCollector( "application" ) ).isTrue();
	}

	@Test
	@DisplayName( "getCollector returns present for 'application' after activate" )
	public void testGetCollector() {
		service.activateCollectors( moduleService.getModuleRecord( MODULE_NAME ).settings );
		assertThat( service.getCollector( "application" ).isPresent() ).isTrue();
	}

	@Test
	@DisplayName( "getAllCollectors returns non-empty collection after activate" )
	public void testGetAllCollectors() {
		service.activateCollectors( moduleService.getModuleRecord( MODULE_NAME ).settings );
		assertThat( service.getAllCollectors() ).isNotEmpty();
	}

	@Test
	@DisplayName( "deactivateCollectors clears the collector registry" )
	public void testDeactivateCollectors() {
		service.activateCollectors( moduleService.getModuleRecord( MODULE_NAME ).settings );
		assertThat( service.getCollectorCount() ).isGreaterThan( 0 );
		service.deactivateCollectors();
		assertThat( service.getCollectorCount() ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "getThreadDump with specific thread ID returns non-empty string" )
	public void testGetThreadDumpById() {
		long	currentThreadId	= Thread.currentThread().getId();
		String	dump			= service.getThreadDump( currentThreadId );
		assertThat( dump ).isNotEmpty();
	}

	@Test
	@DisplayName( "LensService is available in global registry after module activation" )
	public void testGlobalServiceRegistered() {
		// Verify the service is in the global registry (registered by module's onLoad)
		Object svc = runtime.getGlobalService( Key.of( "bxLensService" ) );
		assertThat( svc ).isNotNull();
	}
}
