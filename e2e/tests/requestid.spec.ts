import { test, expect, consoleApi } from './lens';

test.describe( 'request id', () => {

	test( 'one id: response header, lensRequestId(), request.bxlens.id and the logging context', async ( { request } ) => {
		const r = await request.get( '/requestid.bxm' );
		const id = r.headers()[ 'x-bxlens-id' ];
		expect( id ).toMatch( /^[0-9a-z]{9,16}$/ );
		const body = JSON.parse( ( await r.text() ).split( '\n' )[ 0 ].replace( /<!-- BX Lens -->.*/, '' ) );
		expect( body.bif ).toBe( id );
		expect( body.scope ).toBe( id );
		// The logging context is set on the request thread. It reads as "unavailable" when the app cannot see the logging classes
		expect( [ id, 'unavailable' ] ).toContain( body.mdc );
	} );

	test( 'every tracked request carries the header, and the id is in the console detail and the error samples', async ( { request } ) => {
		const ok = await request.get( '/api/orders.json.bxm' );
		expect( ok.headers()[ 'x-bxlens-id' ] ).toBeTruthy();
		const bad = await request.get( '/error.bxm', { failOnStatusCode: false } );
		const badId = bad.headers()[ 'x-bxlens-id' ];
		expect( badId ).toBeTruthy();
		const api = await consoleApi( request );
		const errors = ( await api.get( 'errors' ) ).json.groups;
		const group = ( await api.get( `errors/${ errors[ 0 ].id }` ) ).json;
		expect( group.samples.map( ( s: any ) => s.requestId ) ).toContain( badId );
	} );

	test( 'the id header is an unguessable-enough short id, different for every request', async ( { request } ) => {
		const ids = new Set<string>();
		for ( let i = 0; i < 20; i++ ) {
			ids.add( ( await request.get( '/api/rates.json.bxm' ) ).headers()[ 'x-bxlens-id' ] );
		}
		expect( ids.size ).toBe( 20 );
	} );

} );
