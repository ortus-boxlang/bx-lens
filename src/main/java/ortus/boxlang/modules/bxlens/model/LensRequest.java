/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;
import ortus.boxlang.runtime.context.RequestBoxContext;

/**
 * Everything Lens knows about one request. Created at request start, filled by collectors, frozen into a snapshot at request end.
 * Instances are shared across the threads a request may spawn, so every mutable collection is thread safe.
 */
public final class LensRequest {

	private static final AtomicLong					SEQ				= new AtomicLong();

	public final long								startMillis		= System.currentTimeMillis();
	/** Unique and cheap: the start time and a counter, both in base 36. It is not a secret. Sessions and CSRF tokens use a secure random. */
	public final String								id				= newId( this.startMillis );
	public final long								startNanos		= System.nanoTime();
	public final AtomicBoolean						finished		= new AtomicBoolean( false );
	public final AtomicBoolean						injected		= new AtomicBoolean( false );

	public volatile boolean							enabled			= true;
	/** Is the bar shown to this caller? False when the request is collected only for the console. */
	public volatile boolean							showBar			= true;
	public volatile long							endNanos		= -1;
	public volatile String							method			= "";
	public volatile String							url				= "";
	public volatile String							uri				= "";
	public volatile String							queryString		= "";
	public volatile String							remoteAddr		= "";
	public volatile String							host			= "";
	public volatile String							userAgent		= "";
	public volatile String							appName			= "";
	public volatile String							template		= "";
	public volatile String							contentType		= "";
	public volatile int								status			= 200;
	public volatile boolean							html			= false;
	/** The request never ended by itself: it was cut off or swept by the watchdog. Its open spans were closed with an estimate. */
	public volatile boolean							unfinished		= false;
	/** Has the issue engine looked at this request? It does so for the console, once. */
	public volatile boolean							analyzed		= false;
	public volatile RequestBoxContext				requestContext;
	/** The thread that handles the request, sampled by the slow request watchdog. */
	public volatile Thread							thread;

	/** Request headers as received, copied when the request ends and sanitized only when a snapshot is built. Null when not captured. */
	public volatile Map<String, String>				requestHeaders;
	/** Response headers as sent, copied when the request ends. Null when not captured. */
	public volatile Map<String, String>				responseHeaders;

	public final List<Span>							spans			= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			queries			= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			exceptions		= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			messages		= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			logs			= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			http			= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			timers			= Collections.synchronizedList( new ArrayList<>() );
	public final List<Map<String, Object>>			issues			= Collections.synchronizedList( new ArrayList<>() );
	public final Map<String, Object>				data			= new ConcurrentHashMap<>();
	public final Map<String, LensPanelBuilder>		panels			= Collections.synchronizedMap( new LinkedHashMap<>() );
	public final Map<String, Map<String, Object>>	pendingTimers	= new ConcurrentHashMap<>();

	/** Nanoseconds since the request started at the last thing Lens saw happen: a span opened or closed, an exception. */
	private volatile long							lastActivity	= 0;
	/** When an exception was last seen on each thread, to close the spans it left open. */
	private final Map<Long, Long>					exceptionAt		= new ConcurrentHashMap<>();
	private final AtomicInteger						spanSeq			= new AtomicInteger( 0 );
	private final Map<String, AtomicInteger>		typeCounts		= new ConcurrentHashMap<>();
	private final Map<Long, ArrayDeque<Span>>		stacks			= new ConcurrentHashMap<>();

	private static String newId( long millis ) {
		return Long.toString( millis, 36 ) + Long.toString( SEQ.incrementAndGet(), 36 );
	}

	/**
	 * Let go of the request context and the thread, so a finished request kept in the history does not keep the whole web request alive.
	 */
	public void release() {
		this.requestContext	= null;
		this.thread			= null;
	}

	/**
	 * Nanoseconds since the request started.
	 */
	public long now() {
		return System.nanoTime() - startNanos;
	}

	/**
	 * Wall clock duration so far (or total, once finished) in nanoseconds.
	 */
	public long durationNs() {
		return ( endNanos > 0 ? endNanos : System.nanoTime() ) - startNanos;
	}

	/**
	 * Count how many items of a kind were recorded and report whether another fits under the cap.
	 *
	 * @param kind kind of item, for example "query"
	 * @param max  maximum items of that kind
	 */
	public boolean reserve( String kind, int max ) {
		return typeCounts.computeIfAbsent( kind, k -> new AtomicInteger() ).incrementAndGet() <= max;
	}

	/**
	 * How many items of a kind were seen, including ones dropped over the cap.
	 */
	public int seen( String kind ) {
		AtomicInteger a = typeCounts.get( kind );
		return a == null ? 0 : a.get();
	}

	/**
	 * Open a span nested under whatever is open on the current thread.
	 *
	 * @return the span, or null when the cap for this type is reached
	 */
	public Span begin( String type, String label, int max ) {
		if ( !reserve( "span:" + type, max ) ) {
			return null;
		}
		ArrayDeque<Span>	stack	= stacks.computeIfAbsent( Thread.currentThread().threadId(), k -> new ArrayDeque<>() );
		long				t		= now();
		Span				span	= new Span( spanSeq.incrementAndGet(), type, label, t, stack.size() );
		spans.add( span );
		stack.push( span );
		lastActivity = t;
		return span;
	}

	/**
	 * Close a span. Anything opened after it on this thread and never closed (for example by an exception) is closed with it.
	 */
	public void end( Span span ) {
		if ( span == null ) {
			return;
		}
		long				t		= now();
		long				before	= lastActivity;
		long				tid		= Thread.currentThread().threadId();
		ArrayDeque<Span>	stack	= stacks.get( tid );
		if ( stack != null ) {
			while ( !stack.isEmpty() ) {
				Span top = stack.pop();
				if ( top == span ) {
					break;
				}
				if ( top.isOpen() ) {
					// Its own end event never came (an exception went through it, or the event was skipped). It ended when the last thing we
					// saw on this thread happened, not when the span around it ended
					top.endNs		= estimate( top, exceptionAt.get( tid ), before, t );
					top.interrupted	= true;
				}
			}
		}
		if ( span.isOpen() ) {
			span.endNs = t;
		}
		lastActivity = t;
	}

	/**
	 * Nearest open span of a type on the current thread, or null.
	 */
	public Span open( String type ) {
		ArrayDeque<Span> stack = stacks.get( Thread.currentThread().threadId() );
		if ( stack == null ) {
			return null;
		}
		for ( Span s : stack ) {
			if ( s.type.equals( type ) ) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Nearest open span of a type and label on the current thread, or null.
	 */
	public Span open( String type, String label ) {
		ArrayDeque<Span> stack = stacks.get( Thread.currentThread().threadId() );
		if ( stack == null ) {
			return null;
		}
		for ( Span s : stack ) {
			if ( s.type.equals( type ) && s.label.equals( label ) ) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Add an already finished span, for example from a manual measure.
	 */
	public Span addClosed( String type, String label, long startNs, long endNs, int max ) {
		if ( !reserve( "span:" + type, max ) ) {
			return null;
		}
		ArrayDeque<Span>	stack	= stacks.get( Thread.currentThread().threadId() );
		Span				span	= new Span( spanSeq.incrementAndGet(), type, label, startNs, stack == null ? 0 : stack.size() );
		span.endNs = endNs;
		spans.add( span );
		lastActivity = Math.max( lastActivity, endNs );
		return span;
	}

	/**
	 * Close every span still open, called when the request ends.
	 */
	public void closeAll() {
		long	t		= now();
		long	lastEx	= -1;
		for ( Long x : exceptionAt.values() ) {
			lastEx = Math.max( lastEx, x );
		}
		synchronized ( spans ) {
			for ( Span s : spans ) {
				if ( s.isOpen() ) {
					// Still open when the request ended: its end event never came. It is marked, and it ends where the activity ended
					s.endNs			= estimate( s, lastEx < 0 ? null : lastEx, lastActivity, t );
					s.interrupted	= true;
				}
			}
		}
		stacks.clear();
	}

	/**
	 * Remember that an exception passed on the current thread now. Spans on that thread that never see their end event are closed with this time.
	 */
	public void noteException() {
		long t = now();
		exceptionAt.put( Thread.currentThread().threadId(), t );
		lastActivity = Math.max( lastActivity, t );
	}

	/**
	 * Did any span end without its own end event?
	 */
	public boolean hasInterrupted() {
		synchronized ( spans ) {
			for ( Span s : spans ) {
				if ( s.interrupted ) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The end of a span whose end event never came: the exception on its thread when one came after it started, else the last activity,
	 * never before its start and never after <code>limit</code>.
	 */
	private static long estimate( Span s, Long exception, long lastActivity, long limit ) {
		long end = exception != null && exception >= s.startNs ? exception : lastActivity;
		return Math.max( s.startNs, Math.min( limit, end ) );
	}

	/**
	 * Get or create a custom panel owned by this request.
	 */
	public LensPanelBuilder panel( String panelId, String label, String renderer ) {
		synchronized ( panels ) {
			return panels.computeIfAbsent( panelId, k -> new LensPanelBuilder( panelId, label, renderer ) );
		}
	}

	/**
	 * Issues that count: everything except notes ("info"), which are listed on the Issues tab but do not color the strip.
	 */
	public int countIssues() {
		int n = 0;
		synchronized ( issues ) {
			for ( Map<String, Object> i : issues ) {
				if ( !"info".equals( i.get( "severity" ) ) ) {
					n++;
				}
			}
		}
		return n;
	}

	/**
	 * Add an issue. Severity is "crit", "warn" or "info" (a note that does not color the strip).
	 */
	public void addIssue( String severity, String title, String detail, String file, int line, String tab, int spanId ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "severity", severity );
		m.put( "title", title );
		m.put( "detail", detail == null ? "" : detail );
		m.put( "file", file == null ? "" : file );
		m.put( "line", line );
		m.put( "tab", tab == null ? "" : tab );
		m.put( "span", spanId );
		issues.add( m );
	}

}
