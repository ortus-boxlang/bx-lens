import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

test.describe( 'datasources', () => {

	test( 'shows the pool with live numbers and tests a connection', async ( { page, request } ) => {
		await request.get( '/orders.bxm' );
		await signIn( page );
		await page.click( '.nav:has-text("Datasources")' );
		const card = page.locator( '.main section:visible .card' ).first();
		await expect( card ).toContainText( 'connections' );
		await expect( card.locator( '.chipx' ) ).toHaveText( /ok|waiting|saturated/ );
		await page.click( 'button:has-text("Test connection")' );
		await expect( card ).toContainText( 'Connection opened and validated' );
		await expect( card ).not.toContainText( 'jdbc:derby:memory:lens;user' );
	} );

	test( 'a viewer cannot test a connection', async ( { page } ) => {
		await page.goto( BASE );
		await page.fill( '#pw', 'lens-view' );
		await page.click( '.go' );
		await expect( page.locator( '.shell .app' ) ).toBeVisible();
		await page.click( '.nav:has-text("Datasources")' );
		await expect( page.locator( 'button:has-text("Test connection")' ).first() ).toBeDisabled();
	} );

} );
