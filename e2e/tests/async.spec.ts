import { test, expect, consoleApi } from './lens';

test.describe( 'work off the request thread', () => {

	test( 'the console shows the work queue on Overview and System, with nothing dropped', async ( { request } ) => {
		for ( let i = 0; i < 30; i++ ) {
			await request.get( '/api/rates.json.bxm' );
		}
		const api = await consoleApi( request );
		const overview = ( await api.get( 'overview' ) ).json;
		expect( overview.lens.enabled ).toBe( true );
		expect( overview.lens.capacity ).toBe( 2000 );
		expect( overview.lens.dropped ).toBe( 0 );
		expect( overview.lens.processed ).toBeGreaterThan( 30 );
		const system = ( await api.get( 'system' ) ).json;
		expect( system.lens.depth ).toBeGreaterThanOrEqual( 0 );
		expect( system.lens.failed ).toBe( 0 );
	} );

	test( 'a request that just ended is already in the console history, queries, errors and reports', async ( { request } ) => {
		const api = await consoleApi( request );
		const before = ( await api.get( 'reports' ) ).json.session.requests;
		const ok = await request.get( '/n-plus-one.bxm' );
		const bad = await request.get( '/error.bxm', { failOnStatusCode: false } );
		// No waiting: reading the console waits for the queue first
		const list = ( await api.get( 'requests' ) ).json.requests;
		expect( list.some( ( r: any ) => r.id === ok.headers()[ 'x-bxlens-id' ] ) ).toBe( true );
		expect( list.some( ( r: any ) => r.id === bad.headers()[ 'x-bxlens-id' ] ) ).toBe( true );
		expect( ( await api.get( 'reports' ) ).json.session.requests ).toBe( before + 2 );
		const queries = ( await api.get( 'queries' ) ).json;
		expect( queries.statements.some( ( s: any ) => s.sql.includes( 'FROM orders' ) ) ).toBe( true );
		expect( ( await api.get( 'errors' ) ).json.groups.length ).toBeGreaterThan( 0 );
	} );

	test( 'audit lines of console actions are written by the worker and are in the log when it is read', async ( { request } ) => {
		const api = await consoleApi( request );
		await api.get( 'state' );
		const log = ( await api.get( 'logfiles/read?file=bxlens-audit.log&lines=20&q=login.ok' ) ).json;
		expect( log.lines.length ).toBeGreaterThan( 0 );
		expect( log.lines[ log.lines.length - 1 ] ).toContain( 'event=login.ok' );
	} );

} );
