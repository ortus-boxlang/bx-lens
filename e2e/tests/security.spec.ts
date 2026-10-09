import { test, expect } from './lens';

test.describe( 'safety', () => {

	test( 'markup sent to Lens is shown as text and never runs', async ( { lens, page } ) => {
		await lens.visit( '/xss.bxm' );
		await lens.open( 'Messages' );
		await expect( lens.panel ).toContainText( '<img src=x onerror=window.__lensPwned=1>' );
		await lens.open( 'Queries' );
		await expect( lens.panel ).toContainText( '<img src=x onerror=window.__lensPwned=4>' );
		expect( await page.evaluate( () => ( window as any ).__lensPwned ) ).toBeUndefined();
		expect( await page.locator( '#bxlens img' ).count() ).toBe( 0 );
	} );

	test( 'a closing script tag in the data cannot break out of the data element', async ( { lens, page } ) => {
		await lens.visit( '/xss.bxm' );
		const raw = await page.evaluate( () => document.getElementById( 'bxlens-data' )!.textContent! );
		expect( raw ).not.toContain( '</script' );
		expect( raw ).not.toContain( '<' );
		expect( await page.evaluate( () => ( window as any ).__lensPwned ) ).toBeUndefined();
	} );

	test( 'Lens leaves non HTML responses untouched', async ( { request } ) => {
		for ( const path of [ '/api/orders.json.bxm', '/api/rates.json.bxm', '/events.bxm' ] ) {
			const res = await request.get( path );
			expect( await res.text() ).not.toMatch( /bxlens|BX Lens/ );
		}
	} );

	test( 'the bar adds exactly one root element and requests only its own same-origin files', async ( { lens, page } ) => {
		const requests: string[] = [];
		page.on( 'request', ( r ) => requests.push( r.url() ) );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Timeline' );
		expect( await page.locator( '#bxlens' ).count() ).toBe( 1 );
		const others = requests.filter( ( u ) => !u.includes( '/orders.bxm' ) && !u.includes( 'favicon' ) );
		// Only the five files of the bar, from the same server, with their hash: nothing external, nothing else
		for ( const u of others ) {
			expect( u ).toMatch( /^http:\/\/127\.0\.0\.1:\d+\/~bxlens\/index\.bxm\/assets\/(lens\.css|lens\.js|lens\.html|bar-icons\.svg|alpine\.min\.js)\?v=[0-9a-f]{12}$/ );
		}
		expect( others.length ).toBe( 5 );
	} );

} );
