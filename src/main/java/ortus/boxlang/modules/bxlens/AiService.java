/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Optional help from a language model. Two things, both off the hot path and both opt in:
 * <ul>
 * <li><b>Prompts</b> built from redacted data, to copy or to open in a chat site. They need nothing installed and send nothing from the server.</li>
 * <li><b>Ask</b>: the server sends a prompt to a model through the <code>bx-ai</code> module (<code>aiChat</code>) when <code>ai.enabled</code> is
 * true and the module is installed. The provider, model and API key come from <code>ai.*</code> or from the bx-ai settings. Point it at a local
 * model to keep everything inside the network.</li>
 * </ul>
 */
public final class AiService {

	private static final Key			AI_CHAT	= Key.of( "aiChat" );

	private final LensService			service;
	private final AtomicInteger			running	= new AtomicInteger();
	/** Its own small pool, so a slow model never takes threads from the shared pool of the server. One call runs at a time. */
	private volatile ThreadPoolExecutor	pool	= newPool();
	private volatile long				windowStart;
	private volatile int				windowCount;

	public AiService( LensService service ) {
		this.service = service;
	}

	/**
	 * Is the bx-ai module installed?
	 */
	public boolean installed() {
		try {
			return BoxRuntime.getInstance().getFunctionService().getGlobalFunction( AI_CHAT ) != null;
		} catch ( Throwable t ) {
			return false;
		}
	}

	/**
	 * Can the server call a model? It needs <code>ai.enabled</code>, bx-ai and a BoxLang+ license or trial.
	 */
	public boolean canCall() {
		return service.getConfig().getBool( "ai.enabled", false ) && installed() && service.getLicensing().has( "ai" );
	}

	/** The provider every AI feature uses. Ollama on this machine unless the settings say otherwise. */
	public String provider() {
		String p = service.getConfig().getString( "ai.provider", "ollama" ).trim().toLowerCase( java.util.Locale.ROOT );
		return p.isEmpty() ? "ollama" : p;
	}

	/** The model: the setting, else llama3.2 for Ollama, else empty (the default of bx-ai for that provider). */
	public String model() {
		String m = service.getConfig().getString( "ai.model", "" ).trim();
		return m.isEmpty() && provider().equals( "ollama" ) ? "llama3.2" : m;
	}

	/** The address of the model server: the setting, else the local Ollama address, else empty (the default of the provider). */
	public String baseUrl() {
		String u = service.getConfig().getString( "ai.baseUrl", "" ).trim();
		return u.isEmpty() && provider().equals( "ollama" ) ? "http://localhost:11434" : u;
	}

	/** The model that turns text into vectors for the documentation search. */
	public String embeddingModel() {
		String m = service.getConfig().getString( "ai.embeddingModel", "nomic-embed-text" ).trim();
		return m.isEmpty() ? "nomic-embed-text" : m;
	}

	/**
	 * The API key. It is read from the environment variable named in <code>ai.apiKeyEnv</code> when that is set, else from <code>ai.apiKey</code>
	 * (a <code>bxsecret:</code> value in boxlang.json). A key is never typed into the console and never leaves this method except to the provider.
	 */
	public String apiKey() {
		String env = service.getConfig().getString( "ai.apiKeyEnv", "" ).trim();
		if ( !env.isEmpty() ) {
			String v = System.getenv( env );
			if ( v != null && !v.isBlank() ) {
				return v;
			}
		}
		return service.getConfig().aiApiKey();
	}

	/** Where the key comes from, for the AI page. Never the key. */
	public Map<String, Object> keySource() {
		Map<String, Object>	m	= new LinkedHashMap<>();
		String				env	= service.getConfig().getString( "ai.apiKeyEnv", "" ).trim();
		boolean				sec	= !service.getConfig().getString( "ai.apiKey", "" ).isBlank();
		if ( !env.isEmpty() ) {
			m.put( "source", "environment" );
			m.put( "name", env );
			m.put( "present", System.getenv( env ) != null && !System.getenv( env ).isBlank() );
		} else if ( sec ) {
			m.put( "source", "bxsecret" );
			m.put( "name", "ai.apiKey in boxlang.json" );
			m.put( "present", !service.getConfig().aiApiKey().isBlank() );
		} else {
			m.put( "source", "none" );
			m.put( "name", "" );
			m.put( "present", false );
		}
		return m;
	}

	public Map<String, Object> info() {
		LensConfig			c	= service.getConfig();
		Map<String, Object>	m	= new LinkedHashMap<>();
		m.put( "installed", installed() );
		m.put( "enabled", c.getBool( "ai.enabled", false ) );
		m.put( "canCall", canCall() );
		m.put( "licensed", service.getLicensing().has( "ai" ) );
		m.put( "provider", provider() );
		m.put( "model", model() );
		m.put( "links", c.getBool( "ai.links", true ) );
		return m;
	}

	/**
	 * Send a prompt and return the answer.
	 *
	 * @throws IllegalStateException with a message that is safe to show when the call cannot be made
	 */
	public String chat( String prompt ) {
		if ( !canCall() ) {
			throw new IllegalStateException( !service.getLicensing().has( "ai" ) ? "AI calls are a BoxLang+ feature. A license or trial is needed."
			    : installed() ? "AI is off. Set ai.enabled to true in boxlang.json." : "The bx-ai module is not installed." );
		}
		long now = System.currentTimeMillis();
		synchronized ( this ) {
			if ( now - windowStart > 60_000 ) {
				windowStart	= now;
				windowCount	= 0;
			}
			if ( ++windowCount > 10 ) {
				throw new IllegalStateException( "Too many AI requests. Try again in a minute." );
			}
		}
		if ( !running.compareAndSet( 0, 1 ) ) {
			throw new IllegalStateException( "An AI request is already running." );
		}
		try {
			LensConfig	c		= service.getConfig();
			IStruct		params	= new Struct();
			IStruct		options	= new Struct();
			if ( !model().isBlank() ) {
				params.put( Key.of( "model" ), model() );
			}
			params.put( Key.of( "temperature" ), 0.2 );
			options.put( Key.of( "provider" ), provider() );
			if ( !baseUrl().isBlank() ) {
				options.put( Key.of( "baseUrl" ), baseUrl() );
			}
			String key = apiKey();
			if ( !key.isBlank() ) {
				options.put( Key.of( "apiKey" ), key );
			}
			BoxRuntime		rt		= BoxRuntime.getInstance();
			Future<Object>	call	= pool.submit( () -> rt.getFunctionService().getGlobalFunction( AI_CHAT ).invoke( rt.getRuntimeContext(),
			    new Object[] { prompt, params, options }, false, AI_CHAT ) );
			try {
				Object answer = call.get( 90, TimeUnit.SECONDS );
				return answer == null ? "" : answer.toString();
			} catch ( java.util.concurrent.TimeoutException e ) {
				// Stop the call, do not leave it running in the background
				call.cancel( true );
				throw e;
			}
		} catch ( java.util.concurrent.RejectedExecutionException e ) {
			throw new IllegalStateException( "An AI request is already running." );
		} catch ( java.util.concurrent.TimeoutException e ) {
			throw new IllegalStateException( "The model did not answer in time." );
		} catch ( Exception e ) {
			Throwable root = e;
			while ( root.getCause() != null && root.getCause() != root ) {
				root = root.getCause();
			}
			service.getLogger().warn( "bx-lens AI call failed: {}", root.toString() );
			throw new IllegalStateException( "The AI call failed: " + String.valueOf( root.getMessage() ) );
		} finally {
			running.set( 0 );
		}
	}

	private volatile Map<String, Object> lastTest;

	/** The result of the last connection test, or null. */
	public Map<String, Object> lastTest() {
		return lastTest;
	}

	private volatile long	pingWindow;
	private volatile int	pingCount;

	/**
	 * Check the settings against the real provider: one tiny chat with the configured model, and, when the documentation search is on, one
	 * embedding with the embedding model. It works while <code>ai.enabled</code> is still false, so an admin can try before turning it on.
	 * It needs the bx-ai module and BoxLang+, is limited to six a minute and runs one at a time.
	 *
	 * @return what worked and what did not, with a message that is safe to show
	 */
	public Map<String, Object> ping() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "provider", provider() );
		out.put( "model", model() );
		out.put( "baseUrl", baseUrl() );
		out.put( "embeddingModel", embeddingModel() );
		if ( !installed() ) {
			out.put( "ok", false );
			out.put( "message", "The bx-ai module is not installed." );
			return out;
		}
		long now = System.currentTimeMillis();
		synchronized ( this ) {
			if ( now - pingWindow > 60_000 ) {
				pingWindow	= now;
				pingCount	= 0;
			}
			if ( ++pingCount > 6 ) {
				throw new IllegalStateException( "Too many connection tests. Try again in a minute." );
			}
		}
		if ( !running.compareAndSet( 0, 1 ) ) {
			throw new IllegalStateException( "An AI request is already running." );
		}
		try {
			IStruct	params	= new Struct();
			IStruct	options	= new Struct();
			if ( !model().isBlank() ) {
				params.put( Key.of( "model" ), model() );
			}
			params.put( Key.of( "temperature" ), 0 );
			options.put( Key.of( "provider" ), provider() );
			if ( !baseUrl().isBlank() ) {
				options.put( Key.of( "baseUrl" ), baseUrl() );
			}
			if ( !apiKey().isBlank() ) {
				options.put( Key.of( "apiKey" ), apiKey() );
			}
			out.put( "chat", attempt( AI_CHAT, new Object[] { "Reply with the single word: ok", params, options }, 40, true ) );
			if ( service.getConfig().getBool( "ai.rag", true ) ) {
				IStruct	eParams	= new Struct();
				IStruct	eOpts	= new Struct();
				eParams.put( Key.of( "model" ), embeddingModel() );
				eOpts.put( Key.of( "provider" ), provider() );
				if ( !baseUrl().isBlank() ) {
					eOpts.put( Key.of( "baseUrl" ), baseUrl() );
				}
				if ( !apiKey().isBlank() ) {
					eOpts.put( Key.of( "apiKey" ), apiKey() );
				}
				Map<String, Object> e = attempt( Key.of( "aiEmbed" ), new Object[] { "ping", eParams, eOpts }, 40, false );
				out.put( "embeddings", e );
			} else {
				out.put( "embeddings", Map.of( "ok", true, "skipped", true, "message", "The documentation search is off (ai.rag)." ) );
			}
			boolean chatOk = Boolean.TRUE.equals( ( ( Map<?, ?> ) out.get( "chat" ) ).get( "ok" ) );
			out.put( "ok", chatOk );
			out.put( "message", chatOk ? "The model answered." : ( ( Map<?, ?> ) out.get( "chat" ) ).get( "message" ) );
			out.put( "at", System.currentTimeMillis() );
			lastTest = out;
			return out;
		} finally {
			running.set( 0 );
		}
	}

	private Map<String, Object> attempt( Key fn, Object[] args, int seconds, boolean text ) {
		Map<String, Object>	m	= new LinkedHashMap<>();
		long				t0	= System.nanoTime();
		BoxRuntime			rt	= BoxRuntime.getInstance();
		try {
			Future<Object>	call	= pool.submit( () -> rt.getFunctionService().getGlobalFunction( fn ).invoke( rt.getRuntimeContext(), args, false, fn ) );
			Object			answer;
			try {
				answer = call.get( seconds, TimeUnit.SECONDS );
			} catch ( java.util.concurrent.TimeoutException e ) {
				call.cancel( true );
				throw e;
			}
			m.put( "ok", true );
			m.put( "message",
			    text ? "Answered: " + shortText( answer ) : "Got a vector of " + ( answer instanceof java.util.Collection<?> c ? c.size() : 0 ) + " numbers" );
		} catch ( java.util.concurrent.TimeoutException e ) {
			m.put( "ok", false );
			m.put( "message", "No answer in " + seconds + " seconds." );
		} catch ( Exception e ) {
			Throwable root = e;
			while ( root.getCause() != null && root.getCause() != root ) {
				root = root.getCause();
			}
			m.put( "ok", false );
			m.put( "message", ortus.boxlang.modules.bxlens.util.Secrets.text( String.valueOf( root.getMessage() ) ) );
		}
		m.put( "ms", ( System.nanoTime() - t0 ) / 1_000_000L );
		return m;
	}

	private static String shortText( Object o ) {
		String s = String.valueOf( o ).trim();
		return s.length() > 80 ? s.substring( 0, 80 ) + "..." : s;
	}

	/**
	 * Stop the pool when the module stops.
	 */
	public void shutdown() {
		pool.shutdownNow();
		pool = newPool();
	}

	private static ThreadPoolExecutor newPool() {
		return new ThreadPoolExecutor( 1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>( 1 ), r -> {
			Thread t = new Thread( r, "bxlens-ai" );
			t.setDaemon( true );
			return t;
		}, new ThreadPoolExecutor.AbortPolicy() );
	}

	/**
	 * A short, redacted summary of the server for questions: what is slow, failing or unhealthy right now.
	 */
	public String context() {
		StringBuilder sb = new StringBuilder();
		try {
			Map<String, Object> sys = service.getData().system();
			sb.append( "Uptime ms: " ).append( ( ( Map<?, ?> ) sys.get( "runtime" ) ).get( "uptimeMs" ) ).append( '\n' );
			Map<?, ?> heap = ( Map<?, ?> ) ( ( Map<?, ?> ) sys.get( "memory" ) ).get( "heap" );
			sb.append( "Heap used/max bytes: " ).append( heap.get( "used" ) ).append( " / " ).append( heap.get( "max" ) ).append( '\n' );
			sb.append( "Threads: " ).append( ( ( Map<?, ?> ) sys.get( "threads" ) ).get( "live" ) ).append( ", deadlocked " )
			    .append( ( ( Map<?, ?> ) sys.get( "threads" ) ).get( "deadlocked" ) ).append( '\n' );
		} catch ( Throwable t ) {
			// Leave out what cannot be read
		}
		try {
			Map<String, Object>	rep	= service.getReports().snapshot( false );
			Map<?, ?>			s	= ( Map<?, ?> ) rep.get( "session" );
			sb.append( "Requests " ).append( s.get( "requests" ) ).append( ", errors " ).append( s.get( "errors" ) ).append( ", avg ms " )
			    .append( s.get( "avgMs" ) )
			    .append( ", p95 ms " ).append( s.get( "p95" ) ).append( ", slow " ).append( s.get( "slow" ) ).append( '\n' );
			// No URLs and no query strings are sent to a model
		} catch ( Throwable t ) {
			// Skip
		}
		try {
			List<String> errs = new ArrayList<>();
			for ( Object g : ( List<?> ) service.getErrors().list().get( "groups" ) ) {
				Map<?, ?> m = ( Map<?, ?> ) g;
				errs.add( m.get( "type" ) + ": " + AiPrompts.safe( m.get( "message" ) ) + " (x" + m.get( "count" ) + ")" );
				if ( errs.size() >= 8 ) {
					break;
				}
			}
			sb.append( "Recent errors: " ).append( errs ).append( '\n' );
		} catch ( Throwable t ) {
			// Skip
		}
		try {
			List<String> qs = new ArrayList<>();
			for ( Object q : ( List<?> ) service.getQueryStats().snapshot().get( "statements" ) ) {
				Map<?, ?> m = ( Map<?, ?> ) q;
				if ( ( ( Number ) m.get( "maxMs" ) ).doubleValue() >= service.getConfig().slowQueryMs || ( ( Number ) m.get( "failures" ) ).longValue() > 0 ) {
					String stmt = AiPrompts.safe( m.get( "sql" ) );
					qs.add( stmt.substring( 0, Math.min( 120, stmt.length() ) ) + " max " + m.get( "maxMs" )
					    + " ms, failed "
					    + m.get( "failures" ) );
				}
				if ( qs.size() >= 6 ) {
					break;
				}
			}
			sb.append( "Slow or failing queries: " ).append( qs ).append( '\n' );
		} catch ( Throwable t ) {
			// Skip
		}
		try {
			sb.append( "Requests running now: " ).append( service.inflight().size() ).append( '\n' );
			sb.append( "Executors: " ).append( service.getData().executors().get( "summary" ) ).append( '\n' );
		} catch ( Throwable t ) {
			// Skip
		}
		return sb.toString();
	}

}
