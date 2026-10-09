import { test, expect, consoleApi } from './lens';

// The history belongs to the console. The bar shows the request it belongs to and nothing more.
test.describe( 'history', () => {

	test( 'JSON and SSE requests are recorded without getting a bar, and carry the request id header', async ( { request } ) => {
		const json = await request.get( '/api/orders.json.bxm' );
		expect( json.headers()[ 'x-bxlens-id' ] ).toMatch( /^[0-9a-z]{9,16}$/ );
		expect( await json.text() ).not.toContain( 'bxlens' );
		JSON.parse( await json.text() );
		const sse = await request.get( '/events.bxm' );
		expect( sse.headers()[ 'content-type' ] ).toContain( 'text/event-stream' );
		expect( sse.headers()[ 'x-bxlens-id' ] ).toBeTruthy();
		expect( await sse.text() ).not.toContain( 'bxlens' );
		const api = await consoleApi( request );
		const rows = ( await api.get( 'requests' ) ).json.requests;
		const jsonRow = rows.find( ( r: any ) => r.id === json.headers()[ 'x-bxlens-id' ] );
		expect( jsonRow.type ).toBe( 'json' );
		expect( jsonRow.status ).toBe( 200 );
		const sseRow = rows.find( ( r: any ) => r.id === sse.headers()[ 'x-bxlens-id' ] );
		expect( sseRow.type ).toBe( 'sse' );
	} );

	test( 'the bar has no history tab and its payload carries no list of other requests', async ( { lens, page } ) => {
		await lens.visit( '/cache.bxm' );
		const d = await lens.data();
		expect( d.history ).toBeUndefined();
		await page.keyboard.press( 'Control+`' );
		await expect( lens.tab( 'History' ) ).toHaveCount( 0 );
	} );

	test( 'history is a ring buffer of 50 and recycles the oldest', async ( { request } ) => {
		const ids: string[] = [];
		for ( let i = 0; i < 60; i++ ) {
			const r = await request.get( `/api/rates.json.bxm?n=${ i }` );
			ids.push( r.headers()[ 'x-bxlens-id' ] );
		}
		const api = await consoleApi( request );
		const list = ( await api.get( 'requests' ) ).json;
		expect( list.requests.length ).toBe( 50 );
		expect( list.capacity ).toBe( 50 );
		const seen = new Set( list.requests.map( ( r: any ) => r.id ) );
		expect( seen.has( ids[ 0 ] ) ).toBe( false );
		expect( seen.has( ids[ 59 ] ) ).toBe( true );
		expect( ( await api.get( `requests/${ ids[ 0 ] }` ) ).status ).toBe( 404 );
	} );

	test( 'requests with issues are marked in the console list', async ( { request } ) => {
		const r = await request.get( '/n-plus-one.bxm' );
		const id = r.headers()[ 'x-bxlens-id' ];
		const api = await consoleApi( request );
		const row = ( await api.get( 'requests' ) ).json.requests.find( ( x: any ) => x.id === id );
		expect( row.issues ).toBeGreaterThan( 0 );
		expect( row.severity ).toBe( 'warn' );
	} );

	test( 'the id header is the same id the request has in the bar, the response and the console', async ( { lens, page, request } ) => {
		const res = await page.goto( '/orders.bxm' );
		const id = res!.headers()[ 'x-bxlens-id' ];
		await expect( lens.bar ).toBeVisible();
		expect( ( await lens.data() ).data.request.id ).toBe( id );
		const api = await consoleApi( request );
		expect( ( await api.get( `requests/${ id }` ) ).json.request.id ).toBe( id );
	} );

} );
