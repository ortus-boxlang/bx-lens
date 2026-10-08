/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Sends a message to the Lens Messages panel.
 * <p>
 * Example: <code>lensMessage( "Loaded #orders.len()# orders", "info" )</code>. Structs, arrays and other values are shown as a collapsible tree.
 *
 * @argument.message The message: text or any value
 *
 * @argument.label The level or label: info, warn, error, debug or any text. Default info.
 */
@BoxBIF
public class LensMessage extends BaseLensBIF {

	private static final Key	MESSAGE	= Key.of( "message" );
	private static final Key	LABEL	= Key.of( "label" );

	public LensMessage() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, MESSAGE ),
		    new Argument( false, Argument.STRING, LABEL, "info" )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest req = request( context );
		if ( req == null || !service().getConfig().isCollectorEnabled( "messages", true ) ) {
			return null;
		}
		if ( !req.reserve( "message", service().getConfig().collectorInt( "messages", "max", 200 ) ) ) {
			return null;
		}
		Object				value	= arguments.get( MESSAGE );
		Map<String, Object>	m		= new LinkedHashMap<>();
		m.put( "level", arguments.getAsString( LABEL ).toLowerCase( Locale.ROOT ) );
		if ( value instanceof CharSequence ) {
			m.put( "text", sanitizer().text( value ) );
		} else {
			m.put( "text", "" );
			m.put( "data", sanitizer().clean( value ) );
		}
		m.put( "at", Span.ms( req.now() ) );
		req.messages.add( m );
		return null;
	}

}
