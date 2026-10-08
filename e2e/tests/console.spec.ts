import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';
const PASSWORD = 'lens-demo';

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', PASSWORD );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

test.describe( 'console', () => {

	test( 'asks for a password and a wrong one says so', async ( { page } ) => {
		await page.goto( BASE );
		await expect( page.locator( 'h1' ) ).toHaveText( 'BX Lens Console' );
		await page.fill( '#pw', 'not-it' );
		await page.click( '.go' );
		await expect( page.locator( '.msg.err' ) ).toContainText( 'Wrong password' );
		await expect( page.locator( '.msg.err' ) ).toContainText( 'attempts left' );
	} );

	test( 'the API needs a session', async ( { request } ) => {
		const r = await request.get( `${ BASE }/api/state`, { failOnStatusCode: false } );
		expect( r.status() ).toBe( 401 );
	} );

	test( 'signs in, shows requests from the server and signs out', async ( { page, request } ) => {
		await request.get( '/orders.bxm' );
		await signIn( page );
		await expect( page.locator( '.top .lic' ) ).toBeVisible();
		await expect( page.locator( '.tiles .big' ).first() ).not.toHaveText( '0' );
		await page.click( '.nav:has-text("Requests")' );
		await expect( page.locator( 'tbody tr.row' ).first() ).toBeVisible();
		await page.locator( 'tbody tr.row', { hasText: '/orders.bxm' } ).first().click();
		await expect( page.locator( '.wf div' ).first() ).toBeVisible();
		await page.click( 'button:has-text("Log out")' );
		await expect( page.locator( '#pw' ) ).toBeVisible();
		const r = await page.request.get( `${ BASE }/api/state`, { failOnStatusCode: false } );
		expect( r.status() ).toBe( 401 );
	} );

	test( 'settings show the effective access rules without the password', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Settings")' );
		const kv = page.locator( '.main section:visible dl.kv' ).first();
		await expect( kv ).toContainText( 'console.password' );
		await expect( kv ).toContainText( 'set' );
		expect( await page.content() ).not.toContain( PASSWORD );
	} );

	test( 'never loads anything from outside the server', async ( { page } ) => {
		const outside: string[] = [];
		page.on( 'request', r => { const u = r.url(); if ( !u.startsWith( 'http://127.0.0.1' ) && !u.startsWith( 'data:' ) ) { outside.push( u ); } } );
		await page.goto( BASE );
		await signIn( page );
		await page.click( '.nav:has-text("Requests")' );
		await page.waitForTimeout( 500 );
		expect( outside ).toEqual( [] );
	} );

	test( 'pages carry a strict content security policy and are not cached', async ( { request } ) => {
		const r = await request.get( BASE );
		expect( r.headers()[ 'content-security-policy' ] ).toContain( "default-src 'none'" );
		expect( r.headers()[ 'cache-control' ] ).toBe( 'no-store' );
		expect( r.headers()[ 'x-frame-options' ] ).toBe( 'DENY' );
	} );

	test( 'only the listed assets are served', async ( { request } ) => {
		expect( ( await request.get( `${ BASE }/assets/console.css` ) ).status() ).toBe( 200 );
		expect( ( await request.get( `${ BASE }/assets/ModuleConfig.bx`, { failOnStatusCode: false } ) ).status() ).toBe( 404 );
		expect( ( await request.get( `${ BASE }/assets/..%2fModuleConfig.bx`, { failOnStatusCode: false } ) ).status() ).toBe( 404 );
	} );

	test( 'a login without the custom header is refused', async ( { request } ) => {
		const r = await request.post( `${ BASE }/login`, { form: { password: PASSWORD }, failOnStatusCode: false } );
		expect( r.status() ).toBe( 400 );
	} );

	test( 'a state change without the CSRF token is refused', async ( { page } ) => {
		await signIn( page );
		const r = await page.request.post( `${ BASE }/logout`, { failOnStatusCode: false } );
		expect( r.status() ).toBe( 403 );
	} );

	test( 'the console is not tracked as a request', async ( { page } ) => {
		await signIn( page );
		await page.waitForTimeout( 3500 );
		const j = await ( await page.request.get( `${ BASE }/api/requests` ) ).json();
		expect( j.requests.filter( ( r: any ) => r.url.includes( '~bxlens' ) ) ).toEqual( [] );
	} );

} );
