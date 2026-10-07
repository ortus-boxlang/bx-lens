import path from 'node:path';
import fs from 'node:fs';
import { test, expect, Lens } from './lens';
import { Page } from '@playwright/test';

/**
 * Generates the documentation screenshots. They are written to docs/assets/screenshots and uploaded as a CI artifact.
 * Run on its own with: npx playwright test screens.spec.ts
 */
const OUT = path.resolve( __dirname, '../../docs/assets/screenshots' );

test.describe.configure( { mode: 'serial' } );
test.beforeAll( () => fs.mkdirSync( OUT, { recursive: true } ) );

async function shoot( page: Page, name: string, full = false ) {
	await page.waitForTimeout( 250 );
	if ( full ) {
		await page.screenshot( { path: path.join( OUT, `${ name }.png` ) } );
	} else {
		await page.locator( '#bxlens .lens' ).screenshot( { path: path.join( OUT, `${ name }.png` ) } );
	}
}

async function show( lens: Lens, path: string, tab: string ) {
	await lens.visit( path );
	await lens.open( tab );
}

test.describe( '@screens documentation screenshots', () => {

	test( 'overview and the collapsed strip', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await shoot( page, 'strip-collapsed' );
		await lens.open( 'Timeline' );
		await shoot( page, 'overview', true );
	} );

	test( 'timeline', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await page.locator( '#bxlens .wf .r', { hasText: 'SELECT name FROM customers' } ).nth( 2 ).click();
		await shoot( page, 'timeline' );
	} );

	test( 'queries', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Queries' );
		await shoot( page, 'queries' );
	} );

	test( 'issues', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Issues' );
		await shoot( page, 'issues' );
	} );

	test( 'exceptions', async ( { lens, page } ) => {
		await lens.visit( '/caught-exception.bxm' );
		await shoot( page, 'issues-exception' );
		await lens.open( 'Exceptions' );
		await shoot( page, 'exceptions' );
	} );

	test( 'templates and functions', async ( { lens, page } ) => {
		await show( lens, '/functions.bxm', 'Templates' );
		await shoot( page, 'templates' );
	} );

	test( 'http', async ( { lens, page } ) => {
		await show( lens, '/http.bxm', 'HTTP' );
		await shoot( page, 'http' );
	} );

	test( 'messages and timers', async ( { lens, page } ) => {
		await show( lens, '/timers.bxm', 'Messages' );
		await shoot( page, 'messages' );
		await lens.open( 'Timers' );
		await shoot( page, 'timers' );
	} );

	test( 'cache', async ( { lens, page } ) => {
		await show( lens, '/cache.bxm', 'Cache' );
		await shoot( page, 'cache' );
	} );

	test( 'modules', async ( { lens, page } ) => {
		await show( lens, '/orders.bxm', 'Modules' );
		await shoot( page, 'modules' );
	} );

	test( 'request, scopes and runtime', async ( { lens, page } ) => {
		await page.goto( '/forms.bxm' );
		await page.locator( '#login button' ).click();
		await lens.open( 'Request' );
		await shoot( page, 'request' );
		await lens.open( 'Scopes' );
		await shoot( page, 'scopes' );
		await lens.open( 'Runtime' );
		await shoot( page, 'runtime' );
	} );

	test( 'history', async ( { lens, page, request } ) => {
		await request.get( '/api/orders.json.bxm' );
		await request.get( '/events.bxm' );
		await request.get( '/error.bxm', { failOnStatusCode: false } );
		await show( lens, '/orders.bxm', 'History' );
		await shoot( page, 'history' );
	} );

	test( 'custom panels', async ( { lens, page } ) => {
		await show( lens, '/extend.bxm', 'Harness' );
		await shoot( page, 'extend' );
		await lens.open( 'Demo module' );
		await shoot( page, 'extend-module' );
	} );

	test( 'detached window', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await page.locator( '#bxlens .ibtn[title^="Detach"]' ).click();
		await shoot( page, 'popout', true );
	} );

	test( 'light theme', async ( { lens, page } ) => {
		await page.emulateMedia( { colorScheme: 'light' } );
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await shoot( page, 'light-theme', true );
	} );

	test( 'phone width', async ( { lens, page } ) => {
		await page.setViewportSize( { width: 400, height: 760 } );
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await shoot( page, 'phone', true );
	} );

} );
