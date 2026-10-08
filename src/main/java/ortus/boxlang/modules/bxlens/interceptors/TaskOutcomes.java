/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.interceptors;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ortus.boxlang.runtime.async.tasks.ScheduledTask;
import ortus.boxlang.runtime.events.BaseInterceptor;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Remembers how the last run of each scheduled task ended, so the console can show why a task is failing. Core keeps run counts but not
 * the error of the last run. Kept in memory only, one entry per task.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class TaskOutcomes extends BaseInterceptor {

	/**
	 * How a run ended.
	 */
	public record Outcome( boolean ok, String message, String stack, long at ) {
	}

	private static final Key			TASK		= Key.of( "task" );
	private static final Key			EXCEPTION	= Key.of( "exception" );

	private final Map<String, Outcome>	outcomes	= new ConcurrentHashMap<>();

	/**
	 * The last outcome of a task or null when it has not run since Lens started.
	 */
	public Outcome get( String scheduler, String task ) {
		return outcomes.get( key( scheduler, task ) );
	}

	@InterceptionPoint
	public void schedulerOnAnyTaskSuccess( IStruct data ) {
		ScheduledTask t = task( data );
		if ( t != null ) {
			outcomes.put( key( t ), new Outcome( true, "", "", System.currentTimeMillis() ) );
		}
	}

	@InterceptionPoint
	public void schedulerOnAnyTaskError( IStruct data ) {
		ScheduledTask t = task( data );
		if ( t == null ) {
			return;
		}
		Object	ex		= data.get( EXCEPTION );
		String	msg		= "Task failed";
		String	stack	= "";
		if ( ex instanceof Throwable th ) {
			Throwable root = th;
			while ( root.getCause() != null && root.getCause() != root ) {
				root = root.getCause();
			}
			msg = root.getClass().getSimpleName() + ": " + String.valueOf( root.getMessage() );
			StringWriter sw = new StringWriter();
			th.printStackTrace( new PrintWriter( sw ) );
			stack = sw.toString();
			if ( stack.length() > 6000 ) {
				stack = stack.substring( 0, 6000 ) + "\n...";
			}
		}
		outcomes.put( key( t ), new Outcome( false, msg, stack, System.currentTimeMillis() ) );
	}

	public void clear() {
		outcomes.clear();
	}

	/**
	 * Forget the outcomes of one scheduler, for example after it was reloaded.
	 */
	public void clear( String scheduler ) {
		String prefix = key( scheduler, "" );
		outcomes.keySet().removeIf( k -> k.startsWith( prefix ) );
	}

	private ScheduledTask task( IStruct data ) {
		return data != null && data.get( TASK ) instanceof ScheduledTask t ? t : null;
	}

	private static String key( ScheduledTask t ) {
		return key( t.hasScheduler() ? t.getScheduler().getSchedulerName() : "", t.getName() );
	}

	private static String key( String scheduler, String task ) {
		return scheduler + "\u0000" + task;
	}

}
