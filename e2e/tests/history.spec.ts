import { test, expect } from './lens';

test.describe( 'history', () => {

	test( 'JSON and SSE requests are recorded without getting a bar', async ( { lens, page, request } ) => {
		const json = await request.get( '/api/orders.json.bxm' );
		expect( json.headers()[ 'x-bxlens-id' ] ).toMatch( /^[0-9a-z]{9,16}$/ );
		expect( await json.text() ).not.toContain( 'bxlens' );
		JSON.parse( await json.text() );
		const sse = await request.get( '/events.bxm' );
		expect( sse.headers()[ 'content-type' ] ).toContain( 'text/event-stream' );
		expect( sse.headers()[ 'x-bxlens-id' ] ).toBeTruthy();
		expect( await sse.text() ).not.toContain( 'bxlens' );

		await lens.visit( '/orders.bxm' );
		await lens.open( 'History' );
		const jsonRow = lens.rows.filter( { hasText: '/api/orders.json.bxm' } ).first();
		await expect( jsonRow.locator( '.pill', { hasText: 'json' } ) ).toBeVisible();
		await expect( jsonRow.locator( 'td.num' ).first() ).toHaveText( '200' );
		await expect( page.locator( '#bxlens tbody tr', { hasText: '/events.bxm' } ).first().locator( '.pill', { hasText: 'sse' } ) ).toBeVisible();
	} );

	test( 'the current request is marked and listed first', async ( { lens, page } ) => {
		await lens.visit( '/cache.bxm' );
		await lens.open( 'History' );
		const first = lens.rows.first();
		await expect( first ).toContainText( '/cache.bxm' );
		await expect( first.locator( '.pill', { hasText: 'current' } ) ).toBeVisible();
	} );

	test( 'history is a ring buffer of 50 and recycles the oldest', async ( { lens, page, request } ) => {
		for ( let i = 0; i < 60; i++ ) {
			await request.get( `/api/rates.json.bxm?n=${ i }` );
		}
		await lens.visit( '/orders.bxm' );
		await lens.open( 'History' );
		await expect( lens.rows ).toHaveCount( 50 );
		await expect( lens.panel ).toContainText( '50 of 50' );
		// The oldest of the burst has been recycled and the newest is still there
		await expect( lens.rows.filter( { hasText: /n=0(?!\d)/ } ) ).toHaveCount( 0 );
		await expect( lens.rows.filter( { hasText: /n=59(?!\d)/ } ) ).toHaveCount( 1 );
	} );

	test( 'requests with issues are marked in the list', async ( { lens, page, request } ) => {
		await request.get( '/n-plus-one.bxm' );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'History' );
		const row = lens.rows.filter( { hasText: '/n-plus-one.bxm' } ).first();
		await expect( row.locator( '.pill.warn' ) ).toHaveText( '1' );
	} );

} );
