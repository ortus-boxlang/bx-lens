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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SettingsStoreTest {

	private final SettingsRegistry registry = new SettingsRegistry( List.of( "queries", "logs" ) );

	@Test
	@DisplayName( "values are coerced to the right type and range checked" )
	void coerces() {
		assertThat( registry.coerce( "thresholds.slowQueryMs", "40" ) ).isEqualTo( 40 );
		assertThat( registry.coerce( "bar.enabled", "true" ) ).isEqualTo( true );
		assertThat( registry.coerce( "collect.level", "LIGHT" ) ).isEqualTo( "light" );
		assertThat( registry.coerce( "tabs.hide", "a, b" ) ).isEqualTo( List.of( "a", "b" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "thresholds.slowQueryMs", "-1" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "thresholds.slowQueryMs", "abc" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "collect.level", "loud" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "bar.enabled", "maybe" ) );
	}

	@Test
	@DisplayName( "locked and unknown keys cannot be changed" )
	void rejectsLocked() {
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "console.password", "x" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "bar.access", "all" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "console.readOnly", "false" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "dev.license", "plus" ) );
	}

	@Test
	@DisplayName( "overrides are saved, reloaded, and one bad value saves nothing" )
	void persists( @TempDir Path dir ) throws Exception {
		Path			file	= dir.resolve( "config" ).resolve( "o.json" );
		SettingsStore	store	= new SettingsStore( file, registry );
		store.set( Map.of( "thresholds.slowQueryMs", 99, "collectors.logs.enabled", true ) );
		assertThat( new SettingsStore( file, registry ).get() ).containsEntry( "thresholds.slowQueryMs", 99 );
		assertThrows( IllegalArgumentException.class, () -> store.set( Map.of( "thresholds.slowQueryMs", 5, "console.password", "x" ) ) );
		assertThat( store.get() ).containsEntry( "thresholds.slowQueryMs", 99 );
	}

	@Test
	@DisplayName( "a hand-edited file cannot switch on a locked setting, and a broken file is ignored" )
	void ignoresBadFile( @TempDir Path dir ) throws Exception {
		Path file = dir.resolve( "o.json" );
		Files.writeString( file, "{\"overrides\":{\"console.readOnly\":false,\"bar.access\":\"all\",\"thresholds.slowQueryMs\":12}}" );
		SettingsStore store = new SettingsStore( file, registry );
		assertThat( store.get().keySet() ).containsExactly( "thresholds.slowQueryMs" );
		assertThat( store.skipped() ).isEqualTo( 2 );
		Files.writeString( file, "not json" );
		assertThat( new SettingsStore( file, registry ).get() ).isEmpty();
	}

	@Test
	@DisplayName( "overlay applies dotted overrides on top of the base without changing it" )
	void overlay() {
		Map<String, Object>	base	= Map.of( "thresholds", Map.of( "slowQueryMs", 25 ), "collectors", Map.of( "logs", false ) );
		LensConfig			cfg		= new LensConfig( LensConfig.overlay( base, Map.of( "thresholds.slowQueryMs", 7, "collectors.logs.enabled", true ) ) );
		assertThat( cfg.slowQueryMs ).isEqualTo( 7 );
		assertThat( cfg.isCollectorEnabled( "logs", false ) ).isTrue();
		assertThat( new LensConfig( base ).slowQueryMs ).isEqualTo( 25 );
	}

}
