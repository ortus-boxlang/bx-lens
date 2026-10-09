import { test, expect } from './lens';

const ASSETS = '/~bxlens/index.bxm/assets';

test.describe( 'bar files', () => {

	test( 'the injected block is small and holds no styles, script or markup of the bar', async ( { request } ) => {
		const text = await ( await request.get( '/orders.bxm' ) ).text();
		const block = text.slice( text.indexOf( '<!-- BX Lens -->' ), text.indexOf( '<!-- /BX Lens -->' ) );
		expect( block ).toContain( '<link rel="stylesheet" id="bxlens-css" href="/~bxlens/index.bxm/assets/lens.css?v=' );
		expect( block ).toContain( '<script defer id="bxlens-js" src="/~bxlens/index.bxm/assets/lens.js?v=' );
		expect( block ).not.toContain( '<style' );
		expect( block ).not.toContain( 'bxLens' );
		// The data of the request is the only large part
		const data = block.slice( block.indexOf( 'id="bxlens-data">' ) );
		expect( block.length - data.length ).toBeLessThan( 1100 );
	} );

	// The harness reloads the UI files on the first server (dev.reloadAssets), so nothing there is immutable
	test( 'with dev.reloadAssets a file is never cached by the browser', async ( { request } ) => {
		const page = await ( await request.get( '/orders.bxm' ) ).text();
		const href = /href="([^"]*lens\.css\?v=[0-9a-f]+)"/.exec( page )![ 1 ];
		expect( ( await request.get( href ) ).headers()[ 'cache-control' ] ).toBe( 'no-cache' );
	} );

	test( 'a URL without the current hash is revalidated, not kept', async ( { request } ) => {
		const r = await request.get( `${ ASSETS }/lens.js?v=stale` );
		expect( r.status() ).toBe( 200 );
		expect( r.headers()[ 'cache-control' ] ).toBe( 'no-cache' );
		expect( r.headers()[ 'content-type' ] ).toContain( 'text/javascript' );
		expect( ( await request.get( `${ ASSETS }/lens.js` ) ).headers()[ 'cache-control' ] ).toBe( 'no-cache' );
	} );

	test( 'only the files of the bar are served this way, and the console files keep console.access', async ( { request } ) => {
		expect( ( await request.get( `${ ASSETS }/ModuleConfig.bx`, { failOnStatusCode: false } ) ).status() ).toBe( 404 );
		expect( ( await request.get( `${ ASSETS }/..%2FModuleConfig.bx`, { failOnStatusCode: false } ) ).status() ).toBe( 404 );
		expect( ( await request.get( `${ ASSETS }/login.html`, { failOnStatusCode: false } ) ).status() ).toBe( 404 );
		// A caller console.access does not allow (a public address behind the trusted local proxy) gets the bar files but not the console
		const outsider = { 'X-Forwarded-For': '203.0.113.9' };
		expect( ( await request.get( `${ ASSETS }/lens.js`, { headers: outsider } ) ).status() ).toBe( 200 );
		expect( ( await request.get( `${ ASSETS }/console.js`, { headers: outsider, failOnStatusCode: false } ) ).status() ).toBe( 404 );
		expect( ( await request.get( '/~bxlens/index.bxm', { headers: outsider, failOnStatusCode: false } ) ).status() ).toBe( 404 );
		expect( ( await request.get( `${ ASSETS }/console.js` ) ).status() ).toBe( 200 );
	} );

	test( 'the files hold no request data and no secret', async ( { request } ) => {
		for ( const f of [ 'lens.css', 'lens.js', 'lens.html', 'bar-icons.svg', 'alpine.min.js' ] ) {
			const text = await ( await request.get( `${ ASSETS }/${ f }` ) ).text();
			expect( text, f ).not.toContain( 'lens-demo' );
			expect( text, f ).not.toContain( 'lens-view' );
			expect( text, f ).not.toContain( 'bxlens-data' + '">{' );
		}
	} );

	test( 'without a host Alpine the bar brings its own, and it works', async ( { lens, page } ) => {
		const seen: string[] = [];
		page.on( 'request', r => seen.push( r.url() ) );
		await lens.visit( '/orders.bxm' );
		expect( seen.some( u => u.includes( '/assets/alpine.min.js' ) ) ).toBe( true );
		await lens.open( 'Queries' );
		await expect( lens.panel ).toContainText( 'orders' );
	} );

	test( 'with a host Alpine the bar uses it: Lens loads no second copy and both work', async ( { lens, page } ) => {
		const seen: string[] = [];
		page.on( 'request', r => seen.push( r.url() ) );
		await page.goto( '/with-alpine.bxm' );
		await expect( lens.bar ).toBeVisible();
		expect( seen.some( u => u.includes( '/vendor/alpine.min.js' ) ) ).toBe( true );
		expect( seen.some( u => u.includes( '/assets/alpine.min.js' ) ) ).toBe( false );
		await page.click( '#hostbtn' );
		await expect( page.locator( '#hostn' ) ).toHaveText( '2' );
		await lens.open( 'Request' );
		await expect( lens.panel ).toContainText( 'with-alpine.bxm' );
	} );

	test( 'the bar shows no flash of unstyled content: the empty element is hidden until it is ready', async ( { page } ) => {
		await page.route( '**/assets/lens.html*', async route => { await new Promise( r => setTimeout( r, 400 ) ); await route.continue(); } );
		await page.goto( '/orders.bxm' );
		await expect( page.locator( '#bxlens' ) ).toBeHidden();
		await expect( page.locator( '#bxlens .lens' ) ).toBeVisible();
	} );

} );

// The second server serves the UI files like production
test.describe( 'bar files in production mode', () => {

	test.use( { baseURL: `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }` } );

	test( 'a file with its hash is cached for a year, with an ETag, and answers 304', async ( { request } ) => {
		const page = await ( await request.get( '/orders.bxm' ) ).text();
		const href = /href="([^"]*lens\.css\?v=[0-9a-f]+)"/.exec( page )![ 1 ];
		const r = await request.get( href );
		expect( r.status() ).toBe( 200 );
		expect( r.headers()[ 'cache-control' ] ).toBe( 'public, max-age=31536000, immutable' );
		expect( r.headers()[ 'content-type' ] ).toContain( 'text/css' );
		expect( r.headers()[ 'x-content-type-options' ] ).toBe( 'nosniff' );
		const etag = r.headers()[ 'etag' ];
		expect( etag ).toMatch( /^"[0-9a-f]{12}"$/ );
		expect( ( await r.text() ).length ).toBeGreaterThan( 1000 );
		const again = await request.get( href, { headers: { 'If-None-Match': etag } } );
		expect( again.status() ).toBe( 304 );
	} );

	test( 'a URL without the current hash is revalidated, not kept', async ( { request } ) => {
		const r = await request.get( `${ ASSETS }/lens.js?v=stale` );
		expect( r.status() ).toBe( 200 );
		expect( r.headers()[ 'cache-control' ] ).toBe( 'no-cache' );
	} );

} );
