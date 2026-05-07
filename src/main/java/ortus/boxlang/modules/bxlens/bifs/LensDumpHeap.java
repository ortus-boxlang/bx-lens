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
 */
package ortus.boxlang.modules.bxlens.bifs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import ortus.boxlang.modules.bxlens.LensService;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;

/**
 * LensDumpHeap( [outputPath] ) — Dump the JVM heap to a .hprof file.
 */
@BoxBIF
public class LensDumpHeap extends BaseLensBIF {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern( "yyyyMMddHHmmss" );

	public LensDumpHeap() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( false, Argument.STRING, Key.of( "outputPath" ), "" )
		};
	}

	@Override
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String outputPath = arguments.getAsString( Key.of( "outputPath" ) );

		if ( outputPath == null || outputPath.trim().isEmpty() ) {
			String tmpDir = System.getProperty( "java.io.tmpdir" );
			if ( !tmpDir.endsWith( java.io.File.separator ) ) {
				tmpDir = tmpDir + java.io.File.separator;
			}
			String timestamp = LocalDateTime.now().format( FORMATTER );
			outputPath = tmpDir + "bxlens-heap-" + timestamp + ".hprof";
		}

		LensService svc = getLensService();
		if ( svc == null ) throw new RuntimeException( "LensDumpHeap: LensService is not available" );

		try {
			return svc.dumpHeap( outputPath );
		} catch ( Exception e ) {
			throw new RuntimeException( "LensDumpHeap failed: " + e.getMessage(), e );
		}
	}

}
