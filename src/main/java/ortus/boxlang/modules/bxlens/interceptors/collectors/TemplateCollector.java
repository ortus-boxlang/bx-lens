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
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.modules.bxlens.model.LensRequest;
import ortus.boxlang.modules.bxlens.model.Span;
import ortus.boxlang.modules.bxlens.util.Keys;
import ortus.boxlang.runtime.events.InterceptionPoint;
import ortus.boxlang.runtime.util.ResolvedFilePath;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Records every template and include as a nested span on the waterfall.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class TemplateCollector extends BaseCollector {

	@Override
	public String id() {
		return "templates";
	}

	@InterceptionPoint
	public void preTemplateInvoke( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			String	path	= pathOf( event );
			Span	span	= req.begin( Span.TEMPLATE, path, config().collectorInt( id(), "max", 300 ) );
			if ( span != null ) {
				span.file	= path;
				span.line	= 1;
			}
		} catch ( Throwable t ) {
			fail( "preTemplateInvoke", t );
		}
	}

	@InterceptionPoint
	public void postTemplateInvoke( IStruct event ) {
		try {
			LensRequest req = request( event );
			if ( req == null ) {
				return;
			}
			req.end( req.open( Span.TEMPLATE, pathOf( event ) ) );
		} catch ( Throwable t ) {
			fail( "postTemplateInvoke", t );
		}
	}

	/**
	 * Absolute path of the template. Core sends a ResolvedFilePath record, whose toString lists every part.
	 */
	private String pathOf( IStruct event ) {
		Object tp = event.get( Keys.templatePath );
		if ( tp instanceof ResolvedFilePath r && r.absolutePath() != null ) {
			return r.absolutePath().toString();
		}
		return String.valueOf( tp );
	}

}
