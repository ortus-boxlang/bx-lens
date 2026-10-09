/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class IntegrationsTest {

	private static final Map<String, Object> ON = Map.of( "collectors", Map.of( "orm", Map.of( "enabled", true ) ) );

	@Test
	@DisplayName( "the status is notInstalled, available or on, from the module and the setting" )
	void statusTable() {
		assertThat( Integrations.status( false, false ) ).isEqualTo( "notInstalled" );
		assertThat( Integrations.status( false, true ) ).isEqualTo( "notInstalled" );
		assertThat( Integrations.status( true, false ) ).isEqualTo( "available" );
		assertThat( Integrations.status( true, true ) ).isEqualTo( "on" );
	}

	@Test
	@DisplayName( "orm is off by default, also when its module is installed" )
	void offByDefault() {
		Integrations		installed	= new Integrations( m -> true );
		Integrations.Def	orm			= Integrations.get( "orm" );
		assertThat( installed.status( orm, LensConfig.defaults() ) ).isEqualTo( "available" );
		assertThat( installed.on( "orm", LensConfig.defaults() ) ).isFalse();
		assertThat( new Integrations( m -> false ).status( orm, LensConfig.defaults() ) ).isEqualTo( "notInstalled" );
	}

	@Test
	@DisplayName( "the listeners are wanted only when the setting is on and the module is installed" )
	void onNeedsBoth() {
		LensConfig on = new LensConfig( ON );
		assertThat( new Integrations( m -> true ).on( "orm", on ) ).isTrue();
		assertThat( new Integrations( m -> false ).on( "orm", on ) ).isFalse();
		assertThat( new Integrations( m -> true ).on( "orm", LensConfig.defaults() ) ).isFalse();
		assertThat( new Integrations( m -> true ).on( "nope", on ) ).isFalse();
	}

	@Test
	@DisplayName( "the module name is what is looked up, and a failing lookup means not installed" )
	void lookup() {
		Set<String> asked = new HashSet<>();
		new Integrations( m -> asked.add( m ) ).status( Integrations.get( "ORM" ), LensConfig.defaults() );
		assertThat( asked ).containsExactly( "orm" );
		Integrations broken = new Integrations( m -> {
			throw new IllegalStateException( "no registry" );
		} );
		assertThat( broken.isInstalled( Integrations.get( "orm" ) ) ).isFalse();
	}

	@Test
	@DisplayName( "the console list says what is offered: a switch only when installed, a hint when not" )
	void list() {
		Map<String, Object> missing = new Integrations( m -> false ).list( new LensConfig( ON ) ).get( 0 );
		assertThat( missing.get( "id" ) ).isEqualTo( "orm" );
		assertThat( missing.get( "status" ) ).isEqualTo( "notInstalled" );
		assertThat( missing.get( "installed" ) ).isEqualTo( false );
		assertThat( missing.get( "enabled" ) ).isEqualTo( false );
		assertThat( missing.get( "hint" ) ).isEqualTo( "Install bx-orm to enable" );
		assertThat( missing.get( "setting" ) ).isEqualTo( "collectors.orm.enabled" );
		Map<String, Object> on = new Integrations( m -> true ).list( new LensConfig( ON ) ).get( 0 );
		assertThat( on.get( "status" ) ).isEqualTo( "on" );
		assertThat( on.get( "installed" ) ).isEqualTo( true );
		assertThat( on.get( "enabled" ) ).isEqualTo( true );
		assertThat( on.get( "hint" ) ).isEqualTo( "" );
	}

	@Test
	@DisplayName( "every integration has a unique id, a live setting that defaults to false, and no regex or reflection to a module" )
	void registryRules() {
		SettingsRegistry	registry	= new SettingsRegistry( Integrations.ALL.stream().map( Integrations.Def::id ).toList() );
		Set<String>			ids			= new HashSet<>();
		for ( Integrations.Def d : Integrations.ALL ) {
			assertThat( ids.add( d.id() ) ).isTrue();
			SettingsRegistry.Def s = registry.get( d.settingKey() );
			assertThat( s ).isNotNull();
			assertThat( s.def() ).isEqualTo( false );
			assertThat( s.live() ).isTrue();
			assertThat( registry.coerce( d.settingKey(), "true" ) ).isEqualTo( true );
			assertThat( d.module() ).isNotEmpty();
		}
	}

	@Test
	@DisplayName( "Lens no longer forces Hibernate statistics: the old setting is gone and an unknown key is refused" )
	void statisticsSettingGone() {
		SettingsRegistry registry = new SettingsRegistry( List.of( "orm" ) );
		assertThat( registry.get( "collectors.orm.statistics" ) ).isNull();
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "collectors.orm.statistics", "true" ) );
		assertThrows( IllegalArgumentException.class, () -> registry.coerce( "collectors.orm.enabled", "perhaps" ) );
	}

}
