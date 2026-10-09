import { test, expect, Page } from '@playwright/test';
import { execFileSync } from 'child_process';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	// A viewer has no Environment page
	if ( pw !== 'lens-view' ) {
		await page.click( '.nav:has-text("Environment")' );
	}
}

test.describe( 'environment', () => {

	test( 'shows config, modules, JVM arguments, variables and properties without secrets', async ( { page } ) => {
		await signIn( page );
		const pre = page.locator( 'pre.code' ).first();
		await expect( pre ).toContainText( 'modules' );
		const text = await page.content();
		expect( text ).not.toContain( 'lens-demo' );
		await page.click( '.main section:visible button:has-text("Modules")' );
		await expect( page.locator( '.main section:visible td.mono', { hasText: 'bxLens' } ).first() ).toBeVisible();
		await page.click( 'button:has-text("Environment variables")' );
		await page.fill( 'input[placeholder="Filter by name"]', 'PATH' );
		await expect( page.locator( 'td.mono', { hasText: /^PATH$/ } ).first() ).toBeVisible();
		await page.click( 'button:has-text("System properties")' );
		await page.fill( 'input[placeholder="Filter by name"]', 'java.version' );
		await expect( page.locator( 'td.mono', { hasText: 'java.version' } ).first() ).toBeVisible();
	} );

	test( 'the diagnostic bundle is a zip with the expected files and no password', async ( { page } ) => {
		await signIn( page );
		const [ download ] = await Promise.all( [ page.waitForEvent( 'download' ), page.click( 'a:has-text("Diagnostic bundle")' ) ] );
		expect( download.suggestedFilename() ).toMatch( /^lens-diagnostics-.*\.zip$/ );
		const path = await download.path();
		const list = execFileSync( 'unzip', [ '-Z1', path ] ).toString();
		for ( const f of [ 'README.txt', 'thread-dump.txt', 'environment.json', 'system.json', 'executors.json', 'datasources.json' ] ) {
			expect( list ).toContain( f );
		}
		const all = execFileSync( 'unzip', [ '-p', path ], { maxBuffer: 64 * 1024 * 1024 } ).toString();
		expect( all ).not.toContain( 'lens-demo' );
		expect( all ).not.toContain( 'lens-view' );
	} );

	test( 'a viewer cannot download the bundle', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		const status = await page.evaluate( async () => ( await fetch( '/~bxlens/index.bxm/api/bundle', { credentials: 'same-origin' } ) ).status );
		expect( status ).toBe( 403 );
	} );

} );
