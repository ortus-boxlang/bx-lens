import { test, expect } from '@playwright/test';

test.describe( 'Lens data BIFs', () => {

	test( 'lensReport, lensErrors, lensQueries, lensInflight and lensLicense return data', async ( { request } ) => {
		await request.get( '/n-plus-one.bxm' );
		await request.get( '/caught-exception.bxm' );
		const r = await request.get( '/api/lens.json.bxm' );
		expect( r.status() ).toBe( 200 );
		const j = await r.json();
		expect( j.report.session.requests ).toBeGreaterThan( 0 );
		expect( Array.isArray( j.report.series ) ).toBe( true );
		expect( j.errors.length ).toBeGreaterThan( 0 );
		expect( j.errors.length ).toBeLessThanOrEqual( 5 );
		expect( j.errors[ 0 ] ).toHaveProperty( 'message' );
		expect( j.queries.length ).toBeGreaterThan( 0 );
		expect( j.queries.length ).toBeLessThanOrEqual( 3 );
		expect( j.queries[ 0 ] ).toHaveProperty( 'totalMs' );
		expect( j.queries[ 0 ].totalMs ).toBeGreaterThanOrEqual( j.queries[ j.queries.length - 1 ].totalMs );
		// This very request is running while the BIF is called
		expect( j.inflight.some( ( x: any ) => x.uri === '/api/lens.json.bxm' ) ).toBe( true );
		expect( [ 'plus', 'trial', 'expired', 'none' ] ).toContain( j.license.state );
		expect( j.diagnostics.async.enabled ).toBe( true );
		expect( j.diagnostics.async.capacity ).toBe( 2000 );
		expect( j.diagnostics.async.dropped ).toBe( 0 );
		expect( j.diagnostics.history.capacity ).toBeGreaterThan( 0 );
	} );

} );
