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

	test( 'integrations: bx-orm is not installed here, so it is off and cannot be switched on', async ( { page } ) => {
		await signIn( page );
		const row = page.locator( '#integrations tr[data-integration="orm"]' );
		await expect( row ).toContainText( 'BoxLang ORM' );
		await expect( row.locator( '.chipx' ) ).toHaveText( 'Not installed' );
		await expect( row.locator( 'input[type=checkbox]' ) ).toBeDisabled();
		await expect( row ).toContainText( 'Install bx-orm to enable' );
		// The server refuses too, and the setting stays as it was
		const r = await page.evaluate( async () => {
			const st = await ( await fetch( '/~bxlens/index.bxm/api/state', { credentials: 'same-origin' } ) ).json();
			const x = await fetch( '/~bxlens/index.bxm/api/integrations/orm', {
				method: 'POST', credentials: 'same-origin',
				headers: { 'X-Lens-CSRF': st.csrf, 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'enabled=true'
			} );
			return { status: x.status, body: await x.json() };
		} );
		expect( r.status ).toBe( 409 );
		expect( r.body.error ).toContain( 'Install bx-orm' );
		const list = await page.evaluate( async () => ( await fetch( '/~bxlens/index.bxm/api/integrations', { credentials: 'same-origin' } ) ).json() );
		expect( list.integrations[ 0 ].status ).toBe( 'notInstalled' );
	} );

	test( 'the ORM page says bx-orm is not installed', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("ORM")' );
		const sec = page.locator( '.main section:visible' );
		await expect( sec.locator( '#orm-integration .chipx' ) ).toHaveText( 'Not installed' );
		await expect( sec.locator( '#orm-integration input[type=checkbox]' ) ).toBeDisabled();
		await expect( sec ).toContainText( 'The bx-orm module is not installed.' );
		await expect( sec.locator( '#orm-integration' ) ).toContainText( 'Install bx-orm to enable' );
	} );

} );
