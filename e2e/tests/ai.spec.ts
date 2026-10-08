import { test, expect, Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

async function call( page: Page, method: string, path: string, form?: Record<string, string> ) {
	return page.evaluate( async ( [ m, p, f ] ) => {
		const st = await ( await fetch( '/~bxlens/index.bxm/api/state', { credentials: 'same-origin' } ) ).json();
		const r = await fetch( '/~bxlens/index.bxm/api/' + p, {
			method: m as string, credentials: 'same-origin',
			headers: { 'X-Lens-CSRF': st.csrf, 'Content-Type': 'application/x-www-form-urlencoded' },
			body: m === 'GET' ? undefined : new URLSearchParams( ( f || {} ) as Record<string, string> ).toString()
		} );
		return { status: r.status, body: await r.json() };
	}, [ method, path, form ] as any );
}

test.describe( 'AI help', () => {

	test( 'a prompt for an error holds the stack and context and never a secret', async ( { page, request } ) => {
		await request.get( '/caught-exception.bxm' );
		await request.get( '/error.bxm?password=topsecret&id=5', { failOnStatusCode: false } );
		await signIn( page );
		const list = await call( page, 'GET', 'errors' );
		const g = list.body.groups.find( ( x: any ) => String( x.message ).includes( 'thrown on purpose' ) );
		expect( g ).toBeDefined();
		const p = await call( page, 'GET', `ai/prompt?kind=error&id=${ g.id }&n=0` );
		expect( p.status ).toBe( 200 );
		expect( p.body.prompt ).toContain( 'thrown on purpose' );
		expect( p.body.prompt ).toContain( 'GET /error.bxm' );
		expect( p.body.prompt ).not.toContain( 'topsecret' );
	} );

	test( 'the Errors page offers the prompt buttons and the call is refused while AI is off', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Errors")' );
		await page.locator( '.main section:visible tr.row' ).first().click();
		await expect( page.locator( '.main section:visible button:has-text("Copy prompt")' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible button:has-text("Ask ChatGPT")' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible button:has-text("Explain with AI")' ) ).not.toBeVisible();
		const r = await call( page, 'POST', 'ai/ask', { question: 'why slow?' } );
		expect( r.status ).toBe( 409 );
		expect( r.body.error ).toMatch( /AI is off|not installed/ );
	} );

	test( 'a viewer can copy a prompt but cannot call the model', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		const r = await call( page, 'POST', 'ai/ask', { question: 'why slow?' } );
		expect( r.status ).toBe( 403 );
		const p = await call( page, 'GET', 'ai/prompt?kind=ask&id=' + encodeURIComponent( 'why slow?' ) );
		expect( p.status ).toBe( 200 );
		expect( p.body.prompt ).toContain( 'SERVER SUMMARY' );
	} );

} );
