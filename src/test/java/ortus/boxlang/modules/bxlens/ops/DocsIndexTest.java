/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.modules.bxlens.ops.DocsIndex.Chunk;

public class DocsIndexTest {

	private static DocsIndex index() {
		DocsIndex i = new DocsIndex();
		i.set( List.of(
		    new Chunk( "console/executors.md", "Health",
		        "An executor is healthy when its pool and queue use stay under the thresholds. Raise the pool size when every thread is busy." ),
		    new Chunk( "configuration.md", "server",
		        "Set server.name to give this server a name. server.address sets the primary address and server.id the instance id." ),
		    new Chunk( "security.md", "Audit log", "Every login, failed login and change is written to the audit log." ),
		    new Chunk( "console/logs.md", "Search", "Search the log files by text and level." ) ), "sum1", "sig1" );
		return i;
	}

	@Test
	@DisplayName( "words are lower cased, common words and one letter words dropped, plurals folded" )
	void tokens() {
		assertThat( DocsIndex.tokens( "How do I raise the Executors pool sizes?" ) ).containsExactly( "raise", "executor", "pool", "size" ).inOrder();
		assertThat( DocsIndex.tokens( "" ) ).isEmpty();
		assertThat( DocsIndex.tokens( null ) ).isEmpty();
		assertThat( DocsIndex.tokens( "queries" ) ).containsExactly( "query" );
	}

	@Test
	@DisplayName( "the keyword search ranks the chunk that answers the question first, and each result names its page" )
	void keywordSearch() {
		List<Map<String, Object>> r = index().keywordSearch( "how do I set the server name", 3 );
		assertThat( r ).isNotEmpty();
		assertThat( r.get( 0 ).get( "page" ) ).isEqualTo( "configuration.md" );
		assertThat( r.get( 0 ).get( "heading" ) ).isEqualTo( "server" );
		assertThat( ( String ) r.get( 0 ).get( "text" ) ).contains( "server.name" );
		assertThat( index().keywordSearch( "executors pool is busy", 1 ).get( 0 ).get( "page" ) ).isEqualTo( "console/executors.md" );
		assertThat( index().keywordSearch( "audit", 5 ).get( 0 ).get( "page" ) ).isEqualTo( "security.md" );
	}

	@Test
	@DisplayName( "no match, an empty question and an empty index give no results" )
	void noMatch() {
		assertThat( index().keywordSearch( "zebra", 3 ) ).isEmpty();
		assertThat( index().keywordSearch( "the and of", 3 ) ).isEmpty();
		assertThat( new DocsIndex().keywordSearch( "executor", 3 ) ).isEmpty();
	}

	@Test
	@DisplayName( "a result is cut to the chunk limit" )
	void cap() {
		String		big	= "executor ".repeat( 500 );
		DocsIndex	i	= new DocsIndex();
		i.set( List.of( new Chunk( "a.md", "h", big ) ), "s", "g" );
		String text = ( String ) i.keywordSearch( "executor", 1 ).get( 0 ).get( "text" );
		assertThat( text.length() ).isAtMost( DocsIndex.MAX_CHUNK + 3 );
	}

	@Test
	@DisplayName( "the index is current only for the same documents and the same embedding settings" )
	void current() {
		DocsIndex i = index();
		assertThat( i.current( "sum1", "sig1" ) ).isTrue();
		assertThat( i.current( "sum2", "sig1" ) ).isFalse();
		assertThat( i.current( "sum1", "other" ) ).isFalse();
		assertThat( i.mode() ).isEqualTo( "keywords" );
		i.mode( "embeddings", "" );
		assertThat( i.mode() ).isEqualTo( "embeddings" );
		assertThat( new DocsIndex().current( "", "" ) ).isFalse();
	}

	@Test
	@DisplayName( "the checksum changes when a page is edited, added or removed, and ignores other files" )
	void checksum( @TempDir Path dir ) throws Exception {
		Files.writeString( dir.resolve( "a.md" ), "one" );
		String first = DocsIndex.checksum( dir );
		assertThat( first ).hasLength( 64 );
		assertThat( DocsIndex.checksum( dir ) ).isEqualTo( first );
		Files.writeString( dir.resolve( "a.md" ), "two" );
		String edited = DocsIndex.checksum( dir );
		assertThat( edited ).isNotEqualTo( first );
		Files.createDirectories( dir.resolve( "console" ) );
		Files.writeString( dir.resolve( "console/b.md" ), "x" );
		String added = DocsIndex.checksum( dir );
		assertThat( added ).isNotEqualTo( edited );
		Files.writeString( dir.resolve( "notes.txt" ), "ignored" );
		assertThat( DocsIndex.checksum( dir ) ).isEqualTo( added );
		assertThat( DocsIndex.pages( dir ) ).containsExactly( "a.md", "console/b.md" ).inOrder();
	}

}
