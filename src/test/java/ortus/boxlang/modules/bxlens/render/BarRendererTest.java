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

}
