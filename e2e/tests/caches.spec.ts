import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( '.nav:has-text("Caches")' );
}

test.describe( 'caches', () => {

	test( 'lists caches with a hit rate, filters keys, shows a value and evicts a key', async ( { page, request } ) => {
		await request.get( '/cache.bxm' );
		await signIn( page );
		await expect( page.locator( '.card', { hasText: 'hit rate' } ).first() ).toBeVisible();
		const rows = page.locator( '.main section:visible tbody tr' );
		await expect( rows.first() ).toBeVisible();
		const first = ( await rows.first().locator( 'td' ).first().innerText() ).trim();
		await page.fill( 'input[placeholder="Filter keys"]', first.slice( 0, 6 ) );
		await expect( rows.first() ).toContainText( first.slice( 0, 6 ) );
		await rows.first().locator( 'button:has-text("View")' ).click();
		await expect( page.locator( '.main section:visible pre.code' ).last() ).toBeVisible();
		await rows.first().locator( 'button:has-text("Evict")' ).click();
		await expect( page.locator( '.main section:visible .sub', { hasText: 'Evicted' } ) ).toBeVisible();
	} );

	test( 'a viewer cannot read values or evict', async ( { page, request } ) => {
		await request.get( '/cache.bxm' );
		await signIn( page, 'lens-view' );
		const rows = page.locator( '.main section:visible tbody tr' );
		await expect( rows.first() ).toBeVisible();
		await expect( rows.first().locator( 'button:has-text("View")' ) ).toBeDisabled();
		const status = await page.evaluate( async () => ( await fetch( '/~bxlens/index.bxm/api/cachevalue/default?key=x', { credentials: 'same-origin' } ) ).status );
		expect( status ).toBe( 403 );
	} );

} );
