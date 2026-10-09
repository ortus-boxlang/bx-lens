/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ConsoleAuthTest {

	private ConsoleAuth auth( Map<String, Object> console ) {
		return new ConsoleAuth( new LensConfig( Map.of( "console", console ) ) );
	}

	@Test
	@DisplayName( "Without a password nobody can sign in" )
	public void noPassword() {
		ConsoleAuth a = new ConsoleAuth( LensConfig.defaults() );
		assertThat( a.isConfigured() ).isFalse();
		assertThat( a.login( "", "1.2.3.4" ).ok() ).isFalse();
		assertThat( a.login( "anything", "1.2.3.4" ).ok() ).isFalse();
	}

	@Test
	@DisplayName( "The right password opens a session with a CSRF token, the wrong one does not" )
	public void loginAndSession() {
		ConsoleAuth a = auth( Map.of( "password", "hunter2" ) );
		assertThat( a.login( "wrong", "1.2.3.4" ).ok() ).isFalse();
		ConsoleAuth.LoginResult r = a.login( "hunter2", "1.2.3.4" );
		assertThat( r.ok() ).isTrue();
		assertThat( a.find( r.session().id ) ).isSameInstanceAs( r.session() );
		assertThat( a.csrfOk( r.session(), r.session().csrf ) ).isTrue();
		assertThat( a.csrfOk( r.session(), "nope" ) ).isFalse();
		assertThat( a.csrfOk( r.session(), null ) ).isFalse();
		assertThat( a.find( "unknown" ) ).isNull();
		a.logout( r.session().id );
		assertThat( a.find( r.session().id ) ).isNull();
	}

	@Test
	@DisplayName( "Too many failures lock the address out, even for the right password" )
	public void lockout() {
		ConsoleAuth a = auth( Map.of( "password", "hunter2", "maxLoginAttempts", 3, "lockoutMinutes", 5 ) );
		assertThat( a.login( "x", "9.9.9.9" ).attemptsLeft() ).isEqualTo( 2 );
		assertThat( a.login( "x", "9.9.9.9" ).attemptsLeft() ).isEqualTo( 1 );
		ConsoleAuth.LoginResult locked = a.login( "x", "9.9.9.9" );
		assertThat( locked.lockedSeconds() ).isGreaterThan( 0L );
		assertThat( a.login( "hunter2", "9.9.9.9" ).ok() ).isFalse();
		assertThat( a.login( "hunter2", "8.8.8.8" ).ok() ).isTrue();
	}

	@Test
	@DisplayName( "Session ids are long and unique" )
	public void uniqueIds() {
		ConsoleAuth	a	= auth( Map.of( "password", "p" ) );
		String		one	= a.login( "p", "1.1.1.1" ).session().id;
		String		two	= a.login( "p", "1.1.1.1" ).session().id;
		assertThat( one ).isNotEqualTo( two );
		assertThat( one.length() ).isAtLeast( 40 );
	}

	@Test
	@DisplayName( "the viewer password signs in with the viewer role and never as admin" )
	void viewerRole() {
		ConsoleAuth a = new ConsoleAuth( new LensConfig( Map.of( "console", Map.of( "password", "admin-pw", "viewerPassword", "view-pw" ) ) ) );
		assertThat( a.login( "view-pw", "1.1.1.1" ).session().role ).isEqualTo( "viewer" );
		assertThat( a.login( "admin-pw", "1.1.1.1" ).session().role ).isEqualTo( "admin" );
		assertThat( a.login( "nope", "1.1.1.1" ).ok() ).isFalse();
	}

	@Test
	@DisplayName( "a viewer password without an admin password is ignored" )
	void viewerNeedsAdmin() {
		ConsoleAuth a = new ConsoleAuth( new LensConfig( Map.of( "console", Map.of( "viewerPassword", "view-pw" ) ) ) );
		assertThat( a.isConfigured() ).isFalse();
		assertThat( a.login( "view-pw", "1.1.1.1" ).ok() ).isFalse();
	}

	private ConsoleAuth both( int max ) {
		return new ConsoleAuth( new LensConfig( Map.of( "console", Map.of( "password", "admin-pw", "viewerPassword", "view-pw", "maxLoginAttempts", max ) ) ) );
	}

	@Test
	@DisplayName( "a viewer sign in does not clear the failures counted against the admin password" )
	void viewerDoesNotResetAdminCounter() {
		ConsoleAuth a = both( 3 );
		assertThat( a.login( "guess1", "5.5.5.5" ).attemptsLeft() ).isEqualTo( 2 );
		assertThat( a.login( "guess2", "5.5.5.5" ).attemptsLeft() ).isEqualTo( 1 );
		// The attacker knows the viewer password. This must not give the admin guesses back
		assertThat( a.login( "view-pw", "5.5.5.5" ).ok() ).isTrue();
		ConsoleAuth.LoginResult third = a.login( "guess3", "5.5.5.5" );
		assertThat( third.lockedSeconds() ).isGreaterThan( 0L );
		assertThat( a.login( "admin-pw", "5.5.5.5" ).ok() ).isFalse();
	}

	@Test
	@DisplayName( "an admin sign in clears the admin counter" )
	void adminResets() {
		ConsoleAuth a = auth( Map.of( "password", "admin-pw", "maxLoginAttempts", 3 ) );
		a.login( "x", "6.6.6.6" );
		a.login( "x", "6.6.6.6" );
		assertThat( a.login( "admin-pw", "6.6.6.6" ).ok() ).isTrue();
		assertThat( a.login( "x", "6.6.6.6" ).attemptsLeft() ).isEqualTo( 2 );
	}

	@Test
	@DisplayName( "IPv6 addresses of one /64 share their failures, IPv4 addresses stay apart" )
	void ipv6Network() {
		assertThat( ConsoleAuth.clientKey( "2001:db8:1:2:aaaa::1" ) ).isEqualTo( ConsoleAuth.clientKey( "2001:db8:1:2:bbbb::9" ) );
		assertThat( ConsoleAuth.clientKey( "2001:db8:1:3::1" ) ).isNotEqualTo( ConsoleAuth.clientKey( "2001:db8:1:2::1" ) );
		assertThat( ConsoleAuth.clientKey( "1.2.3.4" ) ).isEqualTo( "1.2.3.4" );
		assertThat( ConsoleAuth.clientKey( null ) ).isEqualTo( "?" );
		ConsoleAuth a = auth( Map.of( "password", "p", "maxLoginAttempts", 2 ) );
		a.login( "x", "2001:db8:1:2::1" );
		assertThat( a.login( "x", "2001:db8:1:2::2" ).lockedSeconds() ).isGreaterThan( 0L );
	}

	@Test
	@DisplayName( "attempts and sessions are capped and pruned on every login" )
	void capped() {
		ConsoleAuth a = auth( Map.of( "password", "p" ) );
		for ( int i = 0; i < 6000; i++ ) {
			a.login( "x", "10." + ( i / 250 ) + "." + ( i % 250 ) + ".1" );
		}
		assertThat( a.attemptCount() ).isAtMost( 5001 );
		for ( int i = 0; i < 400; i++ ) {
			assertThat( a.login( "p", "7.7.7.7" ).ok() ).isTrue();
		}
		assertThat( a.sessionCount() ).isAtMost( 200 );
	}

	@Test
	@DisplayName( "peek checks a session without refreshing its idle timer" )
	void peek() throws Exception {
		ConsoleAuth			a	= auth( Map.of( "password", "p" ) );
		ConsoleAuth.Session	s	= a.login( "p", "1.1.1.1" ).session();
		long				t	= s.lastSeen;
		Thread.sleep( 15 );
		assertThat( a.peek( s.id ) ).isSameInstanceAs( s );
		assertThat( s.lastSeen ).isEqualTo( t );
		a.find( s.id );
		assertThat( s.lastSeen ).isGreaterThan( t );
		assertThat( a.peek( "nope" ) ).isNull();
	}

}
