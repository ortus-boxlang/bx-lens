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

	@Test
	@DisplayName( "the shared matcher knows the extended list of secret names" )
	void extendedNames() {
		for ( String n : new String[] { "key", "api_key", "secretKey", "apiKey", "X-Api-Key", "pass", "pin", "PIN", "cvv", "jwt", "auth", "session",
		    "sessionId", "csrf",
		    "webhook", "dsn", "SENTRY_DSN", "sk_live_abc", "cardnumber", "cardNumber", "accessToken", "access_token", "clientSecret",
		    "javax.net.ssl.trustStorePassword",
		    "sslpassword", "sslPassword", "Authorization", "DB_PASSWORD" } ) {
			com.google.common.truth.Truth.assertWithMessage( n ).that( Secrets.isSecretName( n ) ).isTrue();
		}
		for ( String n : new String[] { "shipping", "monkey", "JAVA_HOME", "user.dir", "username", "email", "mapping", "spinner", "keyboard", "PATH", "" } ) {
			com.google.common.truth.Truth.assertWithMessage( n ).that( Secrets.isSecretName( n ) ).isFalse();
		}
		assertThat( Secrets.isSecretName( null ) ).isFalse();
	}

	@Test
	@DisplayName( "-Dname=value and --flag=value pairs are masked inside any value" )
	void pairsInValues() {
		assertThat( Secrets.text( "-Xmx2g -Ddb.password=hunter2 -Dapp.name=shop" ) ).isEqualTo( "-Xmx2g -Ddb.password=[hidden] -Dapp.name=shop" );
		assertThat( Secrets.text( "-Djavax.net.ssl.trustStorePassword=changeit" ) ).isEqualTo( "-Djavax.net.ssl.trustStorePassword=[hidden]" );
		assertThat( Secrets.text( "main.Class --api-key=abc123 --port=8080" ) ).isEqualTo( "main.Class --api-key=[hidden] --port=8080" );
		assertThat( Secrets.text( "--token=\"two words\" --x=1" ) ).isEqualTo( "--token=[hidden] --x=1" );
		assertThat( Secrets.show( "JAVA_TOOL_OPTIONS", "-Dfoo=1 -Dsecret.token=zzz" ) ).isEqualTo( "-Dfoo=1 -Dsecret.token=[hidden]" );
		assertThat( Secrets.show( "sun.java.command", "app.jar --jwt=eyJ.x.y" ) ).isEqualTo( "app.jar --jwt=[hidden]" );
		assertThat( Secrets.text( "a-Dpassword=x" ) ).isEqualTo( "a-Dpassword=x" );
	}

	@Test
	@DisplayName( "secret URL parameters use the shared matcher and plain text is untouched" )
	void urlParams() {
		assertThat( Secrets.text( "https://api.example.com/v1?access_token=zzz&page=2" ) )
		    .isEqualTo( "https://api.example.com/v1?access_token=[hidden]&page=2" );
		assertThat( Secrets.text( "https://u:p@api.example.com/x?sig=1" ) ).isEqualTo( "https://[hidden]@api.example.com/x?sig=1" );
		assertThat( Secrets.text( "nothing to see" ) ).isEqualTo( "nothing to see" );
		assertThat( Secrets.url( "jdbc:mysql://bob:pw@db/x?password=a" ) ).isEqualTo( "jdbc:mysql://db/x?password=[hidden]" );
	}

	@Test
	@DisplayName( "redactQuery hides secret parameters and cuts long queries" )
	void query() {
		assertThat( Secrets.redactQuery( "a=1&PIN=1234&b", ortus.boxlang.modules.bxlens.LensConfig.defaults(), 100 ) ).isEqualTo( "a=1&PIN=[redacted]&b" );
		assertThat( Secrets.redactQuery( "a=" + "x".repeat( 50 ), null, 10 ) ).hasLength( 13 );
		assertThat( Secrets.redactQuery( "", null, 10 ) ).isEmpty();
	}

}
