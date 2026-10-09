import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	// A viewer has no System page
	if ( pw !== 'lens-view' ) {
		await page.click( '.nav:has-text("System")' );
	}
}

test.describe( 'heap and GC', () => {

	test( 'Run GC reports how much was freed', async ( { page } ) => {
		await signIn( page );
		await page.click( 'button:has-text("Run GC")' );
		await expect( page.locator( '.card', { hasText: 'Heap and garbage collection' } ) ).toContainText( 'Freed' );
	} );

	test( 'a heap dump needs a confirmation, then can be downloaded and is a real hprof file', async ( { page } ) => {
		await signIn( page );
		await page.click( 'button:has-text("Take a heap dump")' );
		await expect( page.locator( '.confirm', { hasText: 'Write a full heap dump' } ) ).toBeVisible();
		await page.click( 'button:has-text("Take the dump")' );
		await expect( page.locator( 'a:has-text("Download .hprof")' ) ).toBeVisible( { timeout: 60_000 } );
		const [ download ] = await Promise.all( [ page.waitForEvent( 'download' ), page.click( 'a:has-text("Download .hprof")' ) ] );
		expect( download.suggestedFilename() ).toMatch( /\.hprof$/ );
		const path = await download.path();
		const fs = await import( 'fs' );
		const head = fs.readFileSync( path ).subarray( 0, 18 ).toString( 'latin1' );
		expect( head ).toContain( 'JAVA PROFILE' );
	} );

	test( 'the API refuses a heap dump without confirmation and for a viewer', async ( { page } ) => {
		await signIn( page );
		await page.click( 'button:has-text("Discard")' ).catch( () => {} );
		const status = await page.evaluate( async () => {
			const st = await ( await fetch( '/~bxlens/index.bxm/api/state', { credentials: 'same-origin' } ) ).json();
			const r = await fetch( '/~bxlens/index.bxm/api/heapdump', { method: 'POST', credentials: 'same-origin', headers: { 'X-Lens-CSRF': st.csrf } } );
			return r.status;
		} );
		expect( status ).toBe( 400 );
		await page.click( 'button:has-text("Log out")' );
		await signIn( page, 'lens-view' );
		const viewer = await page.evaluate( async () => {
			const r = await fetch( '/~bxlens/index.bxm/api/heapdump', { credentials: 'same-origin' } );
			return r.status;
		} );
		expect( viewer ).toBe( 403 );
	} );

} );
