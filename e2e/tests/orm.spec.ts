import { test, expect } from '@playwright/test';

// Runs against the third harness server, which has bx-orm (see playwright.config.ts)
const ORM = `http://127.0.0.1:${ process.env.ORM_PORT || '8091' }`;
const BASE = '/~bxlens/index.bxm';
const API = `${ BASE }/api`;

test.use( { baseURL: ORM } );

test.describe( 'ORM', () => {

	test( 'ORM SQL shows with the other queries and the ORM page has the totals', async ( { page, request } ) => {
		const r = await request.get( '/orm.bxm?save=1' );
		expect( r.status() ).toBe( 200 );
		await page.goto( BASE );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await expect( page.locator( '.shell .app' ) ).toBeVisible();

		await page.click( '.nav:has-text("Queries")' );
		await page.fill( 'input[placeholder="Filter statements"]', 'orm_books' );
		await expect( page.locator( '.main section:visible tbody tr.row' ).first() ).toContainText( 'orm_books' );

		const api = await ( await request.post( `${ BASE }/login`, { headers: { 'X-Lens-Login': '1' }, form: { password: 'lens-demo' } } ) ).status();
		expect( api ).toBe( 200 );
		const orm = await ( await request.get( `${ API }/orm` ) ).json();
		expect( orm.installed ).toBe( true );
		expect( orm.factories.length ).toBeGreaterThan( 0 );
		expect( orm.factories[ 0 ].mode ).toBe( 'proxy' );

		await page.click( '.nav:has-text("ORM")' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'demo' );
	} );

} );
