import { test, expect, Lens } from './lens';
import { Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

test.describe( 'the bar and the console', () => {

	test( 'the bar offers a way into the console', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		const id = ( await lens.data() ).data.request.id;
		const button = page.locator( '#bxlens a.console' );
		await expect( button ).toBeVisible();
		await expect( button ).toHaveText( /Console/ );
		await expect( button ).toHaveAttribute( 'href', `${ BASE }#requests/${ id }` );
	} );

	test( 'the bar makes no request outside the server and uses Phosphor icons', async ( { lens, page } ) => {
		const outside: string[] = [];
		page.on( 'request', r => { const u = r.url(); if ( !u.startsWith( 'http://127.0.0.1' ) && !u.startsWith( 'data:' ) ) { outside.push( u ); } } );
		await lens.visit( '/orders.bxm' );
		await expect( page.locator( '#bxlens svg.ic use[href^="#bxlens-ph-"]' ).first() ).toBeAttached();
		expect( outside ).toEqual( [] );
	} );

	test( 'copying the request as JSON works from the menu', async ( { lens, page, context } ) => {
		await context.grantPermissions( [ 'clipboard-read', 'clipboard-write' ] );
		await lens.visit( '/orders.bxm' );
		await page.locator( '#bxlens .more .ibtn' ).click();
		await page.locator( '#bxlens .menu button', { hasText: 'Copy request as JSON' } ).click();
		const text = await page.evaluate( () => navigator.clipboard.readText() );
		expect( JSON.parse( text ).request.uri ).toContain( '/orders.bxm' );
	} );

	test( 'a request opened from the bar menu is selected in the console', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		const id = ( await lens.data() ).data.request.id;
		await signIn( page );
		await page.goto( `${ BASE }#requests/${ id }` );
		await page.reload();
		await expect( page.locator( '.main section:visible .card h3', { hasText: '/orders.bxm' } ) ).toBeVisible();
	} );

	test( 'the bar designer reorders and hides tabs, and the bar follows after a reload', async ( { lens, page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Bar designer")' );
		const rows = page.locator( '.drow' );
		await expect( rows.first() ).toBeVisible();
		try {
			await page.locator( '.drow', { hasText: 'Scopes' } ).locator( '.eye' ).click();
			await page.locator( '.drow', { hasText: 'Runtime' } ).locator( 'button[aria-label^="Move Runtime up"]' ).click();
			await expect( page.locator( '.ptabs .ptab', { hasText: 'Scopes' } ) ).toHaveCount( 0 );
			await page.click( 'button:has-text("Save layout")' );
			await expect( page.locator( '.toast' ) ).toContainText( 'Layout saved' );

			const bar = new Lens( await page.context().newPage() );
			await bar.visit( '/orders.bxm' );
			await bar.open( 'Timeline' );
			const tabs = await bar.page.locator( '#bxlens .tab' ).allInnerTexts();
			expect( tabs.join( '|' ) ).not.toContain( 'Scopes' );
			const labels = tabs.map( t => t.trim().split( /\s+/ )[ 0 ] );
			expect( labels.indexOf( 'Runtime' ) ).toBeGreaterThan( -1 );
			expect( labels.indexOf( 'Runtime' ) ).toBeLessThan( labels.indexOf( 'Request' ) + 2 );
		} finally {
			await page.request.post( `${ BASE }/api/bar/reset`, { headers: { 'X-Lens-CSRF': await page.evaluate( () => document.body.dataset.csrf! ) } } );
		}
	} );

	test( 'resetting the designer brings every tab back', async ( { lens, page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Bar designer")' );
		await page.locator( '.drow', { hasText: 'Timers' } ).locator( '.eye' ).click();
		await page.click( 'button:has-text("Save layout")' );
		await page.click( 'button:has-text("Reset to default")' );
		await expect( page.locator( '.drow', { hasText: 'Timers' } ).locator( '.eye' ) ).toHaveAttribute( 'aria-pressed', 'true' );
		await lens.visit( '/timers.bxm' );
		await expect( lens.tab( 'Timers' ) ).toBeAttached();
	} );

	test( 'saving a layout needs the CSRF token', async ( { page } ) => {
		await signIn( page );
		const r = await page.request.post( `${ BASE }/api/bar/layout`, { form: { layout: '[]' }, failOnStatusCode: false } );
		expect( r.status() ).toBe( 403 );
	} );

} );
