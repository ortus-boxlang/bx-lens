import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, tab: string ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( `.nav:has-text("${ tab }")` );
}

test.describe( 'queries and requests in flight', () => {

	test( 'the Queries page ranks statements, counts repeats and shows failures', async ( { page, request } ) => {
		await request.get( '/n-plus-one.bxm' );
		await request.get( '/query-error.bxm' );
		await signIn( page, 'Queries' );
		const rows = page.locator( '.main section:visible tbody tr.row' );
		await expect( rows.first() ).toBeVisible();
		// The repeated customer lookup ran at least ten times
		await page.fill( 'input[placeholder="Filter statements"]', 'FROM customers WHERE id' );
		await expect( rows.first() ).toContainText( 'FROM customers WHERE id' );
		expect( Number( await rows.first().locator( 'td.num' ).first().innerText() ) ).toBeGreaterThanOrEqual( 10 );
		await rows.first().click();
		await expect( page.locator( '.main section:visible dl.kv' ) ).toContainText( 'Min / avg / max' );
		// A failed statement is counted
		await page.fill( 'input[placeholder="Filter statements"]', 'missing_table' );
		await page.click( 'button:has-text("Failures")' );
		await expect( rows.first() ).toContainText( 'missing_table' );
		expect( Number( await rows.first().locator( 'td.num' ).last().innerText() ) ).toBeGreaterThanOrEqual( 1 );
	} );

	test( 'a request that is still running shows up with where it is stuck', async ( { page, request } ) => {
		await signIn( page, 'In flight' );
		const slow = request.get( '/stall.bxm', { timeout: 20_000 } );
		const row = page.locator( '.main section:visible tr.row', { hasText: '/stall.bxm' } );
		await expect( row ).toBeVisible( { timeout: 8_000 } );
		await row.click();
		await expect( page.locator( '.main section:visible pre.code' ) ).toContainText( 'stall.bxm' );
		await slow;
		await expect( row ).toHaveCount( 0, { timeout: 8_000 } );
	} );

} );
