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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LogDataTest {

	private static final String LOG = String.join( "\n",
	    "[2026-10-08] [t1] [INFO ] [APP] started",
	    "[2026-10-08] [t1] [ERROR] [APP] boom",
	    "\tat com.example.Thing.run(Thing.java:10)",
	    "[2026-10-08] [t2] [DEBUG] [APP] details about boom",
	    "[2026-10-08] [t2] [WARN ] [APP] careful" ) + "\n";

	@Test
	@DisplayName( "lists files, tails, searches and filters by level with stack lines following their level" )
	void reads( @TempDir Path dir ) throws Exception {
		Files.writeString( dir.resolve( "app.log" ), LOG );
		Files.createDirectories( dir.resolve( "sub" ) );
		Files.writeString( dir.resolve( "sub" ).resolve( "other.log" ), "x\n" );
		LogData logs = new LogData( dir );
		assertThat( ( ( List<?> ) logs.list().get( "files" ) ).size() ).isEqualTo( 2 );
		assertThat( ( List<?> ) logs.read( "app.log", 100, "", "" ).get( "lines" ) ).hasSize( 5 );
		assertThat( ( List<?> ) logs.read( "app.log", 2, "", "" ).get( "lines" ) ).hasSize( 2 );
		assertThat( ( List<?> ) logs.read( "app.log", 100, "BOOM", "" ).get( "lines" ) ).hasSize( 2 );
		List<?> errors = ( List<?> ) logs.read( "app.log", 100, "", "ERROR" ).get( "lines" );
		assertThat( errors ).hasSize( 2 );
		assertThat( errors.get( 1 ).toString() ).contains( "Thing.java" );
		assertThat( ( List<?> ) logs.read( "app.log", 100, "", "WARN" ).get( "lines" ) ).hasSize( 3 );
	}

	@Test
	@DisplayName( "nothing outside the logs directory can be reached" )
	void confined( @TempDir Path dir ) throws Exception {
		Path logsDir = Files.createDirectories( dir.resolve( "logs" ) );
		Files.writeString( dir.resolve( "secret.txt" ), "secret" );
		Files.writeString( logsDir.resolve( "app.log" ), LOG );
		Files.createSymbolicLink( logsDir.resolve( "link.log" ), dir.resolve( "secret.txt" ) );
		LogData logs = new LogData( logsDir );
		assertThat( logs.resolve( "../secret.txt" ) ).isNull();
		assertThat( logs.resolve( dir.resolve( "secret.txt" ).toString() ) ).isNull();
		assertThat( logs.resolve( "link.log" ) ).isNull();
		assertThat( logs.resolve( "app.log" ) ).isNotNull();
		assertThat( logs.read( "../secret.txt", 10, "", "" ) ).isNull();
	}

	@Test
	@DisplayName( "since returns only new lines and starts over after rotation" )
	void since( @TempDir Path dir ) throws Exception {
		Path f = dir.resolve( "app.log" );
		Files.writeString( f, "a\nb\n" );
		LogData	logs	= new LogData( dir );
		long	off		= ( Long ) logs.since( "app.log", 0 ).get( "offset" );
		Files.writeString( f, "a\nb\nc\n" );
		Map<String, Object> m = logs.since( "app.log", off );
		assertThat( ( List<?> ) m.get( "lines" ) ).containsExactly( "c" );
		Files.writeString( f, "z\n" );
		assertThat( logs.since( "app.log", 100 ).get( "rotated" ) ).isEqualTo( true );
	}

}
