/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.ops;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Java HTTP client must not exist until the first call. The JDK freezes its list of restricted headers when its HTTP classes first load,
 * and the BoxLang runtime sets the allowed ones after the modules are loaded, so a client built when Lens loads breaks <code>bx:http</code>
 * of the host application.
 */
public class McpClientLazyTest {

	@Test
	@DisplayName( "Building the client and the service does not create the Java HTTP client" )
	void notBuiltAtStartup() throws Exception {
		McpClient	client	= new McpClient( null );
		Field		f		= McpClient.class.getDeclaredField( "http" );
		f.setAccessible( true );
		assertThat( f.get( client ) ).isNull();
	}

}
