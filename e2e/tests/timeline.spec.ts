import { test, expect } from './lens';

test.describe( 'timeline', () => {

	test( 'shows templates and queries on one waterfall with nesting', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Timeline' );
		const rows = page.locator( '#bxlens .wf .r' );
		await expect( rows.first() ).toBeVisible();
		expect( await rows.count() ).toBeGreaterThanOrEqual( 13 ); // 11 queries plus 3 templates
		await expect( rows.filter( { hasText: 'n-plus-one.bxm' } ).first() ).toBeVisible();
		await expect( rows.filter( { hasText: 'SELECT name FROM customers' } ).first() ).toBeVisible();
	} );

	test( 'clicking a row opens a drawer with details and an editor link', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Timeline' );
		await page.locator( '#bxlens .wf .r', { hasText: 'SELECT name FROM customers' } ).first().click();
		const drawer = page.locator( '#bxlens .drawer' );
		await expect( drawer ).toBeVisible();
		await expect( drawer ).toContainText( 'Query' );
		await expect( drawer ).toContainText( 'SELECT name FROM customers WHERE id = ?' );
		const edit = drawer.locator( 'a', { hasText: 'Open in editor' } );
		expect( await edit.getAttribute( 'href' ) ).toMatch( /^vscode:\/\/file\/.*n-plus-one\.bxm:7$/ );
	} );

	test( 'type filters and search narrow the rows', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Timeline' );
		const rows = page.locator( '#bxlens .wf .r' );
		const all = await rows.count();
		await page.locator( '#bxlens .fchip', { hasText: 'Query' } ).click();
		await expect( rows.filter( { hasText: 'SELECT' } ) ).toHaveCount( 0 );
		await page.locator( '#bxlens .fchip', { hasText: 'Query' } ).click();
		await expect( rows ).toHaveCount( all );
		await page.locator( '#bxlens-q' ).fill( 'header' );
		await expect( rows ).toHaveCount( 1 );
		await expect( rows.first() ).toContainText( 'header.bxm' );
	} );

	test( 'zoom and pan change the visible range and reset restores it', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Timeline' );
		const range = page.locator( '#bxlens .toolbar .mono.muted' );
		const full = await range.textContent();
		await page.locator( '#bxlens .abtn[title="Zoom in"]' ).click();
		const zoomed = await range.textContent();
		expect( zoomed ).not.toBe( full );
		await page.locator( '#bxlens .abtn[title="Pan right"]' ).click();
		expect( await range.textContent() ).not.toBe( zoomed );
		await page.locator( '#bxlens .abtn', { hasText: 'Reset' } ).click();
		expect( await range.textContent() ).toBe( full );
	} );

	test( 'the search box is focused with /', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		await page.keyboard.press( '/' );
		await expect( page.locator( '#bxlens-q' ) ).toBeFocused();
	} );

	test( 'user functions appear when the functions collector is on', async ( { lens, page } ) => {
		await lens.visit( '/functions.bxm' );
		await lens.open( 'Timeline' );
		await expect( page.locator( '#bxlens .fchip', { hasText: 'Function' } ) ).toBeVisible();
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'report()' } ).first() ).toBeVisible();
		await lens.open( 'Templates' );
		await expect( page.locator( '#bxlens .tree' ) ).toContainText( 'fib()' );
		await expect( page.locator( '#bxlens .tree' ) ).toContainText( 'report()' );
	} );

	test( 'transactions are spans, and a rollback is flagged', async ( { lens, page } ) => {
		await lens.visit( '/transaction.bxm' );
		await lens.open( 'Timeline' );
		const tx = page.locator( '#bxlens .wf .r' ).filter( { has: page.locator( '.lbl > span:first-child', { hasText: /^transaction$/ } ) } );
		await expect( tx ).toHaveCount( 2 );
		await expect( tx.filter( { hasText: 'Rollback' } ) ).toHaveCount( 1 );
	} );

} );
