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
package ortus.boxlang.modules.bxlens.util;

import ortus.boxlang.runtime.scopes.Key;

/**
 * Central registry of Key constants used by bx-lens. Keys are created once and reused on hot paths.
 */
public final class Keys {

	private Keys() {
	}

	// Module identity
	public static final Key	moduleName			= Key.of( "bxLens" );
	public static final Key	requestAttach		= Key.of( "__bxLensRequest__" );

	// Event payload keys sent by BoxLang core
	public static final Key	context				= Key.of( "context" );
	public static final Key	args				= Key.of( "args" );
	public static final Key	templatePath		= Key.of( "templatePath" );
	public static final Key	function			= Key.of( "function" );
	public static final Key	name				= Key.of( "name" );
	public static final Key	arguments			= Key.of( "arguments" );
	public static final Key	exception			= Key.of( "exception" );
	public static final Key	sql					= Key.of( "sql" );
	public static final Key	bindings			= Key.of( "bindings" );
	public static final Key	pendingQuery		= Key.of( "pendingQuery" );
	public static final Key	executedQuery		= Key.of( "executedQuery" );
	public static final Key	executionTime		= Key.of( "executionTime" );
	public static final Key	data				= Key.of( "data" );
	public static final Key	result				= Key.of( "result" );
	public static final Key	response			= Key.of( "response" );
	public static final Key	httpRequest			= Key.of( "httpRequest" );
	public static final Key	text				= Key.of( "text" );
	public static final Key	log					= Key.of( "log" );
	public static final Key	type				= Key.of( "type" );
	public static final Key	bif					= Key.of( "bif" );
	public static final Key	transaction			= Key.of( "transaction" );

	// Custom interception points announced by Lens for extension modules
	public static final Key	onLensRegister		= Key.of( "onLensRegister" );
	public static final Key	onLensRequestStart	= Key.of( "onLensRequestStart" );
	public static final Key	onLensCollect		= Key.of( "onLensCollect" );
	public static final Key	onLensRequestFinish	= Key.of( "onLensRequestFinish" );

}
