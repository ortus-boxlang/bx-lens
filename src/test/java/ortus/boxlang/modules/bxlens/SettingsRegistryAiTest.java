/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SettingsRegistryAiTest {

	private final SettingsRegistry reg = new SettingsRegistry( List.of() );

	private void refused( String key, Object value, String message ) {
		try {
			this.reg.coerce( key, value );
			throw new AssertionError( "expected a refusal for " + key + "=" + value );
		} catch ( IllegalArgumentException e ) {
			assertThat( e.getMessage() ).contains( message );
		}
	}

	@Test
	@DisplayName( "the AI settings are live, except the key and the links switch" )
	void live() {
		for ( String k : List.of( "ai.enabled", "ai.provider", "ai.model", "ai.baseUrl", "ai.embeddingModel", "ai.temperature", "ai.timeoutSeconds",
		    "ai.maxToolCalls",
		    "ai.memoryMessages", "ai.maxConcurrentChats", "ai.actions", "ai.rag", "ai.apiKeyEnv" ) ) {
			assertThat( this.reg.get( k ).live() ).isTrue();
		}
		assertThat( this.reg.get( "ai.apiKey" ).live() ).isFalse();
		assertThat( this.reg.get( "ai.apiKey" ).type() ).isEqualTo( "secret" );
		assertThat( this.reg.get( "ai.links" ).live() ).isFalse();
		refused( "ai.apiKey", "sk-123", "boxlang.json" );
	}

	@Test
	@DisplayName( "the provider must be one bx-ai can chat with" )
	void provider() {
		assertThat( this.reg.coerce( "ai.provider", "OpenAI" ) ).isEqualTo( "openai" );
		assertThat( this.reg.coerce( "ai.provider", "claude" ) ).isEqualTo( "claude" );
		assertThat( SettingsRegistry.AI_PROVIDERS ).containsAtLeast( "ollama", "openai", "claude", "gemini" );
		refused( "ai.provider", "skynet", "one of" );
	}

	@Test
	@DisplayName( "the model server address is http or https, has a host and carries no credentials" )
	void baseUrl() {
		assertThat( this.reg.coerce( "ai.baseUrl", "http://localhost:11434" ) ).isEqualTo( "http://localhost:11434" );
		assertThat( this.reg.coerce( "ai.baseUrl", "https://models.internal/v1" ) ).isEqualTo( "https://models.internal/v1" );
		assertThat( this.reg.coerce( "ai.baseUrl", "" ) ).isEqualTo( "" );
		refused( "ai.baseUrl", "ftp://host", "http or https" );
		refused( "ai.baseUrl", "http://user:pw@host/", "without a user name" );
		refused( "ai.baseUrl", "file:///etc/passwd", "http or https" );
		refused( "ai.baseUrl", "not a url", "http or https" );
	}

	@Test
	@DisplayName( "the API key setting takes the name of an environment variable, never a key" )
	void apiKeyEnv() {
		assertThat( this.reg.coerce( "ai.apiKeyEnv", "OPENAI_API_KEY" ) ).isEqualTo( "OPENAI_API_KEY" );
		assertThat( this.reg.coerce( "ai.apiKeyEnv", "" ) ).isEqualTo( "" );
		refused( "ai.apiKeyEnv", "sk-proj-abcdef123456", "NAME of an environment variable" );
		refused( "ai.apiKeyEnv", "has space", "NAME of an environment variable" );
		refused( "ai.apiKeyEnv", "1STARTS_WITH_DIGIT", "NAME of an environment variable" );
		assertThat( SettingsRegistry.isEnvName( "MY_KEY_2" ) ).isTrue();
		assertThat( SettingsRegistry.isEnvName( "a=b" ) ).isFalse();
	}

	@Test
	@DisplayName( "numbers are kept in range" )
	void numbers() {
		assertThat( this.reg.coerce( "ai.maxToolCalls", "5" ) ).isEqualTo( 5 );
		assertThat( this.reg.coerce( "ai.temperature", "0.7" ) ).isEqualTo( "0.7" );
		refused( "ai.maxToolCalls", 0, "between 1 and 20" );
		refused( "ai.maxToolCalls", 99, "between 1 and 20" );
		refused( "ai.timeoutSeconds", 5, "between 10 and 600" );
		refused( "ai.memoryMessages", 1, "between 2 and 100" );
		refused( "ai.maxConcurrentChats", 21, "between 1 and 20" );
		refused( "ai.temperature", "3", "0 to 2" );
		refused( "ai.temperature", "hot", "0 to 2" );
		refused( "ai.enabled", "maybe", "true or false" );
	}

	@Test
	@DisplayName( "model names are plain" )
	void model() {
		assertThat( this.reg.coerce( "ai.model", "llama3.2" ) ).isEqualTo( "llama3.2" );
		assertThat( this.reg.coerce( "ai.model", "gpt-4o-mini" ) ).isEqualTo( "gpt-4o-mini" );
		refused( "ai.model", "two words", "model name" );
		refused( "ai.embeddingModel", "x\ny", "model name" );
	}

}
