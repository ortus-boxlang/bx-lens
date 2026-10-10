import { test, expect, consoleApi } from './lens';
import { Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';
const FREE = `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }`;

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

test.describe( 'Server identity', () => {

	test( 'the state names the server and the console header shows its host and address', async ( { page, request } ) => {
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		expect( st.server.host ).toBeTruthy();
		expect( st.server.ip ).toBeTruthy();
		expect( st.server.id ).toMatch( /^[0-9a-f]{8}$/ );
		expect( Array.isArray( st.server.addresses ) ).toBe( true );
		await signIn( page );
		await expect( page.locator( '#serverinfo' ) ).toContainText( st.server.host );
		await expect( page.locator( '#serverinfo' ) ).toContainText( st.server.ip );
		await expect( page.locator( '#overview-server' ) ).toContainText( st.server.host );
	} );

	test( 'a tracked request, its summary and its detail carry the server', async ( { request } ) => {
		const r = await request.get( '/index.bxm' );
		const id = r.headers()[ 'x-bxlens-id' ];
		// The server header is off unless history.serverHeader is true
		expect( r.headers()[ 'x-bxlens-server' ] ).toBeUndefined();
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		const list = ( await api.get( 'requests' ) ).json;
		expect( list.servers ).toBe( 1 );
		expect( list.server.id ).toBe( st.server.id );
		const row = list.requests.find( ( x: any ) => x.id === id );
		expect( row.serverId ).toBe( st.server.id );
		expect( row.serverHost ).toBe( st.server.host );
		expect( row.serverIp ).toBe( st.server.ip );
		const detail = ( await api.get( 'requests/' + id ) ).json;
		expect( detail.request.serverId ).toBe( st.server.id );
		expect( detail.request.serverHost ).toBe( st.server.host );
		expect( detail.request.serverIp ).toBe( st.server.ip );
	} );

	test( 'the Requests table has no Server column for one server and the detail shows the server', async ( { page, request } ) => {
		await request.get( '/orders.bxm' );
		await signIn( page );
		await page.click( '.nav:has-text("Requests")' );
		await expect( page.locator( '.main section:visible tr.row' ).first() ).toBeVisible();
		await expect( page.locator( '#server-col' ) ).toBeHidden();
		await page.locator( '.main section:visible tr.row' ).first().click();
		const st = await page.evaluate( async () => ( await ( await fetch( '/~bxlens/index.bxm/api/state' ) ).json() ).server );
		await expect( page.locator( '#detail-server' ) ).toContainText( st.host );
		await expect( page.locator( '#detail-server' ) ).toContainText( st.ip );
		await expect( page.locator( '#detail-server' ) ).toContainText( st.id );
	} );

	test( 'the Server column appears when the store has seen more than one server', async ( { page, request } ) => {
		await request.get( '/orders.bxm' );
		await page.route( '**/api/requests', async route => {
			const res = await route.fetch();
			const j = await res.json();
			j.servers = 2;
			j.requests = j.requests.map( ( r: any, i: number ) => ( i % 2 ? { ...r, serverHost: 'web-2', serverId: 'bbbb2222' } : r ) );
			await route.fulfill( { response: res, json: j } );
		} );
		await signIn( page );
		await page.click( '.nav:has-text("Requests")' );
		await expect( page.locator( '#server-col' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible tbody tr.row' ).first() ).toContainText( /\w+ [0-9a-f]{8}/ );
	} );

	test( 'the Request tab of the bar shows the host and the address', async ( { lens, request } ) => {
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		await lens.visit( '/index.bxm' );
		await lens.open( 'Request' );
		const row = lens.page.locator( '#bxlens .bx-server' );
		await expect( row ).toContainText( st.server.host );
		await expect( row ).toContainText( st.server.ip );
		await expect( row ).toContainText( st.server.id );
		const d = await lens.data();
		expect( d.data.request.serverId ).toBe( st.server.id );
	} );

	test( 'errors, reports and queries say which server they describe', async ( { request } ) => {
		await request.get( '/caught-exception.bxm' );
		await request.get( '/orders.bxm' );
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		const errors = ( await api.get( 'errors' ) ).json;
		expect( errors.server.id ).toBe( st.server.id );
		expect( errors.groups.length ).toBeGreaterThan( 0 );
		expect( errors.groups[ 0 ].serverId ).toBe( st.server.id );
		const g = ( await api.get( 'errors/' + errors.groups[ 0 ].id ) ).json;
		expect( g.samples[ 0 ].serverId ).toBe( st.server.id );
		expect( g.samples[ 0 ].serverHost ).toBe( st.server.host );
		expect( ( await api.get( 'reports' ) ).json.server.id ).toBe( st.server.id );
		const q = ( await api.get( 'queries' ) ).json;
		expect( q.server.id ).toBe( st.server.id );
		expect( q.statements[ 0 ].serverId ).toBe( st.server.id );
		expect( ( await api.get( 'overview' ) ).json.server.id ).toBe( st.server.id );
	} );

	test( 'a running request lists the server too', async ( { request } ) => {
		const slow = request.get( '/stall.bxm?ms=1500', { failOnStatusCode: false } );
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		let row: any;
		for ( let i = 0; i < 20 && !row; i++ ) {
			await new Promise( r => setTimeout( r, 150 ) );
			row = ( await api.get( 'inflight' ) ).json.requests.find( ( x: any ) => x.uri.includes( 'stall' ) );
		}
		await slow;
		expect( row, 'the stalled request was in flight' ).toBeTruthy();
		expect( row.serverId ).toBe( st.server.id );
	} );

	test( 'every audit line names the server', async ( { request } ) => {
		const api = await consoleApi( request );
		const st = ( await api.get( 'state' ) ).json;
		const log = ( await api.get( 'logfiles/read?file=bxlens-audit.log&lines=20&q=login.ok' ) ).json;
		expect( log.lines.length ).toBeGreaterThan( 0 );
		expect( log.lines[ log.lines.length - 1 ] ).toContain( 'server=' + st.server.id );
	} );

	test( 'lensServer and the data BIFs carry the server', async ( { request } ) => {
		await request.get( '/orders.bxm' );
		await request.get( '/caught-exception.bxm' );
		const j = await ( await request.get( '/api/lens.json.bxm' ) ).json();
		expect( j.server.host ).toBeTruthy();
		expect( j.server.ip ).toBeTruthy();
		expect( j.server.id ).toMatch( /^[0-9a-f]{8}$/ );
		expect( j.report.server.id ).toBe( j.server.id );
		expect( j.diagnostics.server.id ).toBe( j.server.id );
		expect( j.diagnostics.async.server.id ).toBe( j.server.id );
		expect( j.errors[ 0 ].serverId ).toBe( j.server.id );
		expect( j.queries[ 0 ].serverId ).toBe( j.server.id );
		expect( j.inflight.every( ( x: any ) => x.serverId === j.server.id ) ).toBe( true );
	} );

	test( 'the free server uses the environment for its name and address and sends the server header with the id header', async ( { request } ) => {
		const r = await request.get( `${ FREE }/index.bxm` );
		expect( r.headers()[ 'x-bxlens-id' ] ).toBeTruthy();
		const login = await request.post( `${ FREE }${ BASE }/login`, { headers: { 'X-Lens-Login': '1' }, form: { password: 'lens-demo' } } );
		expect( login.status() ).toBe( 200 );
		const st = await ( await request.get( `${ FREE }${ BASE }/api/state` ) ).json();
		expect( st.server.host ).toBe( 'free-node' );
		expect( st.server.ip ).toBe( '192.0.2.55' );
		expect( r.headers()[ 'x-bxlens-server' ] ).toBe( st.server.id );
	} );

} );
