import { test, expect, consoleApi } from './lens';

// What core fires, seen from the harness pages in harness/app/spans (see docs/reference/events.md):
//   uncaught or caught exception in a function: onFunctionException, but no postFunctionInvoke  -> the span is interrupted
//   exception through an include, caught by the page: postTemplateInvoke still fires, no exception event
//   abort: the request ends normally. Request timeout: not enforced. Client disconnect: the request runs to the end, no event
//   async thread: no event for its work after the request ended

async function detail( request: any, id: string ) {
	const api = await consoleApi( request );
	return ( await api.get( `requests/${ id }` ) ).json;
}

test.describe( 'spans that never close', () => {

	test( 'a function the exception went through is closed at the exception, flagged and drawn as interrupted', async ( { lens, page } ) => {
		await lens.visit( '/spans/caught-func.bxm' );
		const d = ( await lens.data() ).data;
		const page_ = d.spans.find( ( s: any ) => s.label.endsWith( 'caught-func.bxm' ) );
		const risky = d.spans.find( ( s: any ) => s.label === 'risky()' );
		expect( risky.interrupted ).toBe( true );
		expect( page_.interrupted ).toBeUndefined();
		// It ends at the exception (after its 30 ms of work), not when the page ended (over 100 ms later)
		expect( risky.dur ).toBeLessThan( 60 );
		expect( page_.dur ).toBeGreaterThan( 100 );
		await lens.open( 'Timeline' );
		const row = page.locator( '#bxlens .wf .r', { hasText: 'risky()' } );
		await expect( row.locator( '.flag:visible', { hasText: 'interrupted' } ) ).toBeVisible();
		await expect( row.locator( '.seg.cut' ) ).toBeVisible();
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'header.bxm' } ).locator( '.flag:visible' ) ).toHaveCount( 0 );
	} );

	test( 'an uncaught exception in a function shows in the console with the interrupted marker', async ( { request } ) => {
		const res = await request.get( '/spans/uncaught.bxm', { failOnStatusCode: false } );
		expect( res.status() ).toBe( 500 );
		const d = await detail( request, res.headers()[ 'x-bxlens-id' ] );
		const inner = d.spans.find( ( s: any ) => s.label === 'inner()' );
		expect( inner.interrupted ).toBe( true );
		expect( inner.dur ).toBeGreaterThan( 15 );
		expect( d.spans.find( ( s: any ) => s.label.endsWith( 'uncaught.bxm' ) ).interrupted ).toBeUndefined();
	} );

	test( 'normal requests, an include that throws into a try/catch and an abort have no interrupted span', async ( { request } ) => {
		for ( const p of [ '/orders.bxm', '/n-plus-one.bxm', '/spans/trycatch.bxm', '/spans/abort.bxm' ] ) {
			const res = await request.get( p );
			const d = await detail( request, res.headers()[ 'x-bxlens-id' ] );
			expect( d.spans.length, p ).toBeGreaterThan( 0 );
			expect( d.spans.filter( ( s: any ) => s.interrupted ), p ).toEqual( [] );
		}
	} );

	test( 'an include that throws into a try/catch keeps its end and records no exception event', async ( { request } ) => {
		const res = await request.get( '/spans/trycatch.bxm' );
		expect( await res.text() ).toContain( 'caught: Thrown inside an include' );
		const d = await detail( request, res.headers()[ 'x-bxlens-id' ] );
		const boom = d.spans.find( ( s: any ) => s.label.endsWith( 'boom.bxm' ) );
		expect( boom.dur ).toBeGreaterThan( 15 );
		expect( d.exceptions ).toEqual( [] );
	} );

	test( 'core has no event for a request timeout or a hung up client: both requests run to their end', async ( { request } ) => {
		const t = await request.get( '/spans/timeout.bxm' );
		expect( await t.text() ).toContain( 'finished after the timeout' );
		const td = await detail( request, t.headers()[ 'x-bxlens-id' ] );
		expect( td.request.durationMs ).toBeGreaterThan( 2400 );
		expect( td.spans.filter( ( s: any ) => s.interrupted ) ).toEqual( [] );
		// A client that hangs up in the middle of a slow response
		const net = await import( 'net' );
		const port = new URL( process.env.BASE_URL || 'http://127.0.0.1:8085' ).port || '8085';
		await new Promise<void>( ( resolve ) => {
			const sock = net.connect( +port, '127.0.0.1', () => sock.write( 'GET /spans/stream.bxm HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' ) );
			sock.once( 'data', () => { sock.destroy(); resolve(); } );
			sock.on( 'error', () => resolve() );
		} );
		const api = await consoleApi( request );
		await expect.poll( async () => {
			const rows = ( await api.get( 'requests' ) ).json.requests.filter( ( r: any ) => r.url.includes( 'stream.bxm' ) );
			return rows.length ? rows[ 0 ].ms : 0;
		}, { timeout: 10_000 } ).toBeGreaterThan( 3000 );
	} );

	test( 'work in a thread that outlives the request adds no span and no error to it', async ( { request } ) => {
		const res = await request.get( '/spans/async.bxm' );
		const id = res.headers()[ 'x-bxlens-id' ];
		await new Promise( r => setTimeout( r, 2200 ) );
		const d = await detail( request, id );
		expect( d.spans.filter( ( s: any ) => s.interrupted ) ).toEqual( [] );
		expect( d.queries.length ).toBe( 1 );
		expect( d.exceptions ).toEqual( [] );
	} );

	test( 'the watchdog finishes a request that runs past request.maxMinutes as unfinished', async ( { request } ) => {
		test.setTimeout( 150_000 );
		request.get( '/spans/hang.bxm', { timeout: 4000 } ).catch( () => undefined );
		const api = await consoleApi( request );
		await expect.poll( async () => {
			const row = ( await api.get( 'requests' ) ).json.requests.find( ( r: any ) => r.url.includes( 'hang.bxm' ) );
			return row ? row.state : '';
		}, { timeout: 120_000, intervals: [ 3000 ] } ).toBe( 'unfinished' );
		const row = ( await api.get( 'requests' ) ).json.requests.find( ( r: any ) => r.url.includes( 'hang.bxm' ) );
		expect( row.status ).toBe( 0 );
		expect( row.issues ).toBeGreaterThan( 0 );
		const d = ( await api.get( `requests/${ row.id }` ) ).json;
		expect( d.request.state ).toBe( 'unfinished' );
		expect( d.issues.map( ( i: any ) => i.title ) ).toContain( 'Request never finished' );
		const tpl = d.spans.find( ( s: any ) => s.label.endsWith( 'hang.bxm' ) );
		expect( tpl.interrupted ).toBe( true );
		// It no longer counts as running
		const inflight = ( await api.get( 'inflight' ) ).json.requests;
		expect( inflight.find( ( r: any ) => r.id === row.id ) ).toBeUndefined();
	} );

} );
