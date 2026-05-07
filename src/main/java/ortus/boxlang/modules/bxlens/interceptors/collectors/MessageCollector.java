/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.bxlens.interceptors.collectors;

import ortus.boxlang.modules.bxlens.LensRequestData;
import ortus.boxlang.modules.bxlens.interceptors.BaseCollector;
import ortus.boxlang.runtime.types.IStruct;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Message collector. Data is pushed via LensMessage BIF.
 * Registered so the messages tab is available in the debug bar.
 */
@ortus.boxlang.runtime.events.Interceptor( autoLoad = false )
public class MessageCollector extends BaseCollector {

	@Override
	public String getName() {
		return "messages";
	}

	@Override
	public void configure( IStruct properties ) {
		// No event-based collection — data is pushed directly by LensMessage BIF
	}

	/**
	 * Helper for adding a message to the request data.
	 * Called directly from LensMessage BIF.
	 */
	public static void addMessage( LensRequestData data, String message, String type ) {
		if ( data == null || !data.enabled )
			return;
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put( "message", message );
		entry.put( "type", type );
		entry.put( "offset", System.currentTimeMillis() - data.startedAt );
		data.messages.add( entry );
	}

}
