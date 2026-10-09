/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;

/**
 * Runs the packaged module with a fake "bx-orm is installed" answer, to prove that the ORM listeners follow the setting and the module without a
 * restart. The module has its own class loader, so everything is reached by reflection.
 */
public class IntegrationsReconcileTest extends BaseIntegrationTest {

	private Object moduleService() throws Exception {
		Class<?> type = moduleRecord.getModuleClassLoader().toClassLoader().loadClass( "ortus.boxlang.modules.bxlens.LensService" );
		return type.getMethod( "getInstance" ).invoke( null );
	}

	private List<String> collectorIds( Object svc ) throws Exception {
		List<String> ids = new ArrayList<>();
		for ( Object c : ( List<?> ) svc.getClass().getMethod( "getCollectors" ).invoke( svc ) ) {
			ids.add( ( String ) c.getClass().getMethod( "id" ).invoke( c ) );
		}
		return ids;
	}

	private void fakeInstalled( Object svc, Predicate<String> installed ) throws Exception {
		Class<?>	type			= svc.getClass().getClassLoader().loadClass( "ortus.boxlang.modules.bxlens.Integrations" );
		Object		integrations	= type.getConstructor( Predicate.class ).newInstance( installed );
		svc.getClass().getMethod( "setIntegrations", type ).invoke( svc, integrations );
	}

	@SuppressWarnings( "unchecked" )
	@Test
	@DisplayName( "the ORM listeners are registered when the setting is on and the module appears, and removed when it goes, with no restart" )
	void reconcile( @TempDir Path dir ) throws Exception {
		Object				svc			= moduleService();
		AtomicBoolean		installed	= new AtomicBoolean( false );
		Map<String, Object>	original	= LensConfig.overlay( moduleRecord.settings, null );
		Map<String, Object>	settings	= LensConfig.overlay( original, null );
		settings.put( "collectors", new LinkedHashMap<>( Map.of( "orm", Map.of( "enabled", true ) ) ) );
		settings.put( "console", new LinkedHashMap<>( Map.of( "overridesFile", dir.resolve( "overrides.json" ).toString() ) ) );
		try {
			svc.getClass().getMethod( "activate", ortus.boxlang.runtime.BoxRuntime.class, Map.class, String.class, String.class ).invoke( svc, runtime,
			    settings,
			    moduleRecord.physicalPath.toString(), "test" );
			fakeInstalled( svc, m -> installed.get() );
			svc.getClass().getMethod( "reconcileIntegrations" ).invoke( svc );
			// On in the settings, but bx-orm is not there: nothing listens
			assertThat( collectorIds( svc ) ).doesNotContain( "orm" );
			assertThat( runtime.getInterceptorService().hasState( Key.of( "onORMQuery" ) )
			    && runtime.getInterceptorService().getState( Key.of( "onORMQuery" ) ).size() > 0 )
			    .isFalse();

			// The module arrives: the next module event registers the listeners
			installed.set( true );
			svc.getClass().getMethod( "reconcileIntegrations" ).invoke( svc );
			assertThat( collectorIds( svc ) ).contains( "orm" );
			assertThat( runtime.getInterceptorService().getState( Key.of( "onORMQuery" ) ).size() ).isEqualTo( 1 );
			assertThat( runtime.getInterceptorService().getState( Key.of( "onORMFlush" ) ).size() ).isEqualTo( 1 );
			assertThat( runtime.getInterceptorService().getState( Key.of( "onORMException" ) ).size() ).isEqualTo( 1 );

			// The setting changes live: off removes them, on puts them back
			svc.getClass().getMethod( "setIntegration", String.class, boolean.class ).invoke( svc, "orm", false );
			assertThat( collectorIds( svc ) ).doesNotContain( "orm" );
			assertThat( runtime.getInterceptorService().getState( Key.of( "onORMQuery" ) ).size() ).isEqualTo( 0 );
			svc.getClass().getMethod( "setIntegration", String.class, boolean.class ).invoke( svc, "orm", true );
			assertThat( collectorIds( svc ) ).contains( "orm" );

			// The module goes away
			installed.set( false );
			svc.getClass().getMethod( "reconcileIntegrations" ).invoke( svc );
			assertThat( collectorIds( svc ) ).doesNotContain( "orm" );
			assertThat( runtime.getInterceptorService().getState( Key.of( "onORMQuery" ) ).size() ).isEqualTo( 0 );
		} finally {
			// Back to the module as it was loaded
			svc.getClass().getMethod( "activate", ortus.boxlang.runtime.BoxRuntime.class, Map.class, String.class, String.class ).invoke( svc, runtime,
			    original,
			    moduleRecord.physicalPath.toString(), moduleRecord.version );
		}
	}

	@Test
	@DisplayName( "switching an integration on needs its module, and an unknown id is refused" )
	void setIntegrationRules( @TempDir Path dir ) throws Exception {
		Object				svc			= moduleService();
		Map<String, Object>	original	= LensConfig.overlay( moduleRecord.settings, null );
		Map<String, Object>	settings	= LensConfig.overlay( original, null );
		settings.put( "console", new LinkedHashMap<>( Map.of( "overridesFile", dir.resolve( "overrides.json" ).toString() ) ) );
		try {
			svc.getClass().getMethod( "activate", ortus.boxlang.runtime.BoxRuntime.class, Map.class, String.class, String.class ).invoke( svc, runtime,
			    settings,
			    moduleRecord.physicalPath.toString(), "test" );
			fakeInstalled( svc, m -> false );
			var	method	= svc.getClass().getMethod( "setIntegration", String.class, boolean.class );
			var	e1		= org.junit.jupiter.api.Assertions.assertThrows( java.lang.reflect.InvocationTargetException.class,
			    () -> method.invoke( svc, "orm", true ) );
			assertThat( e1.getCause() ).isInstanceOf( IllegalStateException.class );
			assertThat( e1.getCause().getMessage() ).contains( "Install bx-orm" );
			var e2 = org.junit.jupiter.api.Assertions.assertThrows( java.lang.reflect.InvocationTargetException.class,
			    () -> method.invoke( svc, "nope", true ) );
			assertThat( e2.getCause() ).isInstanceOf( IllegalArgumentException.class );
			// Switching off is always allowed
			method.invoke( svc, "orm", false );
			assertThat( collectorIds( svc ) ).doesNotContain( "orm" );
		} finally {
			svc.getClass().getMethod( "activate", ortus.boxlang.runtime.BoxRuntime.class, Map.class, String.class, String.class ).invoke( svc, runtime,
			    original,
			    moduleRecord.physicalPath.toString(), moduleRecord.version );
		}
	}

	@Test
	@DisplayName( "the module events that reconcile are announced by core, and the lifecycle collector listens to them" )
	void moduleEvents() {
		assertThat( runtime.getInterceptorService().getInterceptionPoints() ).containsAtLeast( Key.of( "postModuleLoad" ), Key.of( "postModuleUnload" ) );
		assertThat( runtime.getInterceptorService().getState( Key.of( "postModuleLoad" ) ).size() ).isAtLeast( 1 );
		assertThat( runtime.getInterceptorService().getState( Key.of( "postModuleUnload" ) ).size() ).isAtLeast( 1 );
		// An announcement with no module behind it does no harm
		runtime.getInterceptorService().announce( Key.of( "postModuleLoad" ), Struct.of( "moduleName", "nothing" ) );
	}

}
