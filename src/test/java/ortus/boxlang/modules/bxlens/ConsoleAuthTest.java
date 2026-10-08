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

}
