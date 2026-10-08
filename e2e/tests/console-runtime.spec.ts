import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, tab?: string ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
	if ( tab ) {
		await page.click( `.nav:has-text("${ tab }")` );
	}
}

test.describe( 'console runtime pages', () => {

	test( 'executors show health, thresholds and the health report', async ( { page, request } ) => {
		await request.get( '/load.bxm' );
		await signIn( page, 'Executors' );
		await expect( page.locator( 'tr.row', { hasText: 'demo-pool' } ) ).toBeVisible();
		await page.locator( 'tr.row', { hasText: 'demo-pool' } ).locator( 'td' ).first().click();
		await expect( page.locator( '.card h3', { hasText: 'demo-pool' } ) ).toBeVisible();
		await expect( page.locator( '.meter .tick' ).first() ).toBeVisible();
		await expect( page.locator( '.rep h4', { hasText: 'Health report' } ) ).toBeVisible();
		await expect( page.locator( '.rep' ) ).toContainText( 'pool utilization' );
	} );

	test( 'an executor lists the scheduled tasks bound to it', async ( { page } ) => {
		await signIn( page, 'Executors' );
		await page.locator( 'tr.row', { hasText: 'bxschedule-scheduler' } ).locator( 'td' ).first().click();
		await expect( page.locator( 'text=Scheduled tasks on this executor' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible .card tr', { hasText: 'cleanup-sessions' } ) ).toBeVisible();
	} );

	test( 'tasks list every scheduled task with its status', async ( { page } ) => {
		await signIn( page, 'Tasks' );
		for ( const name of [ 'cleanup-sessions', 'sync-prices', 'warm-cache', 'nightly-report', 'export-orders' ] ) {
			await expect( page.locator( 'tr.row', { hasText: name } ).first() ).toBeVisible();
		}
		await expect( page.locator( 'tr.row', { hasText: 'warm-cache' } ).locator( '.pill' ).first() ).toHaveText( 'paused' );
		await expect( page.locator( 'tr.row', { hasText: 'nightly-report' } ).locator( '.pill' ).first() ).toHaveText( 'not run yet' );
	} );

	test( 'Run now shows the result of a task', async ( { page } ) => {
		await signIn( page, 'Tasks' );
		await page.locator( 'tr.row', { hasText: 'cleanup-sessions' } ).locator( 'td' ).first().click();
		await page.locator( '.card button.primary:has-text("Run now")' ).click();
		await expect( page.locator( '.okbox' ) ).toContainText( '42 expired sessions removed' );
	} );

	test( 'Run now shows the error of a failing task and marks it failing', async ( { page } ) => {
		await signIn( page, 'Tasks' );
		await page.locator( 'tr.row', { hasText: 'sync-prices' } ).locator( 'td' ).first().click();
		await page.locator( '.card button.primary:has-text("Run now")' ).click();
		await expect( page.locator( '.errbox' ).first() ).toContainText( 'Connection refused' );
		await expect( page.locator( 'tr.row', { hasText: 'sync-prices' } ).locator( '.pill' ).first() ).toHaveText( 'failing' );
	} );

	test( 'a task can be paused and resumed', async ( { page } ) => {
		await signIn( page, 'Tasks' );
		const row = page.locator( 'tr.row', { hasText: 'export-orders' } );
		await row.locator( 'button', { hasText: 'Pause' } ).click();
		await expect( row.locator( '.pill' ).first() ).toHaveText( 'paused' );
		await row.locator( 'button', { hasText: 'Resume' } ).click();
		await expect( row.locator( '.pill' ).first() ).not.toHaveText( 'paused' );
	} );

	test( 'reloading a scheduler asks first', async ( { page } ) => {
		await signIn( page, 'Tasks' );
		await page.locator( 'button:has-text("Reload all from tasks.json")' ).click();
		await expect( page.locator( '.confirm' ) ).toContainText( 'restart every scheduler' );
		await page.locator( '.confirm button:has-text("Cancel")' ).click();
		await expect( page.locator( '.confirm' ) ).toBeHidden();
	} );

	test( 'task actions need the CSRF token', async ( { page } ) => {
		await signIn( page );
		const r = await page.request.post( `${ BASE }/api/tasks/bxschedule/cleanup-sessions/pause`, { failOnStatusCode: false } );
		expect( r.status() ).toBe( 403 );
	} );

	test( 'system shows CPU, heap, pools and runtime details', async ( { page } ) => {
		await signIn( page, 'System' );
		await expect( page.locator( '.main section:visible .card h3', { hasText: 'Process CPU' } ) ).toBeVisible();
		await expect( page.locator( '.main section:visible .card h3', { hasText: 'Heap' } ).first() ).toBeVisible();
		await expect( page.locator( 'text=Memory pools' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible dl.kv' ).first() ).toContainText( 'JVM' );
	} );

	test( 'threads list the JVM threads and a dump can be downloaded', async ( { page } ) => {
		await signIn( page, 'Threads' );
		const rows = page.locator( '.main section:visible tbody tr.row' );
		await expect( rows.first() ).toBeVisible();
		await rows.first().locator( 'td' ).first().click();
		await expect( page.locator( '.main section:visible .frames li' ).first() ).toBeVisible();
		const r = await page.request.get( `${ BASE }/api/threads/dump` );
		expect( r.headers()[ 'content-disposition' ] ).toContain( 'lens-thread-dump-' );
		expect( await r.text() ).toContain( 'BX Lens thread dump' );
	} );

	test( 'the live stream pushes updates without polling', async ( { page } ) => {
		await signIn( page, 'System' );
		const ticks = await page.evaluate( () => new Promise<number>( resolve => {
			let n = 0;
			const es = new EventSource( '/~bxlens/index.bxm/stream?topics=system' );
			es.addEventListener( 'tick', () => { n++; if ( n >= 3 ) { es.close(); resolve( n ); } } );
			setTimeout( () => { es.close(); resolve( n ); }, 8000 );
		} ) );
		expect( ticks ).toBeGreaterThanOrEqual( 3 );
	} );

	test( 'the stream needs a session', async ( { request } ) => {
		const r = await request.get( `${ BASE }/stream`, { failOnStatusCode: false } );
		expect( r.status() ).toBe( 401 );
	} );

} );
