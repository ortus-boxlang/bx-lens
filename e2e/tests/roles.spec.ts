import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw: string ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

async function api( page: Page, method: string, path: string, form?: Record<string, string> ) {
	return page.evaluate( async ( [ m, p, f ] ) => {
		const st = await ( await fetch( '/~bxlens/index.bxm/api/state', { credentials: 'same-origin' } ) ).json();
		const r = await fetch( '/~bxlens/index.bxm/api/' + p, {
			method: m as string, credentials: 'same-origin',
			headers: { 'X-Lens-CSRF': st.csrf, 'Content-Type': 'application/x-www-form-urlencoded' },
			body: m === 'GET' ? undefined : new URLSearchParams( ( f || {} ) as Record<string, string> ).toString()
		} );
		return { status: r.status };
	}, [ method, path, form ] as any );
}

test.describe( 'roles and proxy headers', () => {

	test( 'a viewer can look but not change anything or download a dump', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		await expect( page.locator( '.top .chipx', { hasText: 'viewer' } ) ).toBeVisible();
		await page.click( '.nav:has-text("Settings")' );
		await expect( page.locator( '.msg.warn', { hasText: 'signed in as a viewer' } ) ).toBeVisible();
		await expect( page.locator( '.srow', { hasText: 'thresholds.slowQueryMs' } ).locator( 'input[type=number]' ) ).toBeDisabled();
		expect( ( await api( page, 'POST', 'settings', { changes: '{"thresholds.slowQueryMs":50}' } ) ).status ).toBe( 403 );
		expect( ( await api( page, 'POST', 'tasks/x/y/pause' ) ).status ).toBe( 403 );
		expect( ( await api( page, 'GET', 'threads/dump' ) ).status ).toBe( 403 );
		expect( ( await api( page, 'GET', 'overview' ) ).status ).toBe( 200 );
	} );

	test( 'the admin password gives the admin role', async ( { page } ) => {
		await signIn( page, 'lens-demo' );
		await expect( page.locator( '.top .chipx', { hasText: 'admin' } ) ).toBeVisible();
		expect( ( await api( page, 'GET', 'threads/dump' ) ).status ).toBe( 200 );
	} );

	test( 'a proxy header from a trusted peer names the client', async ( { request } ) => {
		// The test connects from loopback, a trusted peer. A public client address is not allowed in by console.access=local
		const outside = await request.get( BASE, { headers: { 'X-Forwarded-For': '203.0.113.9' }, failOnStatusCode: false } );
		expect( outside.status() ).toBe( 404 );
		const inside = await request.get( BASE, { headers: { 'X-Forwarded-For': '127.0.0.1' }, failOnStatusCode: false } );
		expect( inside.status() ).toBe( 200 );
	} );

	test( 'a viewer does not get logs, thread stacks, the environment or system details', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		for ( const r of [ 'logfiles', 'logfiles/read?file=bxlens-audit.log&q=login', 'logfiles/download?file=bxlens-audit.log', 'threads', 'threads/dump', 'environment', 'system' ] ) {
			expect( ( await api( page, 'GET', r ) ).status, r ).toBe( 403 );
		}
		for ( const r of [ 'overview', 'requests', 'inflight', 'queries', 'errors', 'reports', 'executors', 'tasks', 'datasources', 'caches', 'modules', 'settings' ] ) {
			expect( ( await api( page, 'GET', r ) ).status, r ).toBe( 200 );
		}
		// The pages are not offered either
		await expect( page.locator( '.nav:has-text("Logs")' ) ).toHaveCount( 0 );
		await expect( page.locator( '.nav:has-text("Threads")' ) ).toHaveCount( 0 );
		await expect( page.locator( '.nav:has-text("Environment")' ) ).toHaveCount( 0 );
		await expect( page.locator( '.nav:has-text("System")' ) ).toHaveCount( 0 );
		await expect( page.locator( '.nav:has-text("Queries")' ) ).toBeVisible();
	} );

	test( 'a viewer does not see where the console is reachable from or where overrides are saved', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		const view = await page.evaluate( async () => await ( await fetch( '/~bxlens/index.bxm/api/settings', { credentials: 'same-origin' } ) ).json() );
		const byKey = Object.fromEntries( view.settings.map( ( s: any ) => [ s.key, s ] ) );
		expect( byKey[ 'console.access' ].value ).toBe( 'admin only' );
		expect( byKey[ 'access.proxyPeers' ].value ).toBe( 'admin only' );
		expect( byKey[ 'console.overridesFile' ].value ).toBe( 'admin only' );
		expect( view.overridesFile ).toBe( '' );
		await page.click( 'button:has-text("Log out")' );
		await signIn( page, 'lens-demo' );
		const admin = await page.evaluate( async () => await ( await fetch( '/~bxlens/index.bxm/api/settings', { credentials: 'same-origin' } ) ).json() );
		expect( admin.settings.find( ( s: any ) => s.key === 'console.access' ).value ).not.toBe( 'admin only' );
		expect( admin.overridesFile ).not.toBe( '' );
	} );

} );
