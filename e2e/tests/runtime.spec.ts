import { test, expect } from './lens';
import { Page } from '@playwright/test';

const BASE = '/~bxlens/index.bxm';

async function signIn( page: Page, pw = 'lens-demo' ) {
	await page.goto( BASE );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

test.describe( 'runtime snapshot and configuration', () => {

	test( 'the bar Runtime tab shows versions, system, heap, cache names and main settings from the runtime', async ( { lens, page } ) => {
		await lens.visit( '/index.bxm' );
		await lens.open( 'Runtime' );
		const panel = page.locator( '#bxlens .panel' );
		await expect( panel ).toContainText( 'BoxLang' );
		await expect( panel ).toContainText( 'Java' );
		await expect( panel ).toContainText( 'Heap' );
		await expect( panel ).toContainText( 'Uptime' );
		await expect( panel ).toContainText( 'Caches' );
		await expect( panel ).toContainText( 'Time zone' );
		const d = await lens.data();
		expect( d.data.runtime.boxlang.version ).toMatch( /\d+\.\d+/ );
		expect( d.data.runtime.caches ).toContain( 'default' );
		// No secret-looking value is in the snapshot
		expect( JSON.stringify( d.data.runtime ) ).not.toContain( 'lens-demo' );
	} );

	test( 'the snapshot is cached: two requests close together carry the same figures', async ( { lens, request } ) => {
		const a = await request.get( '/index.bxm' );
		const b = await request.get( '/index.bxm' );
		expect( a.status() ).toBe( 200 );
		expect( b.status() ).toBe( 200 );
		await lens.visit( '/index.bxm' );
		const one = ( await lens.data() ).data.runtime;
		await lens.visit( '/index.bxm' );
		const two = ( await lens.data() ).data.runtime;
		expect( two.boxlang ).toEqual( one.boxlang );
		expect( two.settings ).toEqual( one.settings );
	} );

	test( 'the console Configuration page groups the effective settings with their source and filters them', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Configuration")' );
		await expect( page.locator( 'section:visible h1' ) ).toContainText( 'Configuration' );
		await expect( page.locator( 'section:visible .card h3', { hasText: 'Runtime' } ).first() ).toBeVisible();
		await expect( page.locator( 'section:visible .card h3', { hasText: 'Paths' } ) ).toBeVisible();
		await expect( page.locator( 'section:visible td.mono', { hasText: 'debugMode' } ) ).toBeVisible();
		await expect( page.locator( 'section:visible .chipx', { hasText: /default|boxlang.json|environment variable/ } ).first() ).toBeVisible();
		await page.fill( 'input[aria-label="Filter the configuration"]', 'sessionTimeout' );
		await expect( page.locator( 'section:visible td.mono', { hasText: 'sessionTimeout' } ) ).toBeVisible();
		await expect( page.locator( 'section:visible td.mono', { hasText: 'debugMode' } ) ).toHaveCount( 0 );
	} );

	test( 'a viewer reads the Configuration page without the paths, security and logging areas', async ( { page } ) => {
		await signIn( page, 'lens-view' );
		await page.click( '.nav:has-text("Configuration")' );
		await expect( page.locator( 'section:visible .card h3', { hasText: 'Runtime' } ).first() ).toBeVisible();
		await expect( page.locator( 'section:visible .card h3', { hasText: 'Paths' } ) ).toHaveCount( 0 );
		const groups = await page.evaluate( async () => ( await ( await fetch( '/~bxlens/index.bxm/api/configuration', { credentials: 'same-origin' } ) ).json() ).groups.map( ( g: any ) => g.name ) );
		expect( groups ).not.toContain( 'Paths' );
		expect( groups ).not.toContain( 'Security' );
		expect( groups ).not.toContain( 'Logging' );
		expect( groups ).toContain( 'Runtime' );
	} );

} );
