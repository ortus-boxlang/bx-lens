import { test, expect } from './lens';

test.describe( 'panels', () => {

	test( 'Cache shows every registered cache and what this request did to it', async ( { lens, page } ) => {
		await lens.visit( '/cache.bxm' );
		await lens.open( 'Cache' );
		const names = page.locator( '#bxlens .card .t > span:first-child' );
		await expect( names.filter( { hasText: 'default' } ) ).toHaveCount( 1 );
		const card = page.locator( '#bxlens .card', { hasText: /^\s*default/ } ).first();
		await expect( card ).toContainText( 'This request:' );
		await expect( card ).toContainText( '5 hits' );
		await expect( card ).toContainText( '3 misses' );
		await expect( card ).toContainText( '63% hit rate' );
		await expect( card.locator( '.bar-meter' ) ).toBeVisible();
	} );

	test( 'Modules lists the loaded modules with version and author', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Modules' );
		const rows = lens.rows;
		await expect( rows.filter( { hasText: 'bxLens' } ) ).toContainText( 'this module' );
		await expect( rows.filter( { hasText: 'demoLens' } ) ).toContainText( 'Sample module that adds a panel to BX Lens' );
		await expect( rows.filter( { hasText: 'derby' } ) ).toContainText( 'Apache Derby' );
		await expect( rows.filter( { hasText: 'bxLens' } ).locator( '.pill', { hasText: 'active' } ) ).toBeVisible();
	} );

	test( 'Messages and Timers show what the page reported', async ( { lens, page } ) => {
		await lens.visit( '/timers.bxm' );
		await lens.open( 'Messages' );
		await expect( lens.panel ).toContainText( 'Report total is 5050' );
		await expect( page.locator( '#bxlens .msg .lvl.warn' ) ).toHaveText( 'warn' );
		await expect( page.locator( '#bxlens .msg .lvl.error' ) ).toHaveText( 'error' );
		await expect( page.locator( '#bxlens .msg pre:visible' ) ).toContainText( '"total": 5050' );
		await lens.open( 'Timers' );
		const rows = lens.rows;
		await expect( rows ).toHaveCount( 3 );
		await expect( rows.nth( 0 ) ).toContainText( 'build report' );
		await expect( lens.panel ).toContainText( 'sum a range' );
		await expect( lens.panel ).toContainText( 'warm up' );
	} );

	test( 'timers also appear on the waterfall', async ( { lens, page } ) => {
		await lens.visit( '/timers.bxm' );
		await lens.open( 'Timeline' );
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'build report' } ) ).toBeVisible();
	} );

	test( 'Request shows the headers and redacts sensitive ones', async ( { lens, page, context } ) => {
		await context.addCookies( [ { name: 'session_token', value: 'abc123', url: 'http://127.0.0.1:8085' } ] );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Request' );
		await expect( lens.panel ).toContainText( 'GET' );
		await expect( lens.panel ).toContainText( 'User-Agent' );
		await expect( lens.panel ).toContainText( 'Cookie' );
		await expect( lens.panel ).not.toContainText( 'abc123' );
		await expect( page.locator( '#bxlens .redact' ).first() ).toHaveText( '[redacted]' );
	} );

	test( 'Scopes masks passwords and tokens before they reach the page', async ( { lens, page } ) => {
		await page.goto( '/forms.bxm' );
		await page.locator( '#login button' ).click();
		await expect( lens.bar ).toBeVisible();
		await lens.open( 'Scopes' );
		await expect( lens.panel ).toContainText( 'dev@example.com' );
		await expect( lens.panel ).toContainText( '[redacted]' );
		await expect( lens.panel ).not.toContainText( 'hunter2' );
		// The raw value must not be anywhere in what Lens added, not even hidden. The host page's own form still has it.
		expect( await lens.root.innerHTML() ).not.toContain( 'hunter2' );
		expect( JSON.stringify( await lens.data() ) ).not.toContain( 'hunter2' );
	} );

	test( 'session values are shown and tokens inside them are masked', async ( { lens, page } ) => {
		await lens.visit( '/session.bxm' );
		await lens.open( 'Scopes' );
		await expect( lens.panel ).toContainText( '"userId": 42' );
		await expect( lens.panel ).toContainText( '"apiToken": "[redacted]"' );
		await expect( lens.panel ).not.toContainText( 'super-secret-token' );
	} );

	test( 'Runtime shows versions, memory and threads', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Runtime' );
		await expect( lens.panel ).toContainText( 'BoxLang' );
		await expect( lens.panel ).toContainText( /1\.\d+\.\d+/ );
		await expect( lens.panel ).toContainText( 'Heap' );
		await expect( lens.panel ).toContainText( 'Threads' );
	} );

	test( 'Copy buttons copy SQL, cURL and file locations', async ( { lens, page, context } ) => {
		await context.grantPermissions( [ 'clipboard-read', 'clipboard-write' ] );
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		await page.locator( '#bxlens .abtn', { hasText: 'Copy SQL' } ).first().click();
		await expect( page.locator( '#bxlens .toast' ) ).toContainText( 'SQL copied' );
		expect( await page.evaluate( () => navigator.clipboard.readText() ) ).toContain( 'SELECT id, customer_id, total FROM orders' );
		await lens.open( 'Request' );
		await page.locator( '#bxlens .abtn', { hasText: 'Copy as cURL' } ).click();
		expect( await page.evaluate( () => navigator.clipboard.readText() ) ).toMatch( /^curl -X GET 'http:\/\/127\.0\.0\.1:\d+\/n-plus-one\.bxm'$/ );
	} );

} );
