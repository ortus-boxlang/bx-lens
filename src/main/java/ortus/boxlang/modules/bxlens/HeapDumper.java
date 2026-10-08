/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Takes one heap dump at a time with the JDK only (<code>HotSpotDiagnosticMXBean</code>), keeps it in a private temp folder, and removes it
 * after it was downloaded, discarded or has waited too long. A dump holds everything in memory, including secrets, so the console only
 * offers it when <code>console.allowHeapDump</code> is on and the caller is an admin. Only live objects are written, which runs a full GC
 * first and keeps the file smaller.
 */
public final class HeapDumper {

	private static final long				KEEP_MS			= 10 * 60_000L;
	private static final long				AFTER_DOWNLOAD	= 5 * 60_000L;
	private static final double				DISK_FACTOR		= 1.2;

	private final ScheduledExecutorService	timer			= Executors.newSingleThreadScheduledExecutor( r -> {
																Thread t = new Thread( r, "bxlens-heapdump-timer" );
																t.setDaemon( true );
																return t;
															} );

	private volatile String					state			= "idle";
	private volatile Path					dir;
	private volatile Path					file;
	private volatile long					startedAt;
	private volatile long					finishedAt;
	private volatile long					bytes;
	private volatile long					expiresAt;
	private volatile String					error			= "";

	/**
	 * Where dumps are written: the system temp folder.
	 */
	private Path tempRoot() {
		return Path.of( System.getProperty( "java.io.tmpdir" ) );
	}

	/**
	 * What the console shows before the admin confirms.
	 */
	public synchronized Map<String, Object> info( boolean allowed ) {
		Map<String, Object>	m		= new LinkedHashMap<>();
		long				used	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		long				free	= tempRoot().toFile().getUsableSpace();
		long				need	= ( long ) ( used * DISK_FACTOR );
		m.put( "enabled", allowed );
		m.put( "supported", supported() );
		m.put( "state", state );
		m.put( "tempDir", tempRoot().toString() );
		m.put( "freeDiskBytes", free );
		m.put( "usedHeapBytes", used );
		m.put( "estimateBytes", used );
		m.put( "needBytes", need );
		m.put( "enoughDisk", free >= need );
		m.put( "bytes", bytes );
		m.put( "startedAt", startedAt );
		m.put( "finishedAt", finishedAt );
		m.put( "expiresAt", expiresAt );
		m.put( "error", error );
		return m;
	}

	public static boolean supported() {
		try {
			return ManagementFactory.getPlatformMXBean( com.sun.management.HotSpotDiagnosticMXBean.class ) != null;
		} catch ( Throwable t ) {
			return false;
		}
	}

	/**
	 * Start a dump in the background.
	 *
	 * @return null when started, else the reason it was refused
	 */
	public synchronized String start() {
		if ( !supported() ) {
			return "This JVM cannot write heap dumps";
		}
		if ( "running".equals( state ) ) {
			return "A heap dump is already running";
		}
		if ( "ready".equals( state ) ) {
			return "A heap dump is waiting to be downloaded. Download or discard it first";
		}
		long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		if ( tempRoot().toFile().getUsableSpace() < ( long ) ( used * DISK_FACTOR ) ) {
			return "Not enough free disk space in " + tempRoot();
		}
		try {
			// A new private folder: the JDK refuses to overwrite and others on the host must not read it
			this.dir	= Files.createTempDirectory( tempRoot(), "bxlens-heap-" );
			this.file	= dir.resolve( "heap.hprof" );
		} catch ( IOException e ) {
			return "Could not create a temp folder: " + e.getMessage();
		}
		this.state		= "running";
		this.error		= "";
		this.bytes		= 0;
		this.startedAt	= System.currentTimeMillis();
		this.finishedAt	= 0;
		final Path	target	= this.file;
		Thread		t		= new Thread( () -> {
								try {
									ManagementFactory.getPlatformMXBean( com.sun.management.HotSpotDiagnosticMXBean.class ).dumpHeap( target.toString(), true );
									synchronized ( HeapDumper.this ) {
										this.bytes		= Files.size( target );
										this.finishedAt	= System.currentTimeMillis();
										this.expiresAt	= this.finishedAt + KEEP_MS;
										this.state		= "ready";
									}
									timer.schedule( this::expire, KEEP_MS, TimeUnit.MILLISECONDS );
								} catch ( Throwable e ) {
									cleanup();
									synchronized ( HeapDumper.this ) {
										this.error	= String.valueOf( e.getMessage() );
										this.state	= "failed";
									}
								}
							}, "bxlens-heapdump" );
		t.setDaemon( true );
		t.start();
		return null;
	}

	/**
	 * The file to send, or null when there is none ready. After this call the file is removed once a few minutes have passed.
	 */
	public synchronized File takeForDownload() {
		if ( !"ready".equals( state ) || file == null ) {
			return null;
		}
		this.expiresAt = System.currentTimeMillis() + AFTER_DOWNLOAD;
		timer.schedule( this::expire, AFTER_DOWNLOAD, TimeUnit.MILLISECONDS );
		return file.toFile();
	}

	/**
	 * Throw away a ready or failed dump.
	 */
	public synchronized void discard() {
		if ( !"running".equals( state ) ) {
			cleanup();
		}
	}

	/**
	 * Remove the file when its time is up.
	 */
	private synchronized void expire() {
		if ( "ready".equals( state ) && System.currentTimeMillis() >= expiresAt ) {
			cleanup();
		}
	}

	private synchronized void cleanup() {
		Path d = this.dir;
		this.state		= "idle";
		this.file		= null;
		this.dir		= null;
		this.bytes		= 0;
		this.expiresAt	= 0;
		if ( d != null ) {
			try ( Stream<Path> walk = Files.walk( d ) ) {
				walk.sorted( Comparator.reverseOrder() ).forEach( p -> p.toFile().delete() );
			} catch ( Throwable t ) {
				// Best effort
			}
		}
	}

	/**
	 * Remove leftovers when the module stops.
	 */
	public void shutdown() {
		discard();
		timer.shutdownNow();
	}

}
