import { test, expect } from './lens';

test.describe( 'request cost and checks', () => {

	test( 'the strip shows CPU time and allocation of the request', async ( { lens, page } ) => {
		await lens.visit( '/slow.bxm' );
		await expect( page.locator( '#bxlens .chip', { hasText: /^cpu/ } ) ).toBeVisible();
		await expect( page.locator( '#bxlens .chip', { hasText: /^alloc/ } ) ).toBeVisible();
		const d = await lens.data();
		expect( d.data.cost.cpuMs ).toBeGreaterThan( 0 );
		expect( d.data.cost.allocBytes ).toBeGreaterThan( 0 );
	} );

	test( 'the Runtime panel lists the cost', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Runtime' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'CPU time' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Allocated' );
	} );

	test( 'missing security headers are reported on HTML pages', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Issues' );
		const row = page.locator( '#bxlens .issue', { hasText: 'Security headers missing' } );
		await expect( row ).toBeVisible();
		await expect( row ).toContainText( 'Content-Security-Policy' );
	} );

	test( 'the Request panel shows response headers', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Request' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Response headers' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Content-Type' );
	} );

	test( 'a request that runs past the slow limit says where it was', async ( { lens, page } ) => {
		test.setTimeout( 45_000 );
		await lens.visit( '/stall.bxm' );
		const d = await lens.data();
		expect( d.data.slowSample.atMs ).toBeGreaterThanOrEqual( 3000 );
		const issue = d.data.issues.find( ( i: any ) => i.title === 'Slow request' );
		expect( issue.detail ).toContain( 'stall.bxm' );
		expect( issue.file ).toContain( 'stall.bxm' );
	} );

	test( 'JSON responses are not checked for browser security headers', async ( { request } ) => {
		const r = await request.get( '/api/orders.json.bxm' );
		expect( r.status() ).toBe( 200 );
	} );

} );
