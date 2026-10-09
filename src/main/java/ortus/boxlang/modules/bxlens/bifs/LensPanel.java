/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 */
package ortus.boxlang.modules.bxlens.bifs;

import ortus.boxlang.modules.bxlens.ext.LensPanelBuilder;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * Creates or fetches a custom panel for this request and returns a builder to fill it.
 * <p>
 * Example: <code>lensPanel( "orm", "ORM" ).columns( [ "Entity", "ms" ] ).rows( rows ).badge( rows.len(), "none" )</code>
 * <p>
 * When the request is not tracked, the builder is detached and does nothing, so your code never needs an if.
 *
 * @argument.id Unique panel id
 *
 * @argument.label Tab title. Defaults to the id.
 *
 * @argument.renderer table, kv, tree, spans, messages, json or text. Defaults to table.
 */
@BoxBIF
public class LensPanel extends BaseLensBIF {

	private static final Key	ID			= Key.of( "id" );
	private static final Key	LABEL		= Key.of( "label" );
	private static final Key	RENDERER	= Key.of( "renderer" );

	public LensPanel() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, ID ),
		    new Argument( false, Argument.STRING, LABEL, "" ),
		    new Argument( false, Argument.STRING, RENDERER, "table" )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		LensRequest	req			= request( context );
		String		id			= arguments.getAsString( ID );
		String		label		= arguments.getAsString( LABEL );
		String		renderer	= arguments.getAsString( RENDERER );
		return req == null ? new LensPanelBuilder( id, label, renderer ) : req.panel( id, label, renderer );
	}

}
