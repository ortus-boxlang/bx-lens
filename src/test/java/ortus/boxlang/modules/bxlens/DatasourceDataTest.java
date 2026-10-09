/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DatasourceDataTest {

	@Test
	@DisplayName( "credentials and secret parameters are removed from a JDBC URL" )
	void masksUrl() {
		assertThat( DatasourceData.maskUrl( "jdbc:mysql://bob:hunter2@db:3306/app?useSSL=true&password=hunter2" ) )
		    .isEqualTo( "jdbc:mysql://db:3306/app?useSSL=true&password=[hidden]" );
		assertThat( DatasourceData.maskUrl( "jdbc:sqlserver://db;databaseName=x;password=abc;encrypt=true" ) )
		    .isEqualTo( "jdbc:sqlserver://db;databaseName=x;password=[hidden];encrypt=true" );
		assertThat( DatasourceData.maskUrl( "jdbc:derby:memory:lens" ) ).isEqualTo( "jdbc:derby:memory:lens" );
		assertThat( DatasourceData.maskUrl( null ) ).isEmpty();
	}

}
