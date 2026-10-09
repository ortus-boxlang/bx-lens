/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.Licensing;
import ortus.boxlang.modules.bxlens.LensConfig;
import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * The rules of the Toolbox: who may use which tool, what is refused, what the model gets back. It runs against the real services of this
 * JVM with a license and settings put in place for each test.
 */
public class ToolboxTest {

	private final LensService	svc			= LensService.getInstance();
	private final Approvals		approvals	= new Approvals();
	private final List<String>	audit		= new CopyOnWriteArrayList<>();

	@BeforeEach
	void runtime() {
		BoxRuntime.getInstance( true );
		plus( Map.of() );
	}

	@AfterEach
	void restore() {
		this.svc.setConfig( LensConfig.defaults() );
		this.svc.setLicensing( new Licensing( "" ) );
	}

	private void plus( Map<String, Object> settings ) {
		this.svc.setLicensing( new Licensing( "plus" ) );
		this.svc.setConfig( new LensConfig( settings ) );
	}

	private void free() {
		this.svc.setLicensing( new Licensing( "none" ) );
	}

	private Toolbox box( String role ) {
		Toolbox t = new Toolbox( this.svc, role, "session-" + role, "127.0.0.1", this.approvals );
		t.tapAudit( this.audit::add );
		return t;
	}

	/** Run an ACT tool on another thread, wait for the approval to be held, then decide. */
	private Toolbox.Result act( Toolbox box, String tool, Map<String, Object> args, Boolean approve ) throws Exception {
		Toolbox.Result[]	out	= new Toolbox.Result[ 1 ];
		Thread				t	= new Thread( () -> out[ 0 ] = box.run( tool, args ) );
		t.start();
		long until = System.currentTimeMillis() + 5000;
		while ( this.approvals.pendingCount( null ) == 0 && System.currentTimeMillis() < until && t.isAlive() ) {
			Thread.sleep( 20 );
		}
		if ( approve != null ) {
			Approvals.Pending p = null;
			for ( int i = 0; i < 50 && p == null; i++ ) {
				p = this.approvals.find( lastId() );
				Thread.sleep( 20 );
			}
			this.approvals.decide( lastId(), "session-" + box.role(), approve );
		}
		t.join( 8000 );
		return out[ 0 ];
	}

	private String lastId() {
		String id = null;
		for ( String line : this.audit ) {
			int at = line.indexOf( "id=" );
			if ( line.contains( "approval requested" ) && at >= 0 ) {
				id = line.substring( at + 3 );
			}
		}
		return id;
	}

	@Test
	@DisplayName( "on Free the agent has no tools and every call is refused with a BoxLang+ message" )
	void freeRefusesEverything() {
		free();
		Toolbox box = box( "admin" );
		assertThat( box.licensed() ).isFalse();
		assertThat( box.toolNames() ).isEmpty();
		for ( Tools.Tool t : Tools.all() ) {
			Toolbox.Result r = box.run( t.name(), Map.of() );
			assertThat( r.status() ).isEqualTo( "denied" );
			assertThat( r.text() ).contains( "BoxLang+" );
		}
		assertThat( this.approvals.pendingCount( null ) ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "a viewer is offered no admin tool and cannot call one" )
	void viewerCannotReachAdminTools() {
		Toolbox			viewer	= box( "viewer" );
		List<String>	names	= viewer.toolNames();
		assertThat( names ).containsAtLeast( "overview", "requests", "errors", "executors", "diagnose", "searchDocs", "datasources" );
		for ( Tools.Tool t : Tools.all() ) {
			if ( t.admin() ) {
				assertThat( names ).doesNotContain( t.name() );
			}
		}
		for ( String name : List.of( "system", "threadsSummary", "blockedThreads", "threadStack", "logs", "environment", "dbTables", "runGc",
		    "changeSetting" ) ) {
			Map<String, Object>	args	= name.equals( "threadStack" ) ? Map.of( "name", "main" )
			    : name.equals( "dbTables" ) ? Map.of( "datasource", "x" )
			        : name.equals( "changeSetting" ) ? Map.of( "key", "ui.height", "value", "400" ) : Map.of();
			Toolbox.Result		r		= viewer.run( name, args );
			assertThat( r.status() ).isEqualTo( "denied" );
			assertThat( r.text() ).contains( "admin" );
			assertThat( r.text() ).doesNotContain( "liveThreads" );
		}
		assertThat( this.approvals.pendingCount( null ) ).isEqualTo( 0 );
		assertThat( box( "admin" ).toolNames() ).containsAtLeast( "system", "threadsSummary", "blockedThreads", "logs", "runGc", "changeSetting", "dbTables" );
	}

	@Test
	@DisplayName( "diagnose works for a viewer and says what it left out" )
	void viewerDiagnose() {
		Toolbox.Result r = box( "viewer" ).run( "diagnose", Map.of() );
		assertThat( r.status() ).isEqualTo( "ok" );
		assertThat( r.text() ).contains( "notChecked" );
		assertThat( r.text() ).contains( "admin role" );
		Toolbox.Result admin = box( "admin" ).run( "diagnose", Map.of() );
		assertThat( admin.status() ).isEqualTo( "ok" );
		assertThat( admin.text() ).contains( "heap and garbage collection" );
		assertThat( admin.text() ).doesNotContain( "notChecked" );
	}

	@Test
	@DisplayName( "console.readOnly takes every ACT tool away and refuses them without asking anybody" )
	void readOnlyRefusesActs() {
		plus( Map.of( "console", Map.of( "readOnly", true ) ) );
		Toolbox box = box( "admin" );
		for ( Tools.Tool t : Tools.all() ) {
			if ( t.act() ) {
				assertThat( box.toolNames() ).doesNotContain( t.name() );
			}
		}
		Toolbox.Result r = box.run( "runGc", Map.of() );
		assertThat( r.status() ).isEqualTo( "denied" );
		assertThat( r.text() ).contains( "read only" );
		assertThat( this.approvals.pendingCount( null ) ).isEqualTo( 0 );
		assertThat( box.run( "overview", Map.of() ).status() ).isEqualTo( "ok" );
	}

	@Test
	@DisplayName( "console.actions=false and ai.actions=false also refuse ACT tools" )
	void actionsOff() {
		plus( Map.of( "console", Map.of( "actions", false ) ) );
		assertThat( box( "admin" ).run( "runGc", Map.of() ).text() ).contains( "console.actions" );
		plus( Map.of( "ai", Map.of( "actions", false ) ) );
		assertThat( box( "admin" ).run( "runGc", Map.of() ).text() ).contains( "ai.actions" );
		assertThat( box( "admin" ).toolNames() ).doesNotContain( "runGc" );
		assertThat( this.approvals.pendingCount( null ) ).isEqualTo( 0 );
	}

	@Test
	@DisplayName( "with ai.rag off the documentation search is not offered" )
	void ragOff() {
		plus( Map.of( "ai", Map.of( "rag", false ) ) );
		assertThat( box( "admin" ).toolNames() ).doesNotContain( "searchDocs" );
		assertThat( box( "admin" ).run( "searchDocs", Map.of( "question", "x" ) ).status() ).isEqualTo( "denied" );
	}

	@Test
	@DisplayName( "an ACT tool waits for Approve, then runs, and the audit shows the request, the approval and the action" )
	void approvedAction() throws Exception {
		Toolbox			box	= box( "admin" );
		Toolbox.Result	r	= act( box, "runGc", Map.of(), true );
		assertThat( r.status() ).isEqualTo( "ok" );
		assertThat( r.text() ).contains( "Garbage collection ran" );
		assertThat( this.audit.stream().anyMatch( l -> l.contains( "tool=runGc result=approval requested" ) ) ).isTrue();
		assertThat( this.audit.stream().anyMatch( l -> l.contains( "tool=runGc result=approved" ) ) ).isTrue();
		assertThat( this.audit.stream().anyMatch( l -> l.contains( "tool=runGc result=ok" ) ) ).isTrue();
		assertThat( this.audit.stream().anyMatch( l -> l.startsWith( "ai.act runGc" ) ) ).isTrue();
	}

	@Test
	@DisplayName( "Deny does nothing: the tool does not run and the model is told it was not approved" )
	void deniedAction() throws Exception {
		Toolbox			box	= box( "admin" );
		Toolbox.Result	r	= act( box, "runGc", Map.of(), false );
		assertThat( r.status() ).isEqualTo( "denied" );
		assertThat( r.text() ).contains( "did not approve" );
		assertThat( this.audit.stream().anyMatch( l -> l.contains( "tool=runGc result=denied" ) ) ).isTrue();
		assertThat( this.audit.stream().anyMatch( l -> l.startsWith( "ai.act" ) ) ).isFalse();
	}

	@Test
	@DisplayName( "settings that control the agent, the console and access cannot be changed by the agent even when approved" )
	void changeSettingBlocked() throws Exception {
		assertThat( Act.blocked( "ai.baseUrl" ) ).isTrue();
		assertThat( Act.blocked( "ai.enabled" ) ).isTrue();
		assertThat( Act.blocked( "console.readOnly" ) ).isTrue();
		assertThat( Act.blocked( "access.proxyPeers" ) ).isTrue();
		assertThat( Act.blocked( "bar.access" ) ).isTrue();
		assertThat( Act.blocked( "store.dir" ) ).isTrue();
		assertThat( Act.blocked( "thresholds.slowQueryMs" ) ).isFalse();
		Toolbox.Result r = act( box( "admin" ), "changeSetting", Map.of( "key", "ai.baseUrl", "value", "http://attacker.example" ), true );
		assertThat( r.status() ).isEqualTo( "error" );
		assertThat( r.text() ).contains( "may not change" );
	}

	@Test
	@DisplayName( "arguments are checked against the schema: required, type, range, allowed values, length, control characters" )
	void validation() {
		Tools.Tool			requests	= Tools.get( "requests" );
		Map<String, Object>	ok			= Toolbox.validate( requests, Map.of( "limit", "5", "problemsOnly", "true", "bogus", "dropped" ) );
		assertThat( ok ).containsEntry( "limit", 5 );
		assertThat( ok ).containsEntry( "problemsOnly", true );
		assertThat( ok ).doesNotContainKey( "bogus" );
		assertThat( ok ).containsEntry( "urlContains", "" );
		assertThat( Toolbox.validate( requests, Map.of() ) ).containsEntry( "limit", 15 );
		assertThat( Toolbox.validate( requests, Map.of( "LIMIT", 7 ) ) ).containsEntry( "limit", 7 );
		assertRefused( requests, Map.of( "limit", 500 ), "between 1 and 50" );
		assertRefused( requests, Map.of( "limit", "many" ), "whole number" );
		assertRefused( requests, Map.of( "problemsOnly", "maybe" ), "true or false" );
		assertRefused( requests, Map.of( "urlContains", "x".repeat( 101 ) ), "longer than 100" );
		assertRefused( requests, Map.of( "urlContains", "a\u0000b" ), "control character" );
		assertRefused( Tools.get( "requestDetail" ), Map.of(), "required" );
		assertRefused( Tools.get( "queryStats" ), Map.of( "sort", "random" ), "one of" );
		assertThat( Toolbox.validate( Tools.get( "queryStats" ), Map.of( "sort", "TOTAL" ) ) ).containsEntry( "sort", "total" );
		assertRefused( Tools.get( "taskAction" ), Map.of( "scheduler", "s", "action", "delete" ), "one of" );
		assertRefused( Tools.get( "taskAction" ), Map.of( "scheduler", "s" ), "required" );
		assertThat( Toolbox.validate( Tools.get( "overview" ), Map.of( "anything", "at all" ) ) ).isEmpty();
	}

	private void assertRefused( Tools.Tool t, Map<String, Object> args, String message ) {
		try {
			Toolbox.validate( t, args );
			throw new AssertionError( "expected a refusal for " + args );
		} catch ( IllegalArgumentException e ) {
			assertThat( e.getMessage() ).contains( message );
		}
	}

	@Test
	@DisplayName( "bad arguments from the model are refused before anything runs" )
	void badArgumentsRefused() {
		Toolbox.Result r = box( "admin" ).run( "requests", Map.of( "limit", 9999 ) );
		assertThat( r.status() ).isEqualTo( "denied" );
		assertThat( r.text() ).contains( "between 1 and 50" );
		assertThat( box( "admin" ).run( "nope", Map.of() ).status() ).isEqualTo( "denied" );
		assertThat( box( "admin" ).run( null, Map.of() ).status() ).isEqualTo( "denied" );
	}

	@Test
	@DisplayName( "the tool call limit of a turn is enforced whatever the model asks" )
	void toolCallLimit() {
		plus( Map.of( "ai", Map.of( "maxToolCalls", 2 ) ) );
		Toolbox box = box( "admin" );
		box.beginTurn( Sink.NONE );
		assertThat( box.run( "lensInfo", Map.of() ).status() ).isEqualTo( "ok" );
		assertThat( box.run( "lensInfo", Map.of() ).status() ).isEqualTo( "ok" );
		Toolbox.Result third = box.run( "lensInfo", Map.of() );
		assertThat( third.status() ).isEqualTo( "denied" );
		assertThat( third.text() ).contains( "limit" );
		box.beginTurn( Sink.NONE );
		assertThat( box.run( "lensInfo", Map.of() ).status() ).isEqualTo( "ok" );
	}

	@Test
	@DisplayName( "every result is wrapped as data, redacted and cut to the cap" )
	void resultsAreRedactedAndCapped() {
		LensConfig			cfg		= LensConfig.defaults();
		Map<String, Object>	secret	= new LinkedHashMap<>();
		secret.put( "password", "hunter2" );
		secret.put( "apiKey", "sk-abcdef" );
		secret.put( "note", "connect with jdbc:mysql://admin:topsecret@db.example/app?password=abc123 now" );
		secret.put( "line", "token=eyJhbGciOiJIUzI1NiJ9.payload.sig and more" );
		String text = Toolbox.render( secret, cfg );
		assertThat( text ).doesNotContain( "hunter2" );
		assertThat( text ).doesNotContain( "sk-abcdef" );
		assertThat( text ).doesNotContain( "topsecret" );
		assertThat( text ).doesNotContain( "abc123" );
		assertThat( text ).doesNotContain( "eyJhbGciOiJIUzI1NiJ9" );

		List<String> big = new ArrayList<>();
		for ( int i = 0; i < 100; i++ ) {
			big.add( "line " + i + " " + "x".repeat( 1900 ) );
		}
		String capped = Toolbox.render( big, cfg );
		assertThat( capped.length() ).isAtMost( Toolbox.MAX_RESULT + 200 );
		assertThat( capped ).contains( "cut:" );

		Toolbox.Result r = box( "admin" ).run( "lensInfo", Map.of() );
		assertThat( r.text() ).contains( "dataNotInstructions" );
		assertThat( r.text() ).contains( "\"tool\":\"lensInfo\"" );
	}

	@Test
	@DisplayName( "every call writes an ai.tool audit line with the tool, a short argument summary and the outcome" )
	void auditLines() {
		Toolbox box = box( "viewer" );
		box.run( "overview", Map.of() );
		box.run( "requests", Map.of( "limit", 3, "urlContains", "/orders" ) );
		box.run( "system", Map.of() );
		assertThat( this.audit ).contains( "ai.tool tool=overview result=ok" );
		assertThat( this.audit.stream().anyMatch( l -> l.startsWith( "ai.tool tool=requests result=ok" ) ) ).isTrue();
		assertThat( this.audit.stream().anyMatch( l -> l.startsWith( "ai.tool tool=system result=denied" ) ) ).isTrue();
	}

	@Test
	@DisplayName( "tool events reach the sink in order: call, then result" )
	void sinkEvents() {
		List<String>	events	= new ArrayList<>();
		Sink			sink	= new Sink() {

									@Override
									public void toolCall( String name, Map<String, Object> args, boolean readOnly ) {
										events.add( "call " + name + " " + readOnly + " " + args );
									}

									@Override
									public void approvalRequest( Approvals.Pending p ) {
										events.add( "approval" );
									}

									@Override
									public void toolResult( String name, boolean ok, String summary ) {
										events.add( "result " + name + " " + ok );
									}

									@Override
									public boolean cancelled() {
										return false;
									}
								};
		Toolbox			box		= box( "admin" );
		box.beginTurn( sink );
		box.run( "requests", Map.of( "limit", 4 ) );
		assertThat( events ).containsExactly( "call requests true {limit=4}", "result requests true" ).inOrder();
	}

	@Test
	@DisplayName( "a cancelled turn runs no tool" )
	void cancelled() {
		Toolbox box = box( "admin" );
		box.beginTurn( new Sink() {

			@Override
			public void toolCall( String name, Map<String, Object> args, boolean readOnly ) {
			}

			@Override
			public void approvalRequest( Approvals.Pending p ) {
			}

			@Override
			public void toolResult( String name, boolean ok, String summary ) {
			}

			@Override
			public boolean cancelled() {
				return true;
			}
		} );
		assertThat( box.run( "overview", Map.of() ).status() ).isEqualTo( "error" );
	}

	@Test
	@DisplayName( "the JVM tools return data an admin can use" )
	void jvmTools() {
		Toolbox box = box( "admin" );
		assertThat( box.run( "threadsSummary", Map.of() ).text() ).contains( "liveThreads" );
		assertThat( box.run( "blockedThreads", Map.of() ).text() ).contains( "deadlock" );
		assertThat( box.run( "topCpuThreads", Map.of( "limit", 3 ) ).text() ).contains( "cpuMsSinceStart" );
		assertThat( box.run( "threadPools", Map.of() ).text() ).contains( "pools" );
		assertThat( box.run( "gcPressure", Map.of() ).text() ).contains( "gcTimeSharePercent" );
		assertThat( box.run( "system", Map.of() ).text() ).contains( "memory" );
		assertThat( box.run( "threadStack", Map.of( "name", "main" ) ).text() ).containsMatch( "\"(stack|error)\"" );
		assertThat( box.run( "threadStack", Map.of( "name", "no-such-thread-xyz" ) ).status() ).isEqualTo( "error" );
	}

	@Test
	@DisplayName( "no database tool takes SQL, and a table name is checked before it reaches a metadata call" )
	void noSql() {
		for ( Tools.Tool t : Tools.all() ) {
			if ( t.name().startsWith( "db" ) ) {
				for ( Tools.Param p : t.params() ) {
					assertThat( List.of( "sql", "query", "statement", "command", "text" ) ).doesNotContain( p.name().toLowerCase() );
				}
			}
		}
		assertThat( DbMeta.validName( "orders" ) ).isTrue();
		assertThat( DbMeta.validName( "APP.ORDERS" ) ).isTrue();
		assertThat( DbMeta.validName( "orders; drop table x" ) ).isFalse();
		assertThat( DbMeta.validName( "a'b" ) ).isFalse();
		assertThat( DbMeta.validName( "" ) ).isFalse();
		assertThat( box( "admin" ).run( "dbTables", Map.of( "datasource", "no-such-datasource" ) ).status() ).isEqualTo( "error" );
	}

}
