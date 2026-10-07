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

import java.util.List;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Loads the packaged module into a runtime. There is no web request here, so Lens must stay out of the way.
 */
public class IntegrationTest extends BaseIntegrationTest {

	@Test
	@DisplayName( "The module loads and activates" )
	public void loads() {
		assertThat( moduleService.getRegistry().containsKey( moduleName ) ).isTrue();
		assertThat( moduleRecord.activated ).isTrue();
	}

	/**
	 * The module runs in its own class loader, so its LensService is a different class from the one on the test class path. Reach it reflectively.
	 */
	private Object moduleService() throws Exception {
		Class<?> type = moduleRecord.getModuleClassLoader().toClassLoader().loadClass( "ortus.boxlang.modules.bxlens.LensService" );
		return type.getMethod( "getInstance" ).invoke( null );
	}

	@Test
	@DisplayName( "The built in collectors are registered, expensive ones are not" )
	public void collectors() throws Exception {
		Object			svc	= moduleService();
		List<?>			all	= ( List<?> ) svc.getClass().getMethod( "getCollectors" ).invoke( svc );
		List<String>	ids	= new java.util.ArrayList<>();
		for ( Object c : all ) {
			ids.add( ( String ) c.getClass().getMethod( "id" ).invoke( c ) );
		}
		assertThat( ids ).containsAtLeast( "request", "templates", "queries", "http", "exceptions", "scopes", "jvm", "cache", "modules" );
		assertThat( ids ).doesNotContain( "functions" );
		assertThat( ids ).doesNotContain( "logs" );
	}

	@Test
	@DisplayName( "The module is disabled unless settings turn it on" )
	public void offByDefault() throws Exception {
		Object svc = moduleService();
		assertThat( svc.getClass().getMethod( "isEnabled" ).invoke( svc ) ).isEqualTo( false );
	}

	@Test
	@DisplayName( "Custom interception points are registered for extension modules" )
	public void interceptionPoints() {
		var points = runtime.getInterceptorService().getInterceptionPoints();
		assertThat( points ).contains( Key.of( "onLensRegister" ) );
		assertThat( points ).contains( Key.of( "onLensCollect" ) );
	}

	@Test
	@DisplayName( "The BIFs are registered and do nothing outside a tracked request" )
	public void bifsAreSafeWhenUntracked() {
		runtime.executeSource(
		    """
		    lensMessage( "hello", "info" );
		    lensDump( { a : 1 }, "x" );
		    id = lensStart( "t" );
		    lensStop( id );
		    lensAddMeasure( "m", 1, 2 );
		    lensException( "not an exception" );
		    lensDisable();
		    lensEnable();
		    enabled = lensIsEnabled();
		    measured = lensMeasure( "work", () => 21 * 2 );
		    panel = lensPanel( "p", "P" ).kv( { a : 1 } );
		    rendered = lensRender();
		    """,
		    context );
		assertThat( variables.get( Key.of( "enabled" ) ) ).isEqualTo( false );
		assertThat( ( ( Number ) variables.get( Key.of( "measured" ) ) ).intValue() ).isEqualTo( 42 );
		assertThat( variables.get( Key.of( "panel" ) ) ).isNotNull();
		assertThat( variables.get( Key.of( "rendered" ) ) ).isEqualTo( "" );
	}

}
