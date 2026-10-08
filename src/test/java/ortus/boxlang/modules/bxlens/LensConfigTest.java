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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LensConfigTest {

	@Test
	@DisplayName( "Lens is off by default" )
	public void disabledByDefault() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.barEnabled ).isFalse();
		assertThat( cfg.consoleEnabled ).isFalse();
		assertThat( cfg.active ).isFalse();
		assertThat( cfg.collectLevel ).isEqualTo( "full" );
		assertThat( cfg.maxRequests ).isEqualTo( 50 );
		assertThat( cfg.slowQueryMs ).isEqualTo( 25 );
		assertThat( cfg.nPlusOneMin ).isEqualTo( 3 );
	}

	@Test
	@DisplayName( "Nested keys are read case insensitively with defaults for the missing ones" )
	public void nestedLookups() {
		LensConfig cfg = new LensConfig( Map.of(
		    "bar", Map.of( "enabled", true ),
		    "history", Map.of( "maxRequests", 5 ),
		    "collectors", Map.of( "Queries", Map.of( "enabled", false, "max", 7 ), "cache", true )
		) );
		assertThat( cfg.barEnabled ).isTrue();
		assertThat( cfg.active ).isTrue();
		assertThat( cfg.maxRequests ).isEqualTo( 5 );
		assertThat( cfg.trackNonHtml ).isTrue();
		assertThat( cfg.isCollectorEnabled( "queries", true ) ).isFalse();
		assertThat( cfg.collectorInt( "queries", "max", 200 ) ).isEqualTo( 7 );
		assertThat( cfg.isCollectorEnabled( "cache", false ) ).isTrue();
		assertThat( cfg.isCollectorEnabled( "functions", false ) ).isFalse();
		assertThat( cfg.isCollectorEnabled( "jvm", true ) ).isTrue();
	}

	@Test
	@DisplayName( "The console and the bar switch collection on independently" )
	public void surfaces() {
		assertThat( new LensConfig( Map.of( "console", Map.of( "enabled", true ) ) ).active ).isTrue();
		assertThat( new LensConfig( Map.of( "bar", Map.of( "enabled", true ), "collect", Map.of( "level", "off" ) ) ).active ).isFalse();
	}

	@Test
	@DisplayName( "Collect level accepts off, light and full and ignores anything else" )
	public void collectLevels() {
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "LIGHT" ) ) ).light ).isTrue();
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "off" ) ) ).collectLevel ).isEqualTo( "off" );
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "turbo" ) ) ).collectLevel ).isEqualTo( "full" );
	}

	@Test
	@DisplayName( "Tabs can be hidden from settings" )
	public void hiddenTabs() {
		assertThat( new LensConfig( Map.of( "tabs", Map.of( "hide", List.of( "Modules", "cache" ) ) ) ).hiddenTabs ).containsExactly( "modules", "cache" );
		assertThat( LensConfig.defaults().hiddenTabs ).isEmpty();
	}

	@Test
	@DisplayName( "The console path is never tracked, whatever excludePaths says" )
	public void consoleNeverTracked() {
		LensConfig cfg = new LensConfig( Map.of( "excludePaths", List.of() ) );
		assertThat( cfg.isExcluded( "/~bxlens/index.bxm" ) ).isTrue();
		assertThat( cfg.isExcluded( "/~bxlens/index.bxm/api/state" ) ).isTrue();
		assertThat( cfg.isExcluded( "/~bxlensfoo" ) ).isFalse();
	}

	@Test
	@DisplayName( "A plain console password is returned and an empty one is empty" )
	public void consolePassword() {
		assertThat( new LensConfig( Map.of( "console", Map.of( "password", "s3cret" ) ) ).consolePassword() ).isEqualTo( "s3cret" );
		assertThat( LensConfig.defaults().consolePassword() ).isEmpty();
	}

	@Test
	@DisplayName( "Excluded paths support a trailing wildcard" )
	public void excludedPaths() {
		LensConfig cfg = new LensConfig( Map.of( "excludePaths", List.of( "/~bxlens/*", "/health" ) ) );
		assertThat( cfg.isExcluded( "/~bxlens/lens.js" ) ).isTrue();
		assertThat( cfg.isExcluded( "/health" ) ).isTrue();
		assertThat( cfg.isExcluded( "/healthz" ) ).isFalse();
		assertThat( cfg.isExcluded( "/index.bxm" ) ).isFalse();
	}

	@Test
	@DisplayName( "Only configured content types are injectable" )
	public void injectableContentTypes() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.isInjectable( "text/html;charset=UTF-8" ) ).isTrue();
		assertThat( cfg.isInjectable( "TEXT/HTML" ) ).isTrue();
		assertThat( cfg.isInjectable( "application/json" ) ).isFalse();
		assertThat( cfg.isInjectable( "text/event-stream" ) ).isFalse();
		assertThat( cfg.isInjectable( null ) ).isFalse();
	}

	@Test
	@DisplayName( "Redaction matches keys by substring, case insensitive" )
	public void redaction() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.shouldRedact( "password" ) ).isTrue();
		assertThat( cfg.shouldRedact( "userPassword" ) ).isTrue();
		assertThat( cfg.shouldRedact( "X-Api-Token" ) ).isTrue();
		assertThat( cfg.shouldRedact( "Authorization" ) ).isTrue();
		assertThat( cfg.shouldRedact( "username" ) ).isFalse();
	}

}
