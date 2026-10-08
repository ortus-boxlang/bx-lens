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
		await page.locator( '#bxlens .more .ibtn' ).click();
		await page.locator( '#bxlens .menu button', { hasText: 'Detach as a floating window' } ).click();
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


	// ---- console ----

	const CONSOLE = '/~bxlens/index.bxm';

	async function consoleLogin( page: Page ) {
		await page.goto( CONSOLE );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await page.waitForSelector( '.shell .app' );
		await page.waitForTimeout( 1500 );
	}

	async function consoleShot( page: Page, name: string ) {
		await page.waitForTimeout( 400 );
		await page.screenshot( { path: path.join( OUT, `${ name }.png` ) } );
	}

	test( 'console login', async ( { page } ) => {
		await page.goto( CONSOLE );
		await consoleShot( page, 'console-login' );
		await page.fill( '#pw', 'not-it' );
		await page.click( '.go' );
		await expect( page.locator( '.msg.err' ) ).toBeVisible();
		await consoleShot( page, 'console-login-error' );
	} );

	test( 'console overview and requests', async ( { page, request } ) => {
		for ( const u of [ '/orders.bxm', '/n-plus-one.bxm', '/slow.bxm', '/caught-exception.bxm', '/api/orders.json.bxm', '/http.bxm' ] ) {
			await request.get( u );
		}
		await consoleLogin( page );
		await consoleShot( page, 'console-overview' );
		await page.click( '.nav:has-text("Requests")' );
		await page.locator( 'tr.row', { hasText: '/n-plus-one.bxm' } ).first().locator( 'td' ).first().click();
		await expect( page.locator( '.wf div' ).first() ).toBeVisible();
		await consoleShot( page, 'console-requests' );
	} );

	test( 'console executors', async ( { page, request } ) => {
		await request.get( '/load.bxm' );
		await consoleLogin( page );
		await page.click( '.nav:has-text("Executors")' );
		await page.locator( 'tr.row', { hasText: 'demo-pool' } ).locator( 'td' ).first().click();
		await page.waitForTimeout( 2500 );
		await consoleShot( page, 'console-executors' );
	} );

	test( 'console tasks and a run', async ( { page } ) => {
		await consoleLogin( page );
		await page.click( '.nav:has-text("Tasks")' );
		await page.locator( 'tr.row', { hasText: 'cleanup-sessions' } ).locator( 'td' ).first().click();
		await consoleShot( page, 'console-tasks' );
		await page.locator( 'tr.row', { hasText: 'sync-prices' } ).locator( 'td' ).first().click();
		await page.locator( '.card button.primary:has-text("Run now")' ).click();
		await expect( page.locator( '.errbox' ).first() ).toBeVisible();
		await consoleShot( page, 'console-task-run' );
	} );

	test( 'console system, threads, designer and settings', async ( { page } ) => {
		await consoleLogin( page );
		await page.click( '.nav:has-text("System")' );
		await page.waitForTimeout( 1500 );
		await consoleShot( page, 'console-system' );
		await page.click( '.nav:has-text("Threads")' );
		await page.waitForTimeout( 1500 );
		await consoleShot( page, 'console-threads' );
		await page.click( '.nav:has-text("Bar designer")' );
		await page.locator( '.drow', { hasText: 'Scopes' } ).locator( '.eye' ).click();
		await page.locator( '.drow', { hasText: 'History' } ).locator( 'button[aria-label^="Move History up"]' ).click();
		await consoleShot( page, 'console-designer' );
		await page.click( '.nav:has-text("Settings")' );
		await consoleShot( page, 'console-settings' );
	} );

	test( 'bar menu', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await page.locator( '#bxlens .more .ibtn' ).click();
		await page.waitForTimeout( 300 );
		await page.screenshot( { path: path.join( OUT, 'bar-menu.png' ) } );
	} );

} );
