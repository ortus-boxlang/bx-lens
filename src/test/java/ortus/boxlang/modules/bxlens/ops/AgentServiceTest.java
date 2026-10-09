/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.modules.bxlens.Licensing;
import ortus.boxlang.runtime.BoxRuntime;

public class AgentServiceTest {

	private final LensService	svc		= LensService.getInstance();
	private final AgentService	agents	= new AgentService( this.svc );

	private ortus.boxlang.modules.bxlens.ConsoleAuth.Session session( String role ) throws Exception {
		var ctor = ortus.boxlang.modules.bxlens.ConsoleAuth.Session.class.getDeclaredConstructors()[ 0 ];
		ctor.setAccessible( true );
		return ( ortus.boxlang.modules.bxlens.ConsoleAuth.Session ) ctor.newInstance( "sess-" + role, "csrf", "127.0.0.1", role, System.currentTimeMillis() );
	}

	@BeforeEach
	void runtime() {
		BoxRuntime.getInstance( true );
	}

	@AfterEach
	void restore() {
		this.svc.setConfig( LensConfig.defaults() );
		this.svc.setLicensing( new Licensing( "" ) );
		this.agents.shutdown();
	}

	@Test
	@DisplayName( "on Free the status says it is not available because of BoxLang+, and a chat is refused with 409" )
	void free() throws Exception {
		this.svc.setLicensing( new Licensing( "none" ) );
		this.svc.setConfig( new LensConfig( Map.of( "ai", Map.of( "enabled", true ) ) ) );
		Map<String, Object> st = this.agents.status( session( "admin" ) );
		assertThat( st.get( "available" ) ).isEqualTo( false );
		assertThat( st.get( "licensed" ) ).isEqualTo( false );
		assertThat( st.get( "tools" ) ).isEqualTo( 0 );
		AgentService.Refusal r = null;
		try {
			this.agents.chat( session( "admin" ), "127.0.0.1", "hello" );
		} catch ( AgentService.Refusal e ) {
			r = e;
		}
		assertThat( r ).isNotNull();
		assertThat( r.status ).isEqualTo( 409 );
		assertThat( r.plus ).isTrue();
		assertThat( r.getMessage() ).contains( "BoxLang+" );
	}

	@Test
	@DisplayName( "an empty question and a question that is too long are refused with 400" )
	void messageChecks() throws Exception {
		this.svc.setLicensing( new Licensing( "plus" ) );
		for ( String bad : new String[] { null, "", "   ", "x".repeat( AgentService.MAX_MESSAGE + 1 ) } ) {
			try {
				this.agents.chat( session( "admin" ), "127.0.0.1", bad );
				throw new AssertionError( "expected a refusal" );
			} catch ( AgentService.Refusal e ) {
				assertThat( e.status ).isEqualTo( 400 );
			}
		}
	}

	@Test
	@DisplayName( "with AI off the chat is refused with 409 and the status says why" )
	void off() throws Exception {
		this.svc.setLicensing( new Licensing( "plus" ) );
		this.svc.setConfig( new LensConfig( Map.of( "ai", Map.of( "enabled", false ) ) ) );
		assertThat( this.agents.status( session( "admin" ) ).get( "available" ) ).isEqualTo( false );
		try {
			this.agents.chat( session( "admin" ), "127.0.0.1", "hello" );
			throw new AssertionError( "expected a refusal" );
		} catch ( AgentService.Refusal e ) {
			assertThat( e.status ).isEqualTo( 409 );
		}
	}

	@Test
	@DisplayName( "the status counts the tools of the role: a viewer has fewer than an admin" )
	void toolCounts() throws Exception {
		this.svc.setLicensing( new Licensing( "plus" ) );
		int	admin	= ( int ) this.agents.status( session( "admin" ) ).get( "tools" );
		int	viewer	= ( int ) this.agents.status( session( "viewer" ) ).get( "tools" );
		assertThat( admin ).isEqualTo( Tools.all().size() );
		assertThat( viewer ).isLessThan( admin );
		assertThat( viewer ).isEqualTo( ( int ) Tools.all().stream().filter( t -> !t.admin() ).count() );
	}

	@Test
	@DisplayName( "dropping a session forgets its conversation and cancels what it waits for" )
	void dropCancelsApprovals() throws Exception {
		Approvals.Pending p = this.agents.approvals().open( "sess-admin", "runGc", Map.of(), "Run a garbage collection" );
		this.agents.drop( "sess-admin" );
		assertThat( p.state() ).isEqualTo( Approvals.State.CANCELLED );
		assertThat( this.agents.hasConversation( "sess-admin" ) ).isFalse();
	}

	@Test
	@DisplayName( "exceptions are shown without secrets and without a stack" )
	void friendlyErrors() {
		String msg = AgentService.friendly( new IllegalStateException( "failed calling http://user:pw123@host/x?token=abc" ) );
		assertThat( msg ).doesNotContain( "pw123" );
		assertThat( msg ).doesNotContain( "abc" );
		assertThat( AgentService.friendly( new RuntimeException( "MaxInteractionsExceeded: too many" ) ) ).contains( "more steps than allowed" );
	}

}
