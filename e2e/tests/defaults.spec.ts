import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function settings( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	const view = await page.evaluate( async () => await ( await fetch( '/~bxlens/index.bxm/api/settings', { credentials: 'same-origin' } ) ).json() );
	return Object.fromEntries( view.settings.map( ( s: any ) => [ s.key, s ] ) );
}

test.describe( 'defaults', () => {

	test( 'the shipped defaults are light, no query parameter values, no ORM, no bifs or logs', async ( { page } ) => {
		const s = await settings( page );
		expect( s[ 'collect.level' ].default ).toBe( 'light' );
		expect( s[ 'collectors.queries.includeParams' ].default ).toBe( false );
		expect( s[ 'collectors.orm.enabled' ].default ).toBe( false );
		expect( s[ 'collectors.bifs.enabled' ].default ).toBe( false );
		expect( s[ 'collectors.logs.enabled' ].default ).toBe( false );
		expect( s[ 'collectors.functions.enabled' ].default ).toBe( false );
		expect( s[ 'history.trackNonHtml' ].default ).toBe( false );
	} );

	test( 'the harness turns on what it demonstrates, and the console shows both the value and the default', async ( { page } ) => {
		const s = await settings( page );
		expect( s[ 'collect.level' ].value ).toBe( 'full' );
		expect( s[ 'collectors.queries.includeParams' ].value ).toBe( true );
		expect( s[ 'collectors.queries.includeParams' ].source ).toBe( 'config' );
	} );

	test( 'every tracked request carries the request id header', async ( { request } ) => {
		const r = await request.get( '/api/orders.json.bxm' );
		expect( r.headers()[ 'x-bxlens-id' ] ).toMatch( /^[0-9a-z]{9,16}$/ );
	} );

} );
