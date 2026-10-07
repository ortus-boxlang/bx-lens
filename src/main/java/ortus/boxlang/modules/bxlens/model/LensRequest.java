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
package ortus.boxlang.modules.bxlens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;
import ortus.boxlang.runtime.context.RequestBoxContext;

/**
 * Everything Lens knows about one request. Created at request start, filled by collectors, frozen into a snapshot at request end.
 * Instances are shared across the threads a request may spawn, so every mutable collection is thread safe.
 */
public final class LensRequest {

	public final String								id				= UUID.randomUUID().toString().replace( "-", "" ).substring( 0, 10 );
	public final long								startNanos		= System.nanoTime();
	public final long								startMillis		= System.currentTimeMillis();
	public final AtomicBoolean						finished		= new AtomicBoolean( false );
	public final AtomicBoolean						injected		= new AtomicBoolean( false );

	public volatile boolean							enabled			= true;
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
	public volatile RequestBoxContext				requestContext;

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

	private final AtomicInteger						spanSeq			= new AtomicInteger( 0 );
	private final Map<String, AtomicInteger>		typeCounts		= new ConcurrentHashMap<>();
	private final Map<Long, ArrayDeque<Span>>		stacks			= new ConcurrentHashMap<>();

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
		Span				span	= new Span( spanSeq.incrementAndGet(), type, label, now(), stack.size() );
		spans.add( span );
		stack.push( span );
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
		ArrayDeque<Span>	stack	= stacks.get( Thread.currentThread().threadId() );
		if ( stack != null ) {
			while ( !stack.isEmpty() ) {
				Span top = stack.pop();
				if ( top.isOpen() ) {
					top.endNs = t;
				}
				if ( top == span ) {
					break;
				}
			}
		}
		if ( span.isOpen() ) {
			span.endNs = t;
		}
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
		return span;
	}

	/**
	 * Close every span still open, called when the request ends.
	 */
	public void closeAll() {
		long t = now();
		synchronized ( spans ) {
			for ( Span s : spans ) {
				if ( s.isOpen() ) {
					s.endNs = t;
				}
			}
		}
		stacks.clear();
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
	 * Add an issue. Severity is "crit" or "warn".
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
