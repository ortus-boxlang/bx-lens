import { test, expect, consoleApi } from './lens';

test.describe( 'request cost and checks', () => {

	test( 'the request carries CPU time and allocation of the request thread', async ( { lens, page } ) => {
		await lens.visit( '/slow.bxm' );
		const d = await lens.data();
		expect( d.data.cost.cpuMs ).toBeGreaterThan( 0 );
		expect( d.data.cost.allocBytes ).toBeGreaterThan( 0 );
	} );

	test( 'the Request panel lists the cost', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Request' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'CPU time' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Memory allocated' );
	} );

	test( 'missing security headers are reported on HTML pages, in the console only', async ( { lens, page, request } ) => {
		await lens.visit( '/orders.bxm' );
		const d = await lens.data();
		expect( d.data.issues ).toBeUndefined();
		const api = await consoleApi( request );
		const detail = ( await api.get( `requests/${ d.data.request.id }` ) ).json;
		const issue = detail.issues.find( ( i: any ) => i.title === 'Security headers missing' );
		expect( issue ).toBeTruthy();
		expect( issue.detail ).toContain( 'Content-Security-Policy' );
	} );

	test( 'the Request panel shows response headers', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Request' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Response headers' );
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Content-Type' );
	} );

	test( 'a request that runs past the slow limit says where it was', async ( { lens, page, request } ) => {
		test.setTimeout( 45_000 );
		await lens.visit( '/stall.bxm' );
		const d = await lens.data();
		expect( d.data.slowSample.atMs ).toBeGreaterThanOrEqual( 3000 );
		const api = await consoleApi( request );
		const detail = ( await api.get( `requests/${ d.data.request.id }` ) ).json;
		const issue = detail.issues.find( ( i: any ) => i.title === 'Slow request' );
		expect( issue.detail ).toContain( 'stall.bxm' );
		expect( issue.file ).toContain( 'stall.bxm' );
	} );

	test( 'JSON responses are not checked for browser security headers', async ( { request } ) => {
		const r = await request.get( '/api/orders.json.bxm' );
		expect( r.status() ).toBe( 200 );
	} );

} );
