/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
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

	@Test
	@DisplayName( "a big file is read from its end, and only as much as needed" )
	void bigFile( @TempDir Path dir ) throws Exception {
		StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < 200_000; i++ ) {
			sb.append( "[2026-10-08] [t] [" ).append( i % 50 == 0 ? "ERROR" : "INFO " ).append( "] [APP] line " ).append( i ).append( '\n' );
		}
		Files.writeString( dir.resolve( "big.log" ), sb.toString() );
		LogData				logs	= new LogData( dir );
		Map<String, Object>	r		= logs.read( "big.log", 3, "", "" );
		assertThat( ( List<?> ) r.get( "lines" ) ).hasSize( 3 );
		assertThat( ( ( List<?> ) r.get( "lines" ) ).get( 2 ).toString() ).endsWith( "line 199999" );
		assertThat( r.get( "cut" ) ).isEqualTo( true );
		Map<String, Object> hit = logs.read( "big.log", 5, "line 150000", "" );
		assertThat( ( List<?> ) hit.get( "lines" ) ).hasSize( 1 );
		Map<String, Object> errs = logs.read( "big.log", 2, "", "ERROR" );
		assertThat( ( ( List<?> ) errs.get( "lines" ) ).get( 1 ).toString() ).endsWith( "line 199950" );
		// Something older than the scan window is not found, and the answer says the file was cut
		Map<String, Object> old = logs.read( "big.log", 5, "line 1 ", "" );
		assertThat( ( List<?> ) old.get( "lines" ) ).isEmpty();
		assertThat( old.get( "cut" ) ).isEqualTo( true );
	}

	@Test
	@DisplayName( "only two reads run at once, the next one is refused" )
	void busy( @TempDir Path dir ) throws Exception {
		Files.writeString( dir.resolve( "app.log" ), LOG );
		LogData logs = new LogData( dir );
		assertThat( logs.reads().tryAcquire( LogData.MAX_READS ) ).isTrue();
		org.junit.jupiter.api.Assertions.assertThrows( LogData.Busy.class, () -> logs.read( "app.log", 10, "", "" ) );
		logs.reads().release( LogData.MAX_READS );
		assertThat( ( List<?> ) logs.read( "app.log", 10, "", "" ).get( "lines" ) ).hasSize( 5 );
		assertThat( logs.reads().availablePermits() ).isEqualTo( LogData.MAX_READS );
	}

	@Test
	@DisplayName( "level markers are found without a regular expression, and an empty file reads as empty" )
	void levels( @TempDir Path dir ) throws Exception {
		assertThat( LogData.levelOf( "x [ WARN ] y" ) ).isEqualTo( 3 );
		assertThat( LogData.levelOf( "[INFO] a [ERROR] b" ) ).isEqualTo( 2 );
		assertThat( LogData.levelOf( "[WARNING] none" ) ).isEqualTo( -1 );
		assertThat( LogData.levelOf( "no brackets" ) ).isEqualTo( -1 );
		Files.writeString( dir.resolve( "e.log" ), "" );
		assertThat( ( List<?> ) new LogData( dir ).read( "e.log", 10, "", "" ).get( "lines" ) ).isEmpty();
	}

}
