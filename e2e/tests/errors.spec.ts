import { test, expect, Page } from '@playwright/test';
import { existsSync } from 'fs';
import { join } from 'path';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, tab: string ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	await page.click( `.nav:has-text("${ tab }")` );
}

test.describe( 'errors and reports', () => {

	test( 'a failing page becomes a group with a stack and request context, and a repeat adds to it', async ( { page, request } ) => {
		await request.get( '/error.bxm', { failOnStatusCode: false } );
		await request.get( '/error.bxm', { failOnStatusCode: false } );
		await signIn( page, 'Errors' );
		const row = page.locator( '.main section:visible tr.row', { hasText: 'thrown on purpose' } ).first();
		await expect( row ).toBeVisible();
		await row.click();
		const detail = page.locator( '.main section:visible .card' ).last();
		await expect( detail ).toContainText( 'GET /error.bxm' );
		await expect( detail ).toContainText( 'error.bxm' );
		await expect( row.locator( 'td.num' ).first() ).toHaveText( /[2-9]\d*×/ );
	} );

	test( 'reports show totals and the lifetime block when the disk store is on', async ( { page, request } ) => {
		await request.get( '/orders.bxm' );
		await signIn( page, 'Reports' );
		await expect( page.locator( '.main section:visible .tiles .big' ).first() ).not.toHaveText( '0' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'Since first install' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'Slowest URLs' );
	} );

	test( 'with a Plus license the errors and reports are written to disk', async () => {
		const dir = join( __dirname, '..', '..', 'harness', '.run', 'home', 'lens-data' );
		await expect.poll( () => existsSync( join( dir, 'errors.json' ) ) && existsSync( join( dir, 'reports.json' ) ), { timeout: 30_000 } ).toBe( true );
	} );

} );
