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

	test( 'issues, in the console request detail', async ( { page, request } ) => {
		await request.get( '/n-plus-one.bxm' );
		await consoleLogin( page );
		await page.click( '.nav:has-text("Requests")' );
		await page.locator( 'tr.row', { hasText: '/n-plus-one.bxm' } ).first().locator( 'td' ).first().click();
		await expect( page.locator( '.wf div' ).first() ).toBeVisible();
		await consoleShot( page, 'issues' );
	} );

	test( 'exceptions', async ( { lens, page } ) => {
		await lens.visit( '/caught-exception.bxm' );
		await shoot( page, 'issues-exception' );
		await lens.open( 'Exceptions' );
		await shoot( page, 'exceptions' );
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

	test( 'an interrupted span in the timeline', async ( { lens, page } ) => {
		await show( lens, '/spans/caught-func.bxm', 'Timeline' );
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'risky()' } ).locator( '.flag:visible', { hasText: 'interrupted' } ) ).toBeVisible();
		await page.locator( '#bxlens .wf .r', { hasText: 'risky()' } ).click();
		await shoot( page, 'interrupted' );
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
		await page.locator( '.drow', { hasText: 'Runtime' } ).locator( 'button[aria-label^="Move Runtime up"]' ).click();
		await consoleShot( page, 'console-designer' );
		await page.click( '.nav:has-text("Settings")' );
		await consoleShot( page, 'console-settings' );
	} );

	test( 'console datasources, caches, logs and environment', async ( { page, request } ) => {
		for ( const u of [ '/orders.bxm', '/n-plus-one.bxm', '/cache.bxm' ] ) {
			await request.get( u );
		}
		await consoleLogin( page );
		await page.click( '.nav:has-text("Datasources")' );
		await page.waitForTimeout( 800 );
		await page.click( 'button:has-text("Test connection")' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'Connection opened and validated' );
		await consoleShot( page, 'console-datasources' );
		await page.click( '.nav:has-text("Caches")' );
		await page.locator( '.main section:visible tbody tr' ).first().locator( 'button:has-text("View")' ).click();
		await expect( page.locator( '.main section:visible pre.code' ).last() ).toBeVisible();
		await consoleShot( page, 'console-caches' );
		await page.click( '.nav:has-text("Logs")' );
		await page.locator( 'tr.row', { hasText: 'bxlens-audit.log' } ).click();
		await page.fill( 'input[placeholder="Search this file"]', 'login' );
		await page.waitForTimeout( 800 );
		await consoleShot( page, 'console-logs' );
		await page.click( '.nav:has-text("Environment")' );
		await page.click( '.main section:visible button:has-text("Modules")' );
		await consoleShot( page, 'console-environment' );
	} );

	test( 'console queries, errors, reports and in flight', async ( { page, request } ) => {
		for ( const u of [ '/n-plus-one.bxm', '/n-plus-one.bxm', '/slow.bxm', '/query-error.bxm', '/orders.bxm', '/caught-exception.bxm' ] ) {
			await request.get( u );
		}
		await request.get( '/error.bxm', { failOnStatusCode: false } );
		await request.get( '/error.bxm', { failOnStatusCode: false } );
		await consoleLogin( page );
		await page.click( '.nav:has-text("Queries")' );
		await page.locator( '.main section:visible tr.row' ).first().click();
		await consoleShot( page, 'console-queries' );
		await page.click( '.nav:has-text("Errors")' );
		await page.locator( '.main section:visible tr.row', { hasText: 'thrown on purpose' } ).first().click();
		await page.waitForTimeout( 500 );
		await consoleShot( page, 'console-errors' );
		await page.click( '.nav:has-text("Reports")' );
		await page.waitForTimeout( 800 );
		await consoleShot( page, 'console-reports' );
		await page.click( '.nav:has-text("In flight")' );
		const slow = request.get( '/stall.bxm', { timeout: 20_000 } );
		const row = page.locator( '.main section:visible tr.row', { hasText: '/stall.bxm' } );
		await expect( row ).toBeVisible( { timeout: 8_000 } );
		await row.click();
		await page.waitForTimeout( 500 );
		await consoleShot( page, 'console-inflight' );
		await slow;
	} );

	test( 'console ask, settings edit and heap', async ( { page } ) => {
		await consoleLogin( page );
		await page.click( '.nav:has-text("Ask Lens")' );
		await page.fill( 'textarea', 'Why was the server slow in the last few minutes?' );
		await consoleShot( page, 'console-ask' );
		await page.click( '.nav:has-text("Settings")' );
		await page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).locator( 'input[type=number]' ).fill( '40' );
		await page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).scrollIntoViewIfNeeded();
		await consoleShot( page, 'console-settings-edit' );
		await page.click( '.sbar button:has-text("Discard")' );
		await page.click( '.nav:has-text("System")' );
		await page.waitForTimeout( 1200 );
		await page.click( 'button:has-text("Run GC")' );
		await page.locator( '.card', { hasText: 'Heap and garbage collection' } ).scrollIntoViewIfNeeded();
		await page.waitForTimeout( 500 );
		await consoleShot( page, 'console-system-heap' );
	} );

	test( 'console configuration', async ( { page } ) => {
		await consoleLogin( page );
		await page.click( '.nav:has-text("Configuration")' );
		await expect( page.locator( '.main section:visible td.mono', { hasText: 'debugMode' } ) ).toBeVisible();
		await consoleShot( page, 'console-configuration' );
	} );

	test( 'console unfinished request', async ( { page, request } ) => {
		test.setTimeout( 150_000 );
		request.get( '/spans/hang.bxm', { timeout: 4000 } ).catch( () => undefined );
		await consoleLogin( page );
		await page.click( '.nav:has-text("Requests")' );
		const row = page.locator( 'tr.row', { hasText: '/spans/hang.bxm' } );
		await expect( async () => {
			await page.click( '.nav:has-text("Overview")' );
			await page.click( '.nav:has-text("Requests")' );
			await expect( row.locator( '.pill', { hasText: 'unfinished' } ) ).toBeVisible( { timeout: 2000 } );
		} ).toPass( { timeout: 120_000, intervals: [ 3000 ] } );
		await row.first().locator( 'td' ).first().click();
		await expect( page.locator( '.wf div' ).first() ).toBeVisible();
		await consoleShot( page, 'console-unfinished' );
	} );

	test( 'console modules', async ( { page } ) => {
		await consoleLogin( page );
		await page.click( '.nav:has-text("Modules")' );
		await page.locator( '.main section:visible tbody tr.row', { hasText: 'bxLens' } ).first().click();
		await consoleShot( page, 'console-modules' );
	} );

	test( 'console orm', async ( { page, request } ) => {
		const orm = `http://127.0.0.1:${ process.env.ORM_PORT || '8091' }`;
		await request.get( `${ orm }/orm.bxm?save=1` );
		await page.goto( `${ orm }/~bxlens/index.bxm` );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await page.waitForSelector( '.shell .app' );
		await page.click( '.nav:has-text("ORM")' );
		await page.waitForTimeout( 1500 );
		await consoleShot( page, 'console-orm' );
	} );

	test( 'console integrations', async ( { page } ) => {
		const orm = `http://127.0.0.1:${ process.env.ORM_PORT || '8091' }`;
		await page.goto( `${ orm }/~bxlens/index.bxm` );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await page.waitForSelector( '.shell .app' );
		await page.click( '.nav:has-text("Modules")' );
		await page.waitForSelector( '#integrations tr[data-integration="orm"] .chipx' );
		await page.waitForTimeout( 400 );
		await page.locator( '#integrations' ).screenshot( { path: path.join( OUT, 'console-integrations.png' ) } );
	} );

	test( 'console on the free tier', async ( { page } ) => {
		const free = `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }`;
		await page.goto( `${ free }/~bxlens/index.bxm` );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await page.waitForSelector( '.shell .app' );
		await page.click( '.nav:has-text("Caches")' );
		await page.waitForTimeout( 1200 );
		await consoleShot( page, 'console-free-locked' );
	} );

	test( 'bar bifs', async ( { lens } ) => {
		await show( lens, '/orders.bxm', 'BIFs' );
		await shoot( lens.page, 'bar-bifs' );
	} );

	test( 'bar menu', async ( { lens, page } ) => {
		await show( lens, '/n-plus-one.bxm', 'Timeline' );
		await page.locator( '#bxlens .more .ibtn' ).click();
		await page.waitForTimeout( 300 );
		await page.screenshot( { path: path.join( OUT, 'bar-menu.png' ) } );
	} );

} );
