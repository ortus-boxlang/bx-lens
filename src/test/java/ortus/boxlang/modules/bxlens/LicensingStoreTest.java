/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LicensingStoreTest {

	@Test
	@DisplayName( "the disk store and the long history need Plus or a trial, everything else is open" )
	void gates() {
		assertThat( new Licensing( "plus" ).has( "diskStore" ) ).isTrue();
		assertThat( new Licensing( "trial" ).has( "diskStore" ) ).isTrue();
		assertThat( new Licensing( "plus" ).has( "fullHistory" ) ).isTrue();
		assertThat( new Licensing( "none" ).has( "diskStore" ) ).isFalse();
		assertThat( new Licensing( "expired" ).has( "fullHistory" ) ).isFalse();
		assertThat( new Licensing( "none" ).has( "executors" ) ).isTrue();
		assertThat( new Licensing( "none" ).has( "ai" ) ).isFalse();
		assertThat( new Licensing( "trial" ).has( "ai" ) ).isTrue();
	}

}
