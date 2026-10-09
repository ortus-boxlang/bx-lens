import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( '.nav:has-text("Modules")' );
}

test.describe( 'modules page', () => {

	test( 'lists loaded modules and shows what one provides', async ( { page } ) => {
		await signIn( page );
		const rows = page.locator( '.main section:visible tbody tr.row' );
		await expect( rows.first() ).toBeVisible();
		await page.fill( 'input[placeholder="Filter modules"]', 'bxLens' );
		await expect( rows ).toHaveCount( 1 );
		await rows.first().click();
		const detail = page.locator( '.main section:visible .card' ).last();
		await expect( detail ).toContainText( 'bxLens' );
		await expect( detail ).toContainText( 'Public mapping' );
		await expect( detail ).toContainText( '/~bxlens/' );
		await expect( detail ).toContainText( 'Activated' );
	} );

} );
