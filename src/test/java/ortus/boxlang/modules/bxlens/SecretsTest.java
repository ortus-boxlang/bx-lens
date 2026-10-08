/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.modules.bxlens.util.Secrets;

public class SecretsTest {

	@Test
	@DisplayName( "values of secret looking names are hidden, others shown" )
	void byName() {
		assertThat( Secrets.show( "DB_PASSWORD", "x" ) ).isEqualTo( "[hidden]" );
		assertThat( Secrets.show( "AWS_SECRET_ACCESS_KEY", "x" ) ).isEqualTo( "[hidden]" );
		assertThat( Secrets.show( "GITHUB_TOKEN", "x" ) ).isEqualTo( "[hidden]" );
		assertThat( Secrets.show( "JAVA_HOME", "/opt/java" ) ).isEqualTo( "/opt/java" );
		assertThat( Secrets.show( "DB_PASSWORD", "" ) ).isEmpty();
	}

	@Test
	@DisplayName( "credentials inside URLs and encrypted values are hidden" )
	void byValue() {
		assertThat( Secrets.text( "postgres://bob:hunter2@db/app" ) ).isEqualTo( "postgres://[hidden]@db/app" );
		assertThat( Secrets.text( "jdbc:x://h/db?user=a&password=zzz&ssl=1" ) ).isEqualTo( "jdbc:x://h/db?user=a&password=[hidden]&ssl=1" );
		assertThat( Secrets.text( "bxsecret:abcdef" ) ).isEqualTo( "[encrypted]" );
	}

}
