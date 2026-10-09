import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( '.nav:has-text("Logs")' );
}

test.describe( 'logs', () => {

	test( 'lists every log file, searches one and shows new lines live', async ( { page, browser } ) => {
		await signIn( page );
		await page.locator( 'tr.row', { hasText: 'bxlens-audit.log' } ).click();
		await page.fill( 'input[placeholder="Search this file"]', 'login.ok' );
		const view = page.locator( 'pre.logview' );
		await expect( view ).toContainText( 'event=login.ok' );
		await expect( view ).not.toContainText( 'event=logout' );
		// Another browser signs in: the line shows up without a reload
		const before = await view.locator( 'div' ).count();
		const other = await browser.newPage( { baseURL: 'http://127.0.0.1:8085' } );
		await other.goto( BASE );
		await other.fill( '#pw', 'lens-view' );
		await other.click( '.go' );
		await expect( other.locator( '.shell .app' ) ).toBeVisible();
		await expect.poll( async () => await view.locator( 'div' ).count(), { timeout: 15_000 } ).toBeGreaterThan( before );
		await other.close();
	} );

	test( 'a path outside the logs directory is refused, and a viewer cannot download', async ( { page } ) => {
		await signIn( page );
		const r = await page.evaluate( async () => {
			const a = await fetch( '/~bxlens/index.bxm/api/logfiles/read?file=' + encodeURIComponent( '../../../../etc/passwd' ), { credentials: 'same-origin' } );
			const b = await fetch( '/~bxlens/index.bxm/api/logfiles/read?file=' + encodeURIComponent( '/etc/passwd' ), { credentials: 'same-origin' } );
			return [ a.status, b.status ];
		} );
		expect( r ).toEqual( [ 404, 404 ] );
		await page.click( 'button:has-text("Log out")' );
		await expect( page.locator( '#pw' ) ).toBeVisible();
		await page.fill( '#pw', 'lens-view' );
		await page.click( '.go' );
		await expect( page.locator( '.shell .app' ) ).toBeVisible();
		const d = await page.evaluate( async () => ( await fetch( '/~bxlens/index.bxm/api/logfiles/download?file=bxlens-audit.log', { credentials: 'same-origin' } ) ).status );
		expect( d ).toBe( 403 );
	} );

	test( 'searches are bounded: parallel searches are answered or told to wait, never piled up', async ( { page } ) => {
		await signIn( page );
		const statuses = await page.evaluate( async () => {
			const one = () => fetch( '/~bxlens/index.bxm/api/logfiles/read?file=bxlens-audit.log&lines=50&q=event', { credentials: 'same-origin' } ).then( r => r.status );
			return await Promise.all( Array.from( { length: 24 }, one ) );
		} );
		expect( statuses.every( s => s === 200 || s === 429 ) ).toBe( true );
		expect( statuses.some( s => s === 200 ) ).toBe( true );
		// A search still answers afterwards and says whether the file was cut
		const r = await page.evaluate( async () => await ( await fetch( '/~bxlens/index.bxm/api/logfiles/read?file=bxlens-audit.log&lines=5&q=login', { credentials: 'same-origin' } ) ).json() );
		expect( r.lines.length ).toBeLessThanOrEqual( 5 );
		expect( typeof r.cut ).toBe( 'boolean' );
	} );

} );
