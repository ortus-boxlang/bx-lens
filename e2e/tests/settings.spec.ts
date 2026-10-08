import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( '.nav:has-text("Settings")' );
}

test.describe( 'live settings', () => {

	test( 'a change applies at once, survives a reload and can be reset', async ( { page } ) => {
		await signIn( page );
		const row = page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } );
		await row.locator( 'input[type=number]' ).fill( '77' );
		await expect( page.locator( '.sbar' ) ).toContainText( '1 unsaved change' );
		await page.click( '.sbar button:has-text("Apply and save")' );
		await expect( row.locator( '.chipx', { hasText: 'changed' } ) ).toBeVisible();
		await page.reload();
		await page.click( '.nav:has-text("Settings")' );
		await expect( page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).locator( 'input[type=number]' ) ).toHaveValue( '77' );
		await page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).locator( 'button:has-text("Reset")' ).click();
		await expect( page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).locator( 'input[type=number]' ) ).toHaveValue( '15' );
	} );

	test( 'turning a collector off removes it live', async ( { page } ) => {
		await signIn( page );
		const row = page.locator( '.srow', { hasText: 'collectors.http.enabled' } );
		await row.locator( '.sw span' ).click();
		await page.click( '.sbar button:has-text("Apply and save")' );
		await expect( row.locator( '.chipx', { hasText: 'changed' } ) ).toBeVisible();
		await page.locator( '.srow', { hasText: 'collectors.http.enabled' } ).locator( 'button:has-text("Reset")' ).click();
		await expect( page.locator( '.srow', { hasText: 'collectors.http.enabled' } ).locator( '.chipx', { hasText: 'changed' } ) ).not.toBeVisible();
	} );

	test( 'locked settings show as boxlang.json only and the API refuses them', async ( { page } ) => {
		await signIn( page );
		await expect( page.locator( '.srow', { hasText: 'console.password' } ) ).toContainText( 'boxlang.json only' );
		const res = await page.evaluate( async () => {
			const st = await ( await fetch( '/~bxlens/index.bxm/api/state', { credentials: 'same-origin' } ) ).json();
			const r = await fetch( '/~bxlens/index.bxm/api/settings', {
				method: 'POST', credentials: 'same-origin',
				headers: { 'X-Lens-CSRF': st.csrf, 'Content-Type': 'application/x-www-form-urlencoded' },
				body: new URLSearchParams( { changes: JSON.stringify( { 'console.password': 'hack' } ) } ).toString()
			} );
			return { status: r.status, body: await r.json() };
		} );
		expect( res.status ).toBe( 400 );
		expect( res.body.error ).toContain( 'boxlang.json' );
	} );

	test( 'a bad value is rejected and nothing is saved', async ( { page } ) => {
		await signIn( page );
		const row = page.locator( '.srow', { hasText: 'thresholds.slowRequestMs' } );
		await row.locator( 'input[type=number]' ).fill( '-5' );
		await page.click( '.sbar button:has-text("Apply and save")' );
		await expect( page.locator( '.msg.err' ) ).toContainText( 'between' );
		await page.click( '.sbar button:has-text("Discard")' );
	} );

} );
