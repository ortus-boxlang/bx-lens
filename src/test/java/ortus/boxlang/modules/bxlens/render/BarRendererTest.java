/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.render;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BarRendererTest {

	@Test
	@DisplayName( "The block goes before the last closing body tag, any case" )
	public void beforeBody() {
		StringBuffer buf = new StringBuffer( "<html><body><p>hi</p></BODY></html>" );
		BarRenderer.insert( buf, "[BAR]" );
		assertThat( buf.toString() ).isEqualTo( "<html><body><p>hi</p>[BAR]</BODY></html>" );
	}

	@Test
	@DisplayName( "Only the last body tag is used, so a body tag inside a string does not win" )
	public void lastBody() {
		StringBuffer buf = new StringBuffer( "<body>x</body> text </body>" );
		BarRenderer.insert( buf, "[BAR]" );
		assertThat( buf.toString() ).isEqualTo( "<body>x</body> text [BAR]</body>" );
	}

	@Test
	@DisplayName( "A page without a body tag gets the block appended" )
	public void noBody() {
		StringBuffer buf = new StringBuffer( "<p>fragment</p>" );
		BarRenderer.insert( buf, "[BAR]" );
		assertThat( buf.toString() ).isEqualTo( "<p>fragment</p>[BAR]" );
	}

	@Test
	@DisplayName( "A closing body tag far from the end is not searched for: the block is appended" )
	public void notInTail() {
		StringBuffer buf = new StringBuffer( "<body>x</body>" + "y".repeat( BarRenderer.TAIL + 10 ) );
		BarRenderer.insert( buf, "[BAR]" );
		assertThat( buf.toString() ).endsWith( "y[BAR]" );
		assertThat( buf.toString() ).startsWith( "<body>x</body>y" );
	}

	@Test
	@DisplayName( "A closing body tag inside the last kilobyte is found in any case" )
	public void inTail() {
		StringBuffer	buf	= new StringBuffer( "z".repeat( 50_000 ) + "</BoDy>\n</html>" );
		int				at	= BarRenderer.insert( buf, "[BAR]" );
		assertThat( at ).isEqualTo( 50_000 );
		assertThat( buf.toString() ).contains( "[BAR]</BoDy>" );
	}

	@Test
	@DisplayName( "Short and empty pages are handled" )
	public void tiny() {
		StringBuffer empty = new StringBuffer();
		BarRenderer.insert( empty, "[BAR]" );
		assertThat( empty.toString() ).isEqualTo( "[BAR]" );
		StringBuffer two = new StringBuffer( "</body>" );
		BarRenderer.insert( two, "[BAR]" );
		assertThat( two.toString() ).isEqualTo( "[BAR]</body>" );
	}

	private static java.nio.file.Path assets( java.nio.file.Path dir ) throws Exception {
		for ( String n : BarRenderer.NAMES ) {
			java.nio.file.Files.writeString( dir.resolve( n ), "content of " + n );
		}
		return dir;
	}

	@Test
	@DisplayName( "The block is small: links to the files by content hash, the data and nothing inlined" )
	public void smallBlock( @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir ) throws Exception {
		BarRenderer	r		= new BarRenderer( assets( dir ), false );
		String		block	= r.render( "{\"data\":{}}" );
		assertThat( block.length() ).isLessThan( 1200 );
		assertThat( block )
		    .contains( "<link rel=\"stylesheet\" id=\"bxlens-css\" href=\"/~bxlens/index.bxm/assets/lens.css?v=" + r.asset( "lens.css" ).hash() + "\">" );
		assertThat( block )
		    .contains( "<script defer id=\"bxlens-js\" src=\"/~bxlens/index.bxm/assets/lens.js?v=" + r.asset( "lens.js" ).hash() + "\"></script>" );
		assertThat( block ).contains( "<script type=\"application/json\" id=\"bxlens-data\">{\"data\":{}}</script>" );
		assertThat( block ).contains( "data-alpine=\"" + r.asset( "alpine.min.js" ).hash() + "\"" );
		assertThat( block ).doesNotContain( "content of" );
		assertThat( block ).doesNotContain( "<style" );
	}

	@Test
	@DisplayName( "The hash follows the content and is computed once, the files are cached for a year only with the current hash" )
	public void hashes( @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir ) throws Exception {
		BarRenderer	r	= new BarRenderer( assets( dir ), false );
		String		h	= r.asset( "lens.js" ).hash();
		assertThat( h ).matches( "[0-9a-f]{12}" );
		assertThat( r.immutable( "lens.js", h ) ).isTrue();
		assertThat( r.immutable( "lens.js", "stale" ) ).isFalse();
		assertThat( r.immutable( "lens.js", null ) ).isFalse();
		java.nio.file.Files.writeString( dir.resolve( "lens.js" ), "changed" );
		assertThat( r.asset( "lens.js" ).hash() ).isEqualTo( h );
		assertThat( new BarRenderer( dir, false ).asset( "lens.js" ).hash() ).isNotEqualTo( h );
		assertThat( r.asset( "lens.js" ).type() ).startsWith( "text/javascript" );
		assertThat( r.asset( "secret.txt" ) ).isNull();
		assertThat( BarRenderer.isBarAsset( "lens.css" ) ).isTrue();
		assertThat( BarRenderer.isBarAsset( "console.js" ) ).isFalse();
		assertThat( BarRenderer.isBarAsset( "../ModuleConfig.bx" ) ).isFalse();
	}

	@Test
	@DisplayName( "In reload mode the hash is computed again and nothing is immutable" )
	public void reloadMode( @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir ) throws Exception {
		BarRenderer	r	= new BarRenderer( assets( dir ), true );
		String		h	= r.asset( "lens.css" ).hash();
		assertThat( r.immutable( "lens.css", h ) ).isFalse();
		java.nio.file.Files.writeString( dir.resolve( "lens.css" ), "edited" );
		assertThat( r.asset( "lens.css" ).hash() ).isNotEqualTo( h );
		assertThat( r.render( "{}" ) ).contains( "lens.css?v=" + r.asset( "lens.css" ).hash() );
	}

	@Test
	@DisplayName( "A missing file makes the set incomplete" )
	public void incomplete( @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir ) throws Exception {
		assertThat( new BarRenderer( assets( dir ), false ).isComplete() ).isTrue();
		java.nio.file.Files.delete( dir.resolve( "bar-icons.svg" ) );
		assertThat( new BarRenderer( dir, false ).isComplete() ).isFalse();
	}

}
