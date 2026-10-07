import { test, expect } from './lens';

test.describe( 'extending Lens', () => {

	test( 'application code can add panels with lensPanel()', async ( { lens, page } ) => {
		await lens.visit( '/extend.bxm' );
		await lens.open( 'Harness' );
		const rows = lens.rows;
		await expect( rows ).toHaveCount( 3 );
		await expect( lens.panel ).toContainText( 'Feature' );
		await expect( rows.nth( 1 ) ).toContainText( 'demoLens module' );
		await expect( lens.tab( 'Harness' ) ).toContainText( '3' );
	} );

	test( 'panel spans are drawn on the timeline too', async ( { lens, page } ) => {
		await lens.visit( '/extend.bxm' );
		await lens.open( 'Timeline' );
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'warm up' } ) ).toBeVisible();
		await expect( page.locator( '#bxlens .wf .r', { hasText: 'render' } ) ).toBeVisible();
		await lens.open( 'Phases' );
		await expect( lens.panel ).toContainText( 'warm up' );
	} );

	test( 'another module adds a panel through onLensRegister and onLensCollect', async ( { lens, page } ) => {
		await lens.visit( '/orders.bxm' );
		await lens.open( 'Demo module' );
		await expect( lens.panel ).toContainText( 'demoLens' );
		await expect( lens.panel ).toContainText( 'onLensCollect' );
		const data = await lens.data();
		await expect( lens.panel ).toContainText( data.data.request.id );
	} );

	test( 'panels are not left over on the next request', async ( { lens, page } ) => {
		await lens.visit( '/extend.bxm' );
		await lens.visit( '/orders.bxm' );
		await expect( lens.tab( 'Harness' ) ).toHaveCount( 0 );
		await expect( lens.tab( 'Demo module' ) ).toHaveCount( 1 );
	} );

} );
