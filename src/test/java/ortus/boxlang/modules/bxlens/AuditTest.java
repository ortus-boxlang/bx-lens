/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AuditTest {

	@Test
	@DisplayName( "line breaks and control characters cannot forge a log line" )
	void stripsControl() {
		String c = Audit.clean( "login.ok\r\nevent=admin.grant role=admin\u0000\u001b[31m\u2028x", 500 );
		assertThat( c ).doesNotContain( "\n" );
		assertThat( c ).doesNotContain( "\r" );
		assertThat( c ).doesNotContain( "\u0000" );
		assertThat( c ).doesNotContain( "\u001b" );
		assertThat( c ).doesNotContain( "\u2028" );
		assertThat( c ).isEqualTo( "login.ok event=admin.grant role=admin [31m x" );
	}

	@Test
	@DisplayName( "every field is cut to its length" )
	void caps() {
		assertThat( Audit.clean( "a".repeat( 10_000 ), 64 ) ).hasLength( 67 );
		assertThat( Audit.clean( "short", 64 ) ).isEqualTo( "short" );
		assertThat( Audit.clean( null, 64 ) ).isEmpty();
		assertThat( Audit.clean( "\n\n\n", 64 ) ).isEmpty();
	}

}
