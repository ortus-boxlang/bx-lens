/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.IService;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

public class LicensingTest {

	/** Stands in for bx-plus. The methods are found by name, like the real service. */
	public static class FakeLicenseService implements IService {

		private final boolean	valid;
		private final boolean	trial;

		public FakeLicenseService( boolean valid, boolean trial ) {
			this.valid	= valid;
			this.trial	= trial;
		}

		public boolean isValidLicense() {
			return valid;
		}

		public boolean isTrialMode() {
			return trial;
		}

		@Override
		public Key getName() {
			return Key.of( "BoxlangLicenseService" );
		}

		@Override
		public void onConfigurationLoad() {
		}

		@Override
		public void onStartup() {
		}

		@Override
		public void onShutdown( Boolean force ) {
		}
	}

	private Licensing with( boolean valid, boolean trial ) {
		IStruct info = Struct.of( Key.message, "from bx-plus" );
		return new Licensing( () -> new FakeLicenseService( valid, trial ), () -> info, "" );
	}

	@Test
	@DisplayName( "Without bx-plus the state is free and nothing fails" )
	public void notInstalled() {
		Licensing l = new Licensing( () -> null, () -> null, "" );
		assertThat( l.status().get( "state" ) ).isEqualTo( "none" );
		assertThat( l.status().get( "label" ) ).isEqualTo( "Free" );
	}

	@Test
	@DisplayName( "A valid license is plus" )
	public void plus() {
		assertThat( with( true, false ).status().get( "state" ) ).isEqualTo( "plus" );
	}

	@Test
	@DisplayName( "A valid trial is a trial" )
	public void trial() {
		assertThat( with( true, true ).status().get( "state" ) ).isEqualTo( "trial" );
	}

	@Test
	@DisplayName( "An invalid license is expired and carries the message from bx-plus" )
	public void expired() {
		var s = with( false, false ).status();
		assertThat( s.get( "state" ) ).isEqualTo( "expired" );
		assertThat( s.get( "note" ) ).isEqualTo( "from bx-plus" );
	}

	@Test
	@DisplayName( "A failing check reports free instead of throwing" )
	public void failing() {
		Licensing l = new Licensing( () -> {
			throw new IllegalStateException( "boom" );
		}, () -> null, "" );
		assertThat( l.status().get( "state" ) ).isEqualTo( "none" );
	}

	@Test
	@DisplayName( "A developer override wins over detection" )
	public void override() {
		assertThat( new Licensing( () -> null, () -> null, "trial" ).status().get( "state" ) ).isEqualTo( "trial" );
		assertThat( new Licensing( () -> null, () -> null, "plus" ).status().get( "label" ) ).isEqualTo( "BoxLang+ active" );
	}

	@Test
	@DisplayName( "The answer is cached" )
	public void cached() {
		int[]		calls	= { 0 };
		Licensing	l		= new Licensing( () -> {
								calls[ 0 ]++;
								return new FakeLicenseService( true, false );
							}, () -> null, "" );
		l.status();
		l.status();
		assertThat( calls[ 0 ] ).isEqualTo( 1 );
	}

	@Test
	@DisplayName( "Every feature is available until the free and Plus split is decided" )
	public void featuresOpen() {
		assertThat( with( true, true ).has( "executors" ) ).isTrue();
	}

}
