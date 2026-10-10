/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LensConfigTest {

	@Test
	@DisplayName( "Lens is off by default" )
	public void disabledByDefault() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.barEnabled ).isFalse();
		assertThat( cfg.consoleEnabled ).isFalse();
		assertThat( cfg.active ).isFalse();
		assertThat( cfg.collectLevel ).isEqualTo( "light" );
		assertThat( cfg.light ).isTrue();
		assertThat( cfg.trackNonHtml ).isFalse();
		assertThat( cfg.queriesIncludeParams ).isFalse();
		assertThat( cfg.isCollectorEnabled( "orm", false ) ).isFalse();
		assertThat( cfg.maxRequests ).isEqualTo( 50 );
		assertThat( cfg.slowQueryMs ).isEqualTo( 25 );
		assertThat( cfg.nPlusOneMin ).isEqualTo( 3 );
	}

	@Test
	@DisplayName( "Nested keys are read case insensitively with defaults for the missing ones" )
	public void nestedLookups() {
		LensConfig cfg = new LensConfig( Map.of(
		    "bar", Map.of( "enabled", true ),
		    "history", Map.of( "maxRequests", 5 ),
		    "collectors", Map.of( "Queries", Map.of( "enabled", false, "max", 7 ), "cache", true )
		) );
		assertThat( cfg.barEnabled ).isTrue();
		assertThat( cfg.active ).isTrue();
		assertThat( cfg.maxRequests ).isEqualTo( 5 );
		assertThat( cfg.trackNonHtml ).isFalse();
		assertThat( cfg.isCollectorEnabled( "queries", true ) ).isFalse();
		assertThat( cfg.collectorInt( "queries", "max", 200 ) ).isEqualTo( 7 );
		assertThat( cfg.isCollectorEnabled( "cache", false ) ).isTrue();
		assertThat( cfg.isCollectorEnabled( "functions", false ) ).isFalse();
		assertThat( cfg.isCollectorEnabled( "jvm", true ) ).isTrue();
	}

	@Test
	@DisplayName( "The console and the bar switch collection on independently" )
	public void surfaces() {
		assertThat( new LensConfig( Map.of( "console", Map.of( "enabled", true ) ) ).active ).isTrue();
		assertThat( new LensConfig( Map.of( "bar", Map.of( "enabled", true ), "collect", Map.of( "level", "off" ) ) ).active ).isFalse();
	}

	@Test
	@DisplayName( "Collect level accepts off, light and full and ignores anything else" )
	public void collectLevels() {
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "LIGHT" ) ) ).light ).isTrue();
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "off" ) ) ).collectLevel ).isEqualTo( "off" );
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "turbo" ) ) ).collectLevel ).isEqualTo( "light" );
	}

	@Test
	@DisplayName( "Non HTML requests are tracked in full by default and in light only when asked" )
	public void trackNonHtml() {
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "full" ) ) ).trackNonHtml ).isTrue();
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "light" ), "history", Map.of( "trackNonHtml", true ) ) ).trackNonHtml ).isTrue();
		assertThat( new LensConfig( Map.of( "collect", Map.of( "level", "full" ), "history", Map.of( "trackNonHtml", false ) ) ).trackNonHtml ).isFalse();
		assertThat( new LensConfig( Map.of( "history", Map.of( "trackNonHtml", "" ) ) ).trackNonHtml ).isFalse();
	}

	@Test
	@DisplayName( "Parameter values and the ORM collector are off unless switched on" )
	public void privacyDefaults() {
		assertThat( new LensConfig(
		    Map.of( "collectors", Map.of( "queries", Map.of( "includeParams", true ), "orm", Map.of( "enabled", true ) ) ) ).queriesIncludeParams ).isTrue();
		assertThat( new LensConfig( Map.of( "collectors", Map.of( "orm", Map.of( "enabled", true ) ) ) ).isCollectorEnabled( "orm", false ) ).isTrue();
		assertThat( new SettingsRegistry( java.util.List.of( "orm", "queries" ) ).get( "collectors.orm.enabled" ).def() ).isEqualTo( false );
		assertThat( new SettingsRegistry( java.util.List.of() ).get( "collectors.queries.includeParams" ).def() ).isEqualTo( false );
		assertThat( new SettingsRegistry( java.util.List.of() ).get( "collect.level" ).def() ).isEqualTo( "light" );
	}

	@Test
	@DisplayName( "The module descriptor declares the same defaults" )
	public void moduleConfigDefaults() throws Exception {
		String bx = java.nio.file.Files.readString( java.nio.file.Path.of( "src/main/bx/ModuleConfig.bx" ) );
		assertThat( bx ).contains( "collect : { level : \"light\" }" );
		assertThat( bx ).contains( "includeParams : false" );
		assertThat( bx ).contains( "orm          : { enabled : false }" );
		// Statistics are bx-orm's own setting (generateStatistics), Lens no longer forces them
		assertThat( bx ).doesNotContain( "statistics : true" );
		assertThat( bx ).contains( "headerAlways : true" );
	}

	@Test
	@DisplayName( "Tabs can be hidden from settings" )
	public void hiddenTabs() {
		assertThat( new LensConfig( Map.of( "tabs", Map.of( "hide", List.of( "Modules", "cache" ) ) ) ).hiddenTabs ).containsExactly( "modules", "cache" );
		assertThat( LensConfig.defaults().hiddenTabs ).isEmpty();
	}

	@Test
	@DisplayName( "The console path is never tracked, whatever excludePaths says" )
	public void consoleNeverTracked() {
		LensConfig cfg = new LensConfig( Map.of( "excludePaths", List.of() ) );
		assertThat( cfg.isExcluded( "/~bxlens/index.bxm" ) ).isTrue();
		assertThat( cfg.isExcluded( "/~bxlens/index.bxm/api/state" ) ).isTrue();
		assertThat( cfg.isExcluded( "/~bxlensfoo" ) ).isFalse();
	}

	@Test
	@DisplayName( "A plain console password is returned and an empty one is empty" )
	public void consolePassword() {
		assertThat( new LensConfig( Map.of( "console", Map.of( "password", "s3cret" ) ) ).consolePassword() ).isEqualTo( "s3cret" );
		assertThat( LensConfig.defaults().consolePassword() ).isEmpty();
	}

	@Test
	@DisplayName( "Excluded paths support a trailing wildcard" )
	public void excludedPaths() {
		LensConfig cfg = new LensConfig( Map.of( "excludePaths", List.of( "/~bxlens/*", "/health" ) ) );
		assertThat( cfg.isExcluded( "/~bxlens/lens.js" ) ).isTrue();
		assertThat( cfg.isExcluded( "/health" ) ).isTrue();
		assertThat( cfg.isExcluded( "/healthz" ) ).isFalse();
		assertThat( cfg.isExcluded( "/index.bxm" ) ).isFalse();
	}

	@Test
	@DisplayName( "Only configured content types are injectable" )
	public void injectableContentTypes() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.isInjectable( "text/html;charset=UTF-8" ) ).isTrue();
		assertThat( cfg.isInjectable( "TEXT/HTML" ) ).isTrue();
		assertThat( cfg.isInjectable( "application/json" ) ).isFalse();
		assertThat( cfg.isInjectable( "text/event-stream" ) ).isFalse();
		assertThat( cfg.isInjectable( null ) ).isFalse();
	}

	@Test
	@DisplayName( "Redaction matches keys by substring, case insensitive" )
	public void redaction() {
		LensConfig cfg = LensConfig.defaults();
		assertThat( cfg.shouldRedact( "password" ) ).isTrue();
		assertThat( cfg.shouldRedact( "userPassword" ) ).isTrue();
		assertThat( cfg.shouldRedact( "X-Api-Token" ) ).isTrue();
		assertThat( cfg.shouldRedact( "Authorization" ) ).isTrue();
		assertThat( cfg.shouldRedact( "username" ) ).isFalse();
	}

	@Test
	@DisplayName( "The editor link pattern needs an allowed scheme" )
	public void editorSchemes() {
		for ( String ok : new String[] { "vscode://file/{path}:{line}", "vscode-insiders://file/{path}", "idea://open?file={path}&line={line}",
		    "phpstorm://open?file={path}",
		    "subl://open?url=file://{path}", "file:///{path}", "http://localhost/open?f={path}", "https://x/{path}", "cursor://file/{path}",
		    "zed://file/{path}",
		    "VSCODE://file/{path}" } ) {
			com.google.common.truth.Truth.assertWithMessage( ok ).that( LensConfig.isSafeEditorLink( ok ) ).isTrue();
		}
		for ( String bad : new String[] { "javascript:alert(1)", "data:text/html,x", "vbscript:x", "//evil.com/{path}", "{path}", "", " ", "ftp://x",
		    "vscode\n://x" } ) {
			com.google.common.truth.Truth.assertWithMessage( bad ).that( LensConfig.isSafeEditorLink( bad ) ).isFalse();
		}
		assertThat( new LensConfig( Map.of( "editor", Map.of( "linkPattern", "javascript:alert(1)" ) ) ).editorLink() )
		    .isEqualTo( LensConfig.DEFAULT_EDITOR_LINK );
		assertThat( new LensConfig( Map.of( "editor", Map.of( "linkPattern", "idea://open?file={path}" ) ) ).editorLink() )
		    .isEqualTo( "idea://open?file={path}" );
		assertThat( LensConfig.defaults().editorLink() ).isEqualTo( LensConfig.DEFAULT_EDITOR_LINK );
		org.junit.jupiter.api.Assertions.assertThrows( IllegalArgumentException.class,
		    () -> new SettingsRegistry( java.util.List.of() ).coerce( "editor.linkPattern", "javascript:alert(1)" ) );
		assertThat( new SettingsRegistry( java.util.List.of() ).coerce( "editor.linkPattern", "zed://file/{path}" ) ).isEqualTo( "zed://file/{path}" );
	}

}
