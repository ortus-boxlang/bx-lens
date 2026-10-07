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
