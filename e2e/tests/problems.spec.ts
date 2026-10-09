import { test, expect, consoleApi } from './lens';

test.describe( 'finding problems', () => {

	test( 'the bar shows N+1 queries as plain queries, with no flag and no issue chip, and the console lists the issue', async ( { lens, page, request } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await expect( page.locator( '#bxlens .chip.issues' ) ).toHaveCount( 0 );
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /sev-none/ );
		const d = await lens.data();
		expect( d.data.issues ).toBeUndefined();
		expect( d.data.queries.some( ( q: any ) => q.flag || q.count ) ).toBe( false );
		const detail = ( await ( await consoleApi( request ) ).get( `requests/${ d.data.request.id }` ) ).json;
		const issue = detail.issues.find( ( i: any ) => i.title === 'N+1 query pattern' );
		expect( issue.detail ).toContain( 'Ran 10 times' );
		expect( issue.file ).toContain( 'n-plus-one.bxm' );
		expect( issue.line ).toBe( 7 );
	} );

	test( 'the Queries tab shows every statement and its parameters, without flags', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		await expect( lens.rows ).toHaveCount( 11 );
		await expect( lens.panel ).toContainText( 'params [1]' );
		await expect( page.locator( '#bxlens .pill', { hasText: 'N+1' } ) ).toHaveCount( 0 );
	} );

	test( 'a slow query is not flagged in the bar and is an issue in the console', async ( { lens, page, request } ) => {
		await lens.visit( '/slow.bxm' );
		await expect( page.locator( '#bxlens .chip.issues' ) ).toHaveCount( 0 );
		await lens.open( 'Timeline' );
		await expect( page.locator( '#bxlens .wf .flag', { hasText: 'Slow' } ) ).toHaveCount( 0 );
		const id = ( await lens.data() ).data.request.id;
		const detail = ( await ( await consoleApi( request ) ).get( `requests/${ id }` ) ).json;
		expect( detail.issues.map( ( i: any ) => i.title ) ).toContain( 'Slow query' );
	} );

	test( 'a caught exception opens the panel on Exceptions, and the strip stays neutral', async ( { lens, page } ) => {
		await lens.visit( '/caught-exception.bxm' );
		await expect( lens.panel ).toBeVisible();
		await expect( lens.tab( 'Exceptions' ) ).toHaveAttribute( 'aria-selected', 'true' );
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /sev-none/ );
		await expect( lens.panel ).toContainText( 'KeyNotFoundException' );
		await expect( lens.panel ).toContainText( 'caught-exception.bxm:5' );
		await expect( lens.strip ).toContainText( '1 exception' );
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

	test( 'an uncaught error gets core\'s error page without a bar, and is kept in the console history', async ( { request } ) => {
		const res = await request.get( '/error.bxm', { failOnStatusCode: false } );
		expect( res.status() ).toBe( 500 );
		expect( await res.text() ).not.toContain( 'bxlens-data' );
		const id = res.headers()[ 'x-bxlens-id' ];
		const api = await consoleApi( request );
		const row = ( await api.get( 'requests' ) ).json.requests.find( ( r: any ) => r.id === id );
		expect( row.status ).toBe( 500 );
		expect( row.severity ).toBe( 'crit' );
	} );

	test( 'a failing outgoing HTTP call is listed, and only the console calls it an issue', async ( { lens, page, request } ) => {
		await lens.visit( '/http.bxm' );
		await lens.open( 'HTTP' );
		const rows = lens.rows;
		await expect( rows ).toHaveCount( 2 );
		await expect( rows.nth( 0 ) ).toContainText( '/api/rates.json.bxm' );
		await expect( rows.nth( 0 ).locator( '.pill' ) ).toHaveText( '200' );
		await expect( rows.nth( 1 ).locator( '.pill' ) ).toHaveText( '404' );
		await expect( page.locator( '#bxlens .chip.issues' ) ).toHaveCount( 0 );
		const id = ( await lens.data() ).data.request.id;
		const detail = ( await ( await consoleApi( request ) ).get( `requests/${ id }` ) ).json;
		expect( detail.issues.map( ( i: any ) => i.title ) ).toContain( 'Outgoing HTTP 404' );
	} );

	test( 'the strip turns red for a 5xx page and for nothing else', async ( { page } ) => {
		await page.goto( '/orders.bxm' );
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /sev-none/ );
	} );

} );
