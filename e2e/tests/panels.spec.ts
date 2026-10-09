import { test, expect } from './lens';

test.describe( 'panels', () => {

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

	test( 'Copy buttons copy SQL, cURL and file locations', async ( { lens, page, context } ) => {
		await context.grantPermissions( [ 'clipboard-read', 'clipboard-write' ] );
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		await page.locator( '#bxlens .abtn', { hasText: 'Copy SQL' } ).first().click();
		await expect( page.locator( '#bxlens .toast' ) ).toContainText( 'SQL copied' );
		expect( await page.evaluate( () => navigator.clipboard.readText() ) ).toContain( 'SELECT id, customer_id, total FROM orders' );
		await lens.open( 'Request' );
		await page.locator( '#bxlens .abtn', { hasText: 'Copy as cURL' } ).click();
		expect( await page.evaluate( () => navigator.clipboard.readText() ) ).toMatch( /^# handled by server \S+ \S+ \([0-9a-f]{8}\)\ncurl -X 'GET' 'http:\/\/127\.0\.0\.1:\d+\/n-plus-one\.bxm'$/ );
	} );


	test( 'the cURL builder closes and escapes single quotes in the method, address and query', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		const cmd = await page.evaluate( () => ( window as any ).Alpine.$data( document.getElementById( 'bxlens' ) ).curl( "GE'T", "http://h/a'b", "q='1" ) );
		expect( cmd ).toBe( "curl -X 'GE'\\''T' 'http://h/a'\\''b?q='\\''1'" );
	} );

} );
