/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class OrmDataTest {

	/** Looks like bx-orm's service: the two calls Lens reflects into and the app list. */
	public static class FakeOrmService {

		public final List<String>	switched	= new ArrayList<>();
		public boolean				on;

		public List<String> getORMAppNames() {
			return List.of( "shop" );
		}

		public IStruct getStatistics( Key app ) {
			IStruct entry = new Struct();
			entry.put( Key.of( "enabled" ), this.on );
			if ( this.on ) {
				entry.put( Key.of( "startTime" ), 1000L );
				entry.put( Key.of( "queryExecutionCount" ), 7L );
				entry.put( Key.of( "flushCount" ), 2L );
			}
			IStruct ds = new Struct();
			ds.put( Key.of( "demo" ), entry );
			return Struct.of( Key.of( "appName" ), app.getName(), Key.of( "datasources" ), ds );
		}

		public void setStatisticsEnabled( Key app, boolean enabled ) {
			this.on = enabled;
			this.switched.add( app.getName() + "=" + enabled );
		}
	}

	/** An older bx-orm: no statistics API. */
	public static class OldOrmService {

		public List<String> getORMAppNames() {
			return List.of( "shop" );
		}
	}

	@SuppressWarnings( "unchecked" )
	private static Map<String, Object> firstApp( Map<String, Object> stats ) {
		return ( Map<String, Object> ) ( ( List<Object> ) stats.get( "apps" ) ).get( 0 );
	}

	@Test
	@DisplayName( "statistics that are off show enabled false and the message that says how to turn them on" )
	@SuppressWarnings( "unchecked" )
	void off() {
		OrmData				data	= new OrmData( FakeOrmService::new );
		Map<String, Object>	stats	= data.statistics();
		assertThat( stats.get( "supported" ) ).isEqualTo( true );
		assertThat( stats.get( "offMessage" ).toString() )
		    .isEqualTo( "statistics are off in this app: set generateStatistics in ormSettings, or turn them on here" );
		Map<String, Object> app = firstApp( stats );
		assertThat( app.get( "app" ) ).isEqualTo( "shop" );
		assertThat( app.get( "enabled" ) ).isEqualTo( false );
		Map<String, Object> ds = ( ( List<Map<String, Object>> ) app.get( "datasources" ) ).get( 0 );
		assertThat( ds.get( "name" ) ).isEqualTo( "demo" );
		assertThat( ds.get( "enabled" ) ).isEqualTo( false );
		assertThat( ds ).doesNotContainKey( "queryExecutionCount" );
	}

	@Test
	@DisplayName( "switching statistics on goes through ORMService.setStatisticsEnabled and the counters come back as plain values" )
	@SuppressWarnings( "unchecked" )
	void switchOn() {
		FakeOrmService	svc		= new FakeOrmService();
		OrmData			data	= new OrmData( () -> svc );
		data.setStatistics( "shop", true );
		assertThat( svc.switched ).containsExactly( "shop=true" );
		Map<String, Object> app = firstApp( data.statistics() );
		assertThat( app.get( "enabled" ) ).isEqualTo( true );
		Map<String, Object> ds = ( ( List<Map<String, Object>> ) app.get( "datasources" ) ).get( 0 );
		assertThat( ds.get( "queryExecutionCount" ) ).isEqualTo( 7L );
		assertThat( ds.get( "flushCount" ) ).isEqualTo( 2L );
		data.setStatistics( "shop", false );
		assertThat( svc.switched ).containsExactly( "shop=true", "shop=false" );
	}

	@Test
	@DisplayName( "an unknown application is refused without calling bx-orm" )
	void unknownApp() {
		FakeOrmService	svc		= new FakeOrmService();
		OrmData			data	= new OrmData( () -> svc );
		assertThrows( IllegalArgumentException.class, () -> data.setStatistics( "other", true ) );
		assertThrows( IllegalArgumentException.class, () -> data.setStatistics( null, true ) );
		assertThat( svc.switched ).isEmpty();
	}

	@Test
	@DisplayName( "no bx-orm, or one without the statistics API, reports that and cannot switch" )
	void unsupported() {
		OrmData none = new OrmData( () -> null );
		assertThat( none.statistics().get( "serviceFound" ) ).isEqualTo( false );
		assertThat( none.statistics().get( "apps" ) ).isEqualTo( List.of() );
		assertThat( none.canSwitchStatistics() ).isFalse();
		assertThrows( IllegalStateException.class, () -> none.setStatistics( "shop", true ) );
		OrmData old = new OrmData( OldOrmService::new );
		assertThat( old.statistics().get( "serviceFound" ) ).isEqualTo( true );
		assertThat( old.statistics().get( "supported" ) ).isEqualTo( false );
		assertThat( old.canSwitchStatistics() ).isFalse();
		assertThrows( IllegalStateException.class, () -> old.setStatistics( "shop", true ) );
		assertThat( new OrmData( FakeOrmService::new ).canSwitchStatistics() ).isTrue();
	}

}
