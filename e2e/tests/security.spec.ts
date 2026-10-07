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

	test( 'the bar adds exactly one root element and no extra network requests', async ( { lens, page } ) => {
		const requests: string[] = [];
		page.on( 'request', ( r ) => requests.push( r.url() ) );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Timeline' );
		expect( await page.locator( '#bxlens' ).count() ).toBe( 1 );
		expect( requests.filter( ( u ) => !u.includes( '/orders.bxm' ) && !u.includes( 'favicon' ) ) ).toEqual( [] );
	} );

} );
