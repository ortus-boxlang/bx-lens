/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import ortus.boxlang.modules.bxlens.interceptors.TaskOutcomes;
import ortus.boxlang.modules.bxlens.util.Sanitizer;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.async.executors.BoxExecutor;
import ortus.boxlang.runtime.async.tasks.BaseScheduler;
import ortus.boxlang.runtime.async.tasks.ScheduledTask;
import ortus.boxlang.runtime.async.tasks.TaskRecord;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.async.tasks.IScheduler;
import ortus.boxlang.runtime.services.SchedulerService;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Live data for the console: executors with their health reports, scheduled tasks, JVM and system numbers, and threads. Everything is read
 * from BoxLang services and the JDK management beans, so nothing here costs a request anything.
 */
public final class ConsoleData {

	private final LensService	service;
	private volatile long		defsStamp	= -1;
	private volatile IStruct[]	defsCache	= new IStruct[ 0 ];

	public ConsoleData( LensService service ) {
		this.service = service;
	}

	private BoxRuntime runtime() {
		return BoxRuntime.getInstance();
	}

	private Sanitizer clean() {
		return new Sanitizer( service.getConfig() );
	}

	// ---------------------------------------------------------------------------------------------
	// Executors
	// ---------------------------------------------------------------------------------------------

	/**
	 * Every executor with its stats and health report, plus the scheduled tasks bound to it.
	 */
	@SuppressWarnings( "unchecked" )
	public Map<String, Object> executors() {
		Map<String, List<Map<String, Object>>>	byExecutor	= tasksByExecutor();
		List<Map<String, Object>>				list		= new ArrayList<>();
		Map<String, Integer>					counts		= new LinkedHashMap<>();
		for ( Map.Entry<String, BoxExecutor> e : runtime().getAsyncService().getExecutors().entrySet() ) {
			BoxExecutor ex = e.getValue();
			try {
				Object				cleaned	= clean().clean( ex.getStats() );
				Map<String, Object>	m		= new LinkedHashMap<>( ( Map<String, Object> ) cleaned );
				m.put( "name", e.getKey() );
				m.put( "kind", ex.type() == null ? "" : ex.type().name().toLowerCase( Locale.ROOT ) );
				m.put( "className", ex.executor().getClass().getSimpleName() );
				m.put( "tasks", byExecutor.getOrDefault( e.getKey(), List.of() ) );
				String health = String.valueOf( m.get( "healthStatus" ) );
				counts.merge( health, 1, Integer::sum );
				list.add( m );
			} catch ( Throwable t ) {
				service.getLogger().debug( "Could not read executor [{}]: {}", e.getKey(), t.toString() );
			}
		}
		list.sort( Comparator.comparingInt( ( Map<String, Object> m ) -> rank( String.valueOf( m.get( "healthStatus" ) ) ) )
		    .thenComparing( m -> String.valueOf( m.get( "name" ) ) ) );
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "executors", list );
		out.put( "counts", counts );
		return out;
	}

	private static int rank( String health ) {
		return switch ( health ) {
			case "critical" -> 0;
			case "degraded" -> 1;
			case "draining", "shutdown", "terminated" -> 2;
			case "healthy" -> 3;
			default -> 4;
		};
	}

	private Map<String, List<Map<String, Object>>> tasksByExecutor() {
		Map<String, List<Map<String, Object>>> out = new HashMap<>();
		for ( IScheduler s : runtime().getSchedulerService().getSchedulers().values() ) {
			if ( s instanceof BaseScheduler bs && bs.getExecutor() != null ) {
				String name = bs.getExecutor().name();
				for ( TaskRecord r : new ArrayList<>( bs.getTasks().values() ) ) {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put( "name", r.name );
					m.put( "group", r.group == null ? "" : r.group );
					m.put( "scheduler", bs.getSchedulerName() );
					m.put( "status", status( bs, r ) );
					out.computeIfAbsent( name, k -> new ArrayList<>() ).add( m );
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------
	// Scheduled tasks
	// ---------------------------------------------------------------------------------------------

	/**
	 * Schedulers and their tasks with status, metrics and the definition stored in tasks.json.
	 */
	public Map<String, Object> tasks() {
		Map<String, IStruct>		defs		= definitions();
		List<Map<String, Object>>	schedulers	= new ArrayList<>();
		int							total		= 0, failing = 0, paused = 0;
		for ( Map.Entry<Key, IScheduler> e : runtime().getSchedulerService().getSchedulers().entrySet() ) {
			if ( ! ( e.getValue() instanceof BaseScheduler bs ) ) {
				continue;
			}
			Map<String, Object> sm = new LinkedHashMap<>();
			sm.put( "name", bs.getSchedulerName() );
			sm.put( "executor", bs.getExecutor() == null ? "" : bs.getExecutor().name() );
			sm.put( "started", Boolean.TRUE.equals( bs.hasStarted() ) );
			sm.put( "startedAt", bs.getStartedAt() == null ? null : bs.getStartedAt().toString() );
			sm.put( "timezone", bs.getTimezone() == null ? "" : bs.getTimezone().getId() );
			List<Map<String, Object>> tasks = new ArrayList<>();
			for ( TaskRecord r : new ArrayList<>( bs.getTasks().values() ) ) {
				Map<String, Object> tm = taskMap( bs, r, defs.get( defKey( bs.getSchedulerName(), r.name ) ) );
				tasks.add( tm );
				total++;
				String st = String.valueOf( tm.get( "status" ) );
				if ( st.equals( "failing" ) || st.equals( "error" ) ) {
					failing++;
				} else if ( st.equals( "paused" ) ) {
					paused++;
				}
			}
			tasks.sort( Comparator.comparing( m -> String.valueOf( m.get( "name" ) ), String.CASE_INSENSITIVE_ORDER ) );
			sm.put( "tasks", tasks );
			schedulers.add( sm );
		}
		schedulers.sort( Comparator.comparing( m -> String.valueOf( m.get( "name" ) ), String.CASE_INSENSITIVE_ORDER ) );
		Map<String, Object> out = new LinkedHashMap<>();
		out.put( "schedulers", schedulers );
		out.put( "total", total );
		out.put( "failing", failing );
		out.put( "paused", paused );
		out.put( "tasksFile", tasksFilePath() == null ? "" : tasksFilePath().toString() );
		out.put( "reloadOnChange", runtime().getConfiguration().scheduler.reloadOnChange );
		out.put( "actions", service.getConfig().getBool( "console.actions", true ) );
		return out;
	}

	private Map<String, Object> taskMap( BaseScheduler bs, TaskRecord r, IStruct def ) {
		ScheduledTask		t		= r.task;
		IStruct				stats	= t.getStats();
		Map<String, Object>	m		= new LinkedHashMap<>();
		m.put( "name", r.name );
		m.put( "group", r.group == null ? "" : r.group );
		m.put( "scheduler", bs.getSchedulerName() );
		m.put( "status", status( bs, r ) );
		m.put( "persisted", def != null );
		m.put( "schedule", scheduleText( t, def ) );
		m.put( "executor", bs.getExecutor() == null ? "" : bs.getExecutor().name() );
		m.put( "created", str( stats.get( Key.of( "created" ) ) ) );
		m.put( "lastRun", str( stats.get( Key.of( "lastRun" ) ) ) );
		m.put( "nextRun", nextRun( bs, t, def, stats ) );
		m.put( "totalRuns", num( stats.get( Key.of( "totalRuns" ) ) ) );
		m.put( "totalSuccess", num( stats.get( Key.of( "totalSuccess" ) ) ) );
		m.put( "totalFailures", num( stats.get( Key.of( "totalFailures" ) ) ) );
		m.put( "lastMs", num( stats.get( Key.of( "lastExecutionTime" ) ) ) );
		m.put( "neverRun", Boolean.TRUE.equals( stats.get( Key.of( "neverRun" ) ) ) );
		m.put( "host", str( stats.get( Key.of( "inetHost" ) ) ) );
		m.put( "ip", str( stats.get( Key.of( "localIp" ) ) ) );
		m.put( "lastResult", resultOf( stats ) );
		m.put( "registeredAt", str( r.registeredAt ) );
		m.put( "scheduledAt", str( r.scheduledAt ) );
		m.put( "schedulingError", Boolean.TRUE.equals( r.error ) ? r.errorMessage : "" );
		TaskOutcomes.Outcome o = service.getOutcomes().get( bs.getSchedulerName(), r.name );
		if ( o != null ) {
			Map<String, Object> om = new LinkedHashMap<>();
			om.put( "ok", o.ok() );
			om.put( "message", o.message() );
			om.put( "stack", o.stack() );
			om.put( "at", o.at() );
			m.put( "outcome", om );
		}
		if ( def != null ) {
			Map<String, Object> dm = new LinkedHashMap<>();
			for ( Map.Entry<Key, Object> e : def.entrySet() ) {
				String	k	= e.getKey().getName();
				Object	v	= e.getValue();
				if ( v == null || ( v instanceof String s && s.isBlank() ) ) {
					continue;
				}
				dm.put( k, ortus.boxlang.modules.bxlens.util.Secrets.isSecretName( k ) ? "••••••"
				    : v instanceof String str ? ortus.boxlang.modules.bxlens.util.Secrets.text( str ) : clean().clean( v ) );
			}
			m.put( "definition", dm );
		}
		return m;
	}

	/**
	 * Next fire time. For cron tasks core keeps a polling time in its stats, so the real next fire is worked out from the expression.
	 */
	private String nextRun( BaseScheduler bs, ScheduledTask t, IStruct def, IStruct stats ) {
		String cron = def == null ? "" : str( def.get( Key.of( "cronTime" ) ) );
		if ( !cron.isBlank() ) {
			try {
				java.time.ZoneId	zone	= bs.getTimezone() == null ? java.time.ZoneId.systemDefault() : bs.getTimezone();
				long				delay	= ortus.boxlang.runtime.async.CronExpression.parse( cron ).nextFireDelayMillis( zone );
				return java.time.LocalDateTime.now( zone ).plusNanos( ( delay + 500 ) * 1_000_000L ).withNano( 0 ).toString();
			} catch ( Throwable e ) {
				// Fall back to what core reports
			}
		}
		return str( stats.get( Key.of( "nextRun" ) ) );
	}

	private String status( BaseScheduler bs, TaskRecord r ) {
		if ( Boolean.TRUE.equals( r.disabled ) || Boolean.TRUE.equals( r.task.isDisabled() ) ) {
			return "paused";
		}
		if ( Boolean.TRUE.equals( r.error ) ) {
			return "error";
		}
		TaskOutcomes.Outcome o = service.getOutcomes().get( bs.getSchedulerName(), r.name );
		if ( o != null && !o.ok() ) {
			return "failing";
		}
		if ( Boolean.TRUE.equals( r.task.getStats().get( Key.of( "neverRun" ) ) ) ) {
			return "never";
		}
		return "scheduled";
	}

	private String scheduleText( ScheduledTask t, IStruct def ) {
		if ( def != null ) {
			String cron = str( def.get( Key.of( "cronTime" ) ) );
			if ( !cron.isBlank() ) {
				return "cron " + cron;
			}
			String interval = str( def.get( Key.of( "interval" ) ) );
			if ( !interval.isBlank() ) {
				return interval.matches( "\\d+" ) ? "every " + interval + " s" : interval;
			}
		}
		try {
			long		p	= t.getPeriod();
			TimeUnit	u	= t.getTimeUnit();
			if ( p > 0 && u != null ) {
				return "every " + p + " " + u.name().toLowerCase( Locale.ROOT );
			}
			String tt = t.getTaskTime();
			if ( tt != null && !tt.isBlank() ) {
				return "daily at " + tt;
			}
		} catch ( Throwable e ) {
			// Fall through
		}
		return "custom";
	}

	private Object resultOf( IStruct stats ) {
		Object r = stats.get( Key.of( "lastResult" ) );
		if ( r instanceof Optional<?> o ) {
			r = o.orElse( null );
		}
		return r == null ? null : clean().clean( r );
	}

	/**
	 * Read tasks.json, cached by its modification time.
	 */
	private Map<String, IStruct> definitions() {
		Map<String, IStruct>	out		= new HashMap<>();
		Path					file	= tasksFilePath();
		try {
			if ( file != null && Files.exists( file ) ) {
				long stamp = Files.getLastModifiedTime( file ).toMillis();
				if ( stamp != defsStamp ) {
					Array a = runtime().getSchedulerService().loadTasksFromDisk();
					defsCache	= a.stream().filter( o -> o instanceof IStruct ).map( o -> ( IStruct ) o ).toArray( IStruct[]::new );
					defsStamp	= stamp;
				}
				for ( IStruct d : defsCache ) {
					out.put( defKey(
					    str( d.get( Key.of( "scheduler" ) ) ).isBlank() ? SchedulerService.DEFAULT_SCHEDULER_NAME : str( d.get( Key.of( "scheduler" ) ) ),
					    str( d.get( Key.of( "task" ) ) ) ), d );
				}
			}
		} catch ( Throwable t ) {
			service.getLogger().debug( "Could not read tasks.json: {}", t.toString() );
		}
		return out;
	}

	private Path tasksFilePath() {
		try {
			return Path.of( runtime().getConfiguration().scheduler.tasksFile );
		} catch ( Throwable t ) {
			return null;
		}
	}

	private static String defKey( String scheduler, String task ) {
		return scheduler + "\u0000" + task;
	}

	/**
	 * Run an action on a task or a scheduler.
	 *
	 * @param action    pause, resume, run, pauseall, resumeall or reload
	 * @param scheduler scheduler name
	 * @param taskName  task name for the single task actions, else null
	 *
	 * @return what happened
	 */
	public Map<String, Object> taskAction( String action, String scheduler, String taskName ) {
		SchedulerService	svc	= runtime().getSchedulerService();
		Map<String, Object>	out	= new LinkedHashMap<>();
		Key					key	= Key.of( scheduler );
		if ( !svc.hasScheduler( key ) || ! ( svc.getScheduler( key ) instanceof BaseScheduler bs ) ) {
			out.put( "ok", false );
			out.put( "message", "Unknown scheduler" );
			return out;
		}
		switch ( action ) {
			case "pause", "resume", "run" -> {
				TaskRecord r = bs.getTaskRecord( taskName );
				if ( r == null ) {
					out.put( "ok", false );
					out.put( "message", "Unknown task" );
					return out;
				}
				if ( action.equals( "pause" ) ) {
					pause( svc, bs, r );
					out.put( "message", taskName + " paused" );
				} else if ( action.equals( "resume" ) ) {
					resume( svc, bs, r );
					out.put( "message", taskName + " resumed" );
				} else {
					return runNow( bs, r );
				}
			}
			case "pauseall" -> {
				for ( TaskRecord r : new ArrayList<>( bs.getTasks().values() ) ) {
					pauseLocal( r );
				}
				svc.updateAllTasksPausedState( scheduler, null, true );
				out.put( "message", "All tasks in " + scheduler + " paused" );
			}
			case "resumeall" -> {
				for ( TaskRecord r : new ArrayList<>( bs.getTasks().values() ) ) {
					resumeLocal( bs, r );
				}
				svc.updateAllTasksPausedState( scheduler, null, false );
				out.put( "message", "All tasks in " + scheduler + " resumed" );
			}
			case "reload" -> {
				svc.reloadSchedulerFromDisk( key, false, SchedulerService.DEFAULT_SHUTDOWN_TIMEOUT );
				service.getOutcomes().clear( scheduler );
				out.put( "message", "Scheduler " + scheduler + " reloaded from tasks.json" );
			}
			default -> {
				out.put( "ok", false );
				out.put( "message", "Unknown action" );
				return out;
			}
		}
		out.put( "ok", true );
		return out;
	}

	private void pause( SchedulerService svc, BaseScheduler bs, TaskRecord r ) {
		pauseLocal( r );
		svc.updateTaskPausedState( r.name, bs.getSchedulerName(), true );
	}

	private void resume( SchedulerService svc, BaseScheduler bs, TaskRecord r ) {
		resumeLocal( bs, r );
		svc.updateTaskPausedState( r.name, bs.getSchedulerName(), false );
	}

	private void pauseLocal( TaskRecord r ) {
		r.task.disable();
		r.disabled = true;
		if ( r.future != null ) {
			r.future.cancel( false );
		}
	}

	private void resumeLocal( BaseScheduler bs, TaskRecord r ) {
		r.task.enable();
		r.disabled		= false;
		r.scheduledAt	= null;
		bs.startupTask( r.name );
	}

	/**
	 * Run a task right now on its own thread, wait for it and report how it went.
	 */
	private Map<String, Object> runNow( BaseScheduler bs, TaskRecord r ) {
		Map<String, Object>	out			= new LinkedHashMap<>();
		long				began		= System.currentTimeMillis();
		int					timeoutS	= Math.max( 1, service.getConfig().getInt( "console.runTimeoutSeconds", 60 ) );
		Thread				t			= Thread.ofVirtual().name( "bxlens-run-" + r.name ).unstarted( () -> r.task.run( true ) );
		t.start();
		try {
			t.join( timeoutS * 1000L );
		} catch ( InterruptedException e ) {
			Thread.currentThread().interrupt();
		}
		boolean timedOut = t.isAlive();
		out.put( "task", r.name );
		out.put( "scheduler", bs.getSchedulerName() );
		out.put( "timedOut", timedOut );
		TaskOutcomes.Outcome	o		= service.getOutcomes().get( bs.getSchedulerName(), r.name );
		boolean					fresh	= o != null && o.at() >= began;
		out.put( "ok", !timedOut && ( !fresh || o.ok() ) );
		out.put( "ms", num( r.task.getStats().get( Key.of( "lastExecutionTime" ) ) ) );
		out.put( "result", resultOf( r.task.getStats() ) );
		if ( fresh && !o.ok() ) {
			out.put( "error", o.message() );
			out.put( "stack", o.stack() );
		}
		out.put( "message", timedOut ? "Still running after " + timeoutS + " s. It keeps going in the background." : fresh && !o.ok() ? "Failed" : "Ran" );
		return out;
	}

	// ---------------------------------------------------------------------------------------------
	// System
	// ---------------------------------------------------------------------------------------------

	/**
	 * CPU, memory, memory pools, garbage collection, class loading, threads, disks and runtime details from the JDK management beans.
	 */
	public Map<String, Object> system() {
		Map<String, Object>	m	= new LinkedHashMap<>();
		var					os	= ManagementFactory.getOperatingSystemMXBean();
		Map<String, Object>	cpu	= new LinkedHashMap<>();
		cpu.put( "cores", os.getAvailableProcessors() );
		cpu.put( "loadAverage", round( os.getSystemLoadAverage() ) );
		cpu.put( "os", os.getName() + " " + os.getVersion() );
		cpu.put( "arch", os.getArch() );
		if ( os instanceof com.sun.management.OperatingSystemMXBean x ) {
			cpu.put( "processCpu", pct( x.getProcessCpuLoad() ) );
			cpu.put( "systemCpu", pct( x.getCpuLoad() ) );
			cpu.put( "processCpuTimeMs", x.getProcessCpuTime() / 1_000_000L );
			Map<String, Object> ram = new LinkedHashMap<>();
			ram.put( "total", x.getTotalMemorySize() );
			ram.put( "free", x.getFreeMemorySize() );
			ram.put( "swapTotal", x.getTotalSwapSpaceSize() );
			ram.put( "swapFree", x.getFreeSwapSpaceSize() );
			ram.put( "committedVirtual", x.getCommittedVirtualMemorySize() );
			m.put( "ram", ram );
		}
		m.put( "cpu", cpu );

		MemoryUsage			heap	= ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
		MemoryUsage			non		= ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
		Map<String, Object>	mem		= new LinkedHashMap<>();
		mem.put( "heap", usage( heap ) );
		mem.put( "nonHeap", usage( non ) );
		List<Map<String, Object>> pools = new ArrayList<>();
		for ( MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans() ) {
			Map<String, Object> pm = usage( p.getUsage() );
			pm.put( "name", p.getName() );
			pm.put( "type", p.getType().name() );
			pools.add( pm );
		}
		mem.put( "pools", pools );
		m.put( "memory", mem );

		List<Map<String, Object>> gcs = new ArrayList<>();
		for ( GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans() ) {
			Map<String, Object> gm = new LinkedHashMap<>();
			gm.put( "name", g.getName() );
			gm.put( "count", g.getCollectionCount() );
			gm.put( "timeMs", g.getCollectionTime() );
			gcs.add( gm );
		}
		m.put( "gc", gcs );

		ThreadMXBean		tb	= ManagementFactory.getThreadMXBean();
		Map<String, Object>	th	= new LinkedHashMap<>();
		th.put( "live", tb.getThreadCount() );
		th.put( "daemon", tb.getDaemonThreadCount() );
		th.put( "peak", tb.getPeakThreadCount() );
		th.put( "started", tb.getTotalStartedThreadCount() );
		long[] dead = tb.findDeadlockedThreads();
		th.put( "deadlocked", dead == null ? 0 : dead.length );
		m.put( "threads", th );

		var					cl	= ManagementFactory.getClassLoadingMXBean();
		Map<String, Object>	cm	= new LinkedHashMap<>();
		cm.put( "loaded", cl.getLoadedClassCount() );
		cm.put( "total", cl.getTotalLoadedClassCount() );
		cm.put( "unloaded", cl.getUnloadedClassCount() );
		m.put( "classes", cm );

		RuntimeMXBean		rt	= ManagementFactory.getRuntimeMXBean();
		Map<String, Object>	rm	= new LinkedHashMap<>();
		rm.put( "vm", rt.getVmName() + " " + rt.getVmVersion() );
		rm.put( "vendor", rt.getVmVendor() );
		rm.put( "java", System.getProperty( "java.version" ) );
		rm.put( "pid", rt.getPid() );
		rm.put( "uptimeMs", rt.getUptime() );
		rm.put( "startedAt", Instant.ofEpochMilli( rt.getStartTime() ).toString() );
		rm.put( "flags", redactArgs( rt.getInputArguments() ) );
		rm.put( "properties", properties() );
		m.put( "runtime", rm );

		List<Map<String, Object>> disks = new ArrayList<>();
		for ( File root : File.listRoots() ) {
			if ( root.getTotalSpace() > 0 ) {
				Map<String, Object> d = new LinkedHashMap<>();
				d.put( "path", root.getPath() );
				d.put( "total", root.getTotalSpace() );
				d.put( "usable", root.getUsableSpace() );
				disks.add( d );
			}
		}
		m.put( "disks", disks );
		m.put( "at", System.currentTimeMillis() );
		return m;
	}

	private static Map<String, Object> usage( MemoryUsage u ) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put( "used", u.getUsed() );
		m.put( "committed", u.getCommitted() );
		m.put( "max", u.getMax() );
		return m;
	}

	private static Object pct( double v ) {
		return v < 0 ? null : round( v * 100.0 );
	}

	private static double round( double v ) {
		return Math.round( v * 10.0 ) / 10.0;
	}

	private List<String> redactArgs( List<String> args ) {
		List<String> out = new ArrayList<>();
		for ( String a : args ) {
			// Hides the value of -Dname=value and --flag=value pairs whose name looks secret, wherever they stand in the argument
			out.add( ortus.boxlang.modules.bxlens.util.Secrets.text( a ) );
		}
		return out;
	}

	private Map<String, Object> properties() {
		Map<String, Object> out = new LinkedHashMap<>();
		for ( String k : List.of( "os.name", "os.version", "os.arch", "user.timezone", "user.language", "file.encoding", "java.io.tmpdir", "user.dir",
		    "java.home",
		    "java.vm.name", "java.specification.version" ) ) {
			String v = System.getProperty( k );
			if ( v != null ) {
				out.put( k, v );
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------
	// Threads
	// ---------------------------------------------------------------------------------------------

	/**
	 * Every thread with state, CPU time, locks and stack. Taking a dump pauses the JVM briefly, so this is on demand and not part of the stream.
	 */
	public Map<String, Object> threads() {
		ThreadMXBean				tb		= ManagementFactory.getThreadMXBean();
		ThreadInfo[]				infos	= tb.dumpAllThreads( true, true );
		long						self	= Thread.currentThread().threadId();
		List<Map<String, Object>>	list	= new ArrayList<>();
		Map<String, Integer>		states	= new LinkedHashMap<>();
		for ( ThreadInfo ti : infos ) {
			if ( ti == null ) {
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put( "id", ti.getThreadId() );
			m.put( "name", ti.getThreadName() );
			m.put( "pool", poolOf( ti.getThreadName() ) );
			m.put( "state", ti.getThreadState().name() );
			m.put( "daemon", ti.isDaemon() );
			m.put( "priority", ti.getPriority() );
			long cpu = tb.isThreadCpuTimeSupported() ? tb.getThreadCpuTime( ti.getThreadId() ) : -1;
			m.put( "cpuMs", cpu < 0 ? null : cpu / 1_000_000L );
			m.put( "blockedCount", ti.getBlockedCount() );
			m.put( "blockedMs", ti.getBlockedTime() );
			m.put( "waitedCount", ti.getWaitedCount() );
			m.put( "lock", ti.getLockName() );
			m.put( "lockOwner", ti.getLockOwnerName() );
			m.put( "self", ti.getThreadId() == self );
			List<Map<String, Object>>	frames		= new ArrayList<>();
			boolean						hasBoxLang	= false;
			for ( StackTraceElement e : ti.getStackTrace() ) {
				if ( frames.size() >= 60 ) {
					break;
				}
				boolean bx = isBoxLangFrame( e );
				hasBoxLang |= bx;
				Map<String, Object> f = new LinkedHashMap<>();
				f.put( "text", e.toString() );
				f.put( "bx", bx );
				frames.add( f );
			}
			m.put( "frames", frames );
			m.put( "boxlang", hasBoxLang );
			List<String> locked = new ArrayList<>();
			for ( var mi : ti.getLockedMonitors() ) {
				locked.add( mi.toString() );
			}
			m.put( "locked", locked );
			states.merge( ti.getThreadState().name(), 1, Integer::sum );
			list.add( m );
		}
		list.sort( Comparator.comparing( m -> String.valueOf( m.get( "name" ) ), String.CASE_INSENSITIVE_ORDER ) );
		long[]				dead	= tb.findDeadlockedThreads();
		Map<String, Object>	out		= new LinkedHashMap<>();
		out.put( "threads", list );
		out.put( "states", states );
		out.put( "deadlocked", dead == null ? List.of() : java.util.Arrays.stream( dead ).boxed().toList() );
		out.put( "at", System.currentTimeMillis() );
		return out;
	}

	/**
	 * A thread dump as text, laid out like jstack so existing tools can read it.
	 */
	public String threadDump() {
		StringBuilder	sb	= new StringBuilder();
		ThreadMXBean	tb	= ManagementFactory.getThreadMXBean();
		sb.append( "BX Lens thread dump " ).append( Instant.now() ).append( '\n' ).append( ManagementFactory.getRuntimeMXBean().getVmName() ).append( ' ' )
		    .append( System.getProperty( "java.version" ) ).append( "\n\n" );
		for ( ThreadInfo ti : tb.dumpAllThreads( true, true ) ) {
			if ( ti == null ) {
				continue;
			}
			long cpu = tb.isThreadCpuTimeSupported() ? tb.getThreadCpuTime( ti.getThreadId() ) / 1_000_000L : -1;
			sb.append( '"' ).append( ti.getThreadName() ).append( "\" #" ).append( ti.getThreadId() ).append( " prio=" ).append( ti.getPriority() )
			    .append( ti.isDaemon() ? " daemon" : "" ).append( cpu >= 0 ? " cpu=" + cpu + "ms" : "" ).append( '\n' );
			sb.append( "   java.lang.Thread.State: " ).append( ti.getThreadState() );
			if ( ti.getLockName() != null ) {
				sb.append( " on " ).append( ti.getLockName() );
				if ( ti.getLockOwnerName() != null ) {
					sb.append( " owned by \"" ).append( ti.getLockOwnerName() ).append( "\" #" ).append( ti.getLockOwnerId() );
				}
			}
			sb.append( '\n' );
			StackTraceElement[] st = ti.getStackTrace();
			for ( int i = 0; i < st.length; i++ ) {
				sb.append( "\tat " ).append( st[ i ] ).append( '\n' );
				for ( var mi : ti.getLockedMonitors() ) {
					if ( mi.getLockedStackDepth() == i ) {
						sb.append( "\t- locked " ).append( mi ).append( '\n' );
					}
				}
			}
			sb.append( '\n' );
		}
		long[] dead = tb.findDeadlockedThreads();
		if ( dead != null ) {
			sb.append( "Found deadlocked threads: " ).append( java.util.Arrays.toString( dead ) ).append( '\n' );
		}
		return sb.toString();
	}

	private static boolean isBoxLangFrame( StackTraceElement e ) {
		String f = e.getFileName();
		if ( f != null ) {
			String l = f.toLowerCase( Locale.ROOT );
			if ( l.endsWith( ".bx" ) || l.endsWith( ".bxm" ) || l.endsWith( ".bxs" ) || l.endsWith( ".cfc" ) || l.endsWith( ".cfm" ) || l.endsWith( ".cfs" ) ) {
				return true;
			}
		}
		return e.getClassName().startsWith( "boxgenerated" );
	}

	private static String poolOf( String name ) {
		if ( name == null ) {
			return "";
		}
		String p = name.replaceAll( "[-_ #]?\\d+$", "" ).replaceAll( "[-_ ]task[-_ ]?\\d*$", "" );
		return p.isBlank() ? name : p;
	}

	private static String str( Object o ) {
		return o == null ? "" : o.toString();
	}

	private static Object num( Object o ) {
		return o instanceof Number n ? n : 0;
	}

}
