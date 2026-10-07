import { test, expect } from './lens';

test.describe( 'finding problems', () => {

	test( 'N+1 queries turn the strip amber and are listed on Issues', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		const chip = page.locator( '#bxlens .chip.issues' );
		await expect( chip ).toContainText( '1 issue' );
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /sev-warn/ );
		await lens.open( 'Issues' );
		await expect( lens.panel ).toContainText( 'N+1 query pattern' );
		await expect( lens.panel ).toContainText( 'Ran 10 times' );
		await expect( lens.panel ).toContainText( 'n-plus-one.bxm:7' );
	} );

	test( 'the Queries tab shows every statement, its parameters and the N+1 flag', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		await expect( lens.rows ).toHaveCount( 11 );
		await expect( lens.panel ).toContainText( 'params [1]' );
		await expect( page.locator( '#bxlens .pill', { hasText: 'N+1: ran 10 times' } ).first() ).toBeVisible();
	} );

	test( 'a slow query is flagged', async ( { lens, page } ) => {
		await lens.visit( '/slow.bxm' );
		await expect( page.locator( '#bxlens .chip.issues' ) ).toBeVisible();
		await lens.open( 'Issues' );
		await expect( lens.panel ).toContainText( 'Slow query' );
		await lens.open( 'Timeline' );
		await expect( page.locator( '#bxlens .wf .flag', { hasText: 'Slow' } ).first() ).toBeVisible();
	} );

	test( 'a caught exception opens the panel on Issues by itself', async ( { lens, page } ) => {
		await lens.visit( '/caught-exception.bxm' );
		await expect( lens.panel ).toBeVisible();
		await expect( lens.tab( 'Issues' ) ).toHaveAttribute( 'aria-selected', 'true' );
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /sev-crit/ );
		await expect( lens.panel ).toContainText( 'KeyNotFoundException' );
		await expect( lens.panel ).toContainText( 'caught-exception.bxm:5' );
	} );

	test( 'the Exceptions tab shows the location, the Java stack and an editor link', async ( { lens, page } ) => {
		await lens.visit( '/caught-exception.bxm' );
		await lens.open( 'Exceptions' );
		await expect( lens.panel ).toContainText( 'was not found' );
		const link = page.locator( '#bxlens .card a' ).first();
		expect( await link.getAttribute( 'href' ) ).toMatch( /^vscode:\/\/file\/.*caught-exception\.bxm:5$/ );
		await page.locator( '#bxlens details summary', { hasText: 'Java stack' } ).click();
		await expect( page.locator( '#bxlens details pre' ) ).toContainText( 'ortus.boxlang' );
	} );

	test( 'an uncaught error gets core\'s error page without a bar, and is kept in History', async ( { lens, page, request } ) => {
		const res = await request.get( '/error.bxm', { failOnStatusCode: false } );
		expect( res.status() ).toBe( 500 );
		expect( await res.text() ).not.toContain( 'bxlens-data' );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'History' );
		const row = page.locator( '#bxlens tbody tr', { hasText: '/error.bxm' } ).first();
		await expect( row ).toBeVisible();
		await expect( row ).toContainText( '500' );
	} );

	test( 'a failing outgoing HTTP call is flagged', async ( { lens, page } ) => {
		await lens.visit( '/http.bxm' );
		await lens.open( 'HTTP' );
		const rows = lens.rows;
		await expect( rows ).toHaveCount( 2 );
		await expect( rows.nth( 0 ) ).toContainText( '/api/rates.json.bxm' );
		await expect( rows.nth( 0 ).locator( '.pill' ) ).toHaveText( '200' );
		await expect( rows.nth( 1 ).locator( '.pill' ) ).toHaveText( '404' );
		await expect( page.locator( '#bxlens .chip.issues' ) ).toBeVisible();
	} );

} );
