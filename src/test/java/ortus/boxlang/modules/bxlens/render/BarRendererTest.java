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

}
