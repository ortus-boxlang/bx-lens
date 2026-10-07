import { test, expect } from './lens';

test.describe( 'the bar', () => {

	test( 'starts as a collapsed health strip with the key numbers', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await expect( lens.panel ).toBeHidden();
		await expect( lens.strip ).toContainText( 'GET 200' );
		await expect( lens.strip ).toContainText( '/orders.bxm' );
		await expect( lens.strip ).toContainText( /time\s+[\d.]+ ms/ );
		await expect( lens.strip ).toContainText( /mem\s+[\d.]+ MB/ );
		await expect( lens.strip ).toContainText( /sql\s+1/ );
		await expect( lens.strip ).toContainText( /tpl\s+3/ );
		// A healthy page has no issues chip
		await expect( page.locator( '#bxlens .chip.issues' ) ).toBeHidden();
	} );

	test( 'a chip opens the matching tab', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await page.locator( '#bxlens .chip', { hasText: /^sql/ } ).click();
		await expect( lens.panel ).toBeVisible();
		await expect( lens.tab( 'Queries' ) ).toHaveAttribute( 'aria-selected', 'true' );
	} );

	test( 'Ctrl+` toggles the panel and number keys switch tabs', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await page.keyboard.press( 'Control+`' );
		await expect( lens.panel ).toBeVisible();
		await page.keyboard.press( '3' );
		await expect( lens.tab( 'Queries' ) ).toHaveAttribute( 'aria-selected', 'true' );
		await page.keyboard.press( '2' );
		await expect( lens.tab( 'Timeline' ) ).toHaveAttribute( 'aria-selected', 'true' );
		await page.keyboard.press( 'Control+`' );
		await expect( lens.panel ).toBeHidden();
	} );

	test( 'the open tab and panel state survive a reload', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Cache' );
		await page.reload();
		await expect( lens.panel ).toBeVisible();
		await expect( lens.tab( 'Cache' ) ).toHaveAttribute( 'aria-selected', 'true' );
	} );

	test( 'the panel can be resized by dragging its edge', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Timeline' );
		const before = ( await lens.panel.boundingBox() )!.height;
		const handle = ( await page.locator( '#bxlens .handle' ).boundingBox() )!;
		await page.mouse.move( handle.x + handle.width / 2, handle.y + 3 );
		await page.mouse.down();
		await page.mouse.move( handle.x + handle.width / 2, handle.y - 120, { steps: 6 } );
		await page.mouse.up();
		const after = ( await lens.panel.boundingBox() )!.height;
		expect( after ).toBeGreaterThan( before + 80 );
	} );

	test( 'the panel can be detached and docked again', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Timeline' );
		await page.locator( '#bxlens .ibtn[title^="Detach"]' ).click();
		await expect( page.locator( '#bxlens .lens' ) ).toHaveClass( /detached/ );
		await expect( page.locator( '#bxlens .popbar' ) ).toBeVisible();
		await expect( lens.panel ).toBeVisible();
		await page.locator( '#bxlens .popbar button', { hasText: 'Dock' } ).click();
		await expect( page.locator( '#bxlens .lens' ) ).not.toHaveClass( /detached/ );
	} );

	test( 'the theme follows the OS and can be toggled', async ( { lens, page } ) => {
		await page.emulateMedia( { colorScheme: 'dark' } );
		await lens.visit( '/orders.bxm' );
		await expect( lens.root ).toHaveAttribute( 'data-theme', 'dark' );
		await page.locator( '#bxlens .ibtn[title="Toggle theme"]' ).click();
		await expect( lens.root ).toHaveAttribute( 'data-theme', 'light' );
	} );

	test( 'light OS preference gives the light theme', async ( { lens, page } ) => {
		await page.emulateMedia( { colorScheme: 'light' } );
		await lens.visit( '/orders.bxm' );
		await expect( lens.root ).toHaveAttribute( 'data-theme', 'light' );
	} );

	test( 'the bar does not restyle the host page and the host does not restyle the bar', async ( { lens, page } ) => {
		await lens.visit( '/n-plus-one.bxm' );
		await lens.open( 'Queries' );
		// The harness page styles every table with a white background. Lens tables must stay transparent.
		const bg = await page.locator( '#bxlens table' ).first().evaluate( ( el ) => getComputedStyle( el ).backgroundColor );
		expect( bg ).toBe( 'rgba(0, 0, 0, 0)' );
		const h1 = await page.locator( 'main h1' ).evaluate( ( el ) => getComputedStyle( el ).fontSize );
		expect( parseFloat( h1 ) ).toBeGreaterThan( 20 );
	} );

	test( 'works at phone width without horizontal page scroll', async ( { lens, page } ) => {
		await page.setViewportSize( { width: 400, height: 760 } );
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Timeline' );
		const overflow = await page.evaluate( () => document.documentElement.scrollWidth - window.innerWidth );
		expect( overflow ).toBeLessThanOrEqual( 0 );
	} );

	test( 'no console errors from the bar', async ( { lens, page } ) => {
		const errors: string[] = [];
		page.on( 'pageerror', ( e ) => errors.push( e.message ) );
		page.on( 'console', ( m ) => { if ( m.type() === 'error' && !m.text().includes( 'favicon' ) && !m.text().includes( '404' ) ) { errors.push( m.text() ); } } );
		await lens.visit( '/n-plus-one.bxm' );
		for ( const t of [ 'Issues', 'Timeline', 'Queries', 'Templates', 'HTTP', 'Exceptions', 'Messages', 'Timers', 'Cache', 'Modules', 'Request', 'Scopes', 'Runtime', 'History' ] ) {
			await lens.open( t );
		}
		expect( errors ).toEqual( [] );
	} );

} );
