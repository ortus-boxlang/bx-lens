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
