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

} );
