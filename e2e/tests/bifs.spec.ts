import { test, expect } from './lens';

test.describe( 'BIF timing', () => {

	test( 'the BIFs tab lists built-in functions with calls and time', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'BIFs' );
		const rows = page.locator( '#bxlens .panel table tbody tr' );
		await expect( rows.first() ).toBeVisible();
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'Built-in functions' );
		expect( await rows.count() ).toBeGreaterThan( 0 );
		// Lens's own functions are left out
		await expect( page.locator( '#bxlens .panel' ) ).not.toContainText( 'lensmessage' );
	} );

} );
