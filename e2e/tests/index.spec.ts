import { test, expect } from '@playwright/test';

test.describe( 'console URL', () => {

	test( '/~bxlens/ opens the console login like /~bxlens/index.bxm', async ( { request } ) => {
		for ( const url of [ '/~bxlens/', '/~bxlens', '/~bxlens/index.bxm' ] ) {
			const r = await request.get( url );
			expect( r.status(), url ).toBe( 200 );
			expect( await r.text(), url ).toContain( 'BX Lens Console' );
		}
	} );

	test( 'other paths under the mapping are not exposed', async ( { request } ) => {
		const r = await request.get( '/~bxlens/other', { failOnStatusCode: false } );
		expect( r.status() ).toBe( 404 );
	} );

} );
