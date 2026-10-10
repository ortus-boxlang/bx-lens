import { test, expect, APIRequestContext } from '@playwright/test';
import { existsSync } from 'fs';
import { join } from 'path';

// Runs against the second harness server, started without a BoxLang+ license (see playwright.config.ts)
const FREE = `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }`;
const API = '/~bxlens/index.bxm/api';

test.use( { baseURL: FREE } );

async function session( request: APIRequestContext ) {
	const r = await request.post( '/~bxlens/index.bxm/login', { headers: { 'X-Lens-Login': '1' }, form: { password: 'lens-demo' } } );
	expect( r.status() ).toBe( 200 );
	const st = await ( await request.get( `${ API }/state` ) ).json();
	return { csrf: st.csrf as string, state: st };
}

test.describe( 'Free: what is open and what is locked', () => {

	test( 'the console says Free and lists every gated feature as off', async ( { request } ) => {
		const { state } = await session( request );
		expect( state.license.state ).toBe( 'none' );
		for ( const f of [ 'diskStore', 'fullHistory', 'ai', 'cost', 'taskActions', 'cacheActions', 'logDownload', 'bundle', 'heapDump', 'barDesigner', 'ormStats' ] ) {
			expect( state.plus[ f ], f ).toBe( false );
		}
		expect( state.diskStore ).toBe( false );
	} );

	test( 'ORM statistics are locked', async ( { request } ) => {
		await session( request );
		const r = await request.get( `${ API }/orm` );
		expect( r.status() ).toBe( 403 );
		expect( ( await r.json() ).plus ).toBe( true );
	} );

	test( 'the ORM integration is free to see and says bx-orm is not installed', async ( { page, request } ) => {
		const { csrf } = await session( request );
		const list = await ( await request.get( `${ API }/integrations` ) ).json();
		expect( list.integrations[ 0 ].id ).toBe( 'orm' );
		expect( list.integrations[ 0 ].status ).toBe( 'notInstalled' );
		// Not installed: switching on is refused, also on the free tier
		const on = await request.post( `${ API }/integrations/orm`, { headers: { 'X-Lens-CSRF': csrf }, form: { enabled: 'true' } } );
		expect( on.status() ).toBe( 409 );
		// Changing the ORM statistics is a Plus feature: refused before anything else
		const stats = await request.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': csrf }, form: { app: 'x', enabled: 'true' } } );
		expect( stats.status() ).toBe( 403 );
		expect( ( await stats.json() ).plus ).toBe( true );
		await page.goto( '/~bxlens/index.bxm' );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await page.click( '.nav:has-text("ORM")' );
		const sec = page.locator( '.main section:visible' );
		await expect( sec.locator( '#orm-integration .chipx' ) ).toHaveText( 'Not installed' );
		await expect( sec.locator( '#orm-integration input[type=checkbox]' ) ).toBeDisabled();
		await expect( sec ).toContainText( 'Install bx-orm to enable' );
		await expect( sec.locator( '.plusnote' ) ).toContainText( 'BoxLang+' );
	} );

	test( 'the free pages and reads still work', async ( { request } ) => {
		await request.get( '/orders.bxm' );
		await session( request );
		for ( const p of [ 'overview', 'requests', 'inflight', 'executors', 'tasks', 'datasources', 'caches', 'logfiles', 'environment', 'modules', 'integrations', 'system', 'threads', 'queries', 'errors', 'reports', 'settings' ] ) {
			const r = await request.get( `${ API }/${ p }` );
			expect( r.status(), p ).toBe( 200 );
		}
		const log = await request.get( `${ API }/logfiles/read?file=bxlens-audit.log` );
		expect( log.status() ).toBe( 200 );
		const caches = await ( await request.get( `${ API }/caches` ) ).json();
		const keys = await request.get( `${ API }/caches/${ encodeURIComponent( caches.caches[ 0 ].name ) }/keys` );
		expect( keys.status() ).toBe( 200 );
	} );

	test( 'the Plus features are refused with a clear message', async ( { request } ) => {
		const { csrf } = await session( request );
		const h = { 'X-Lens-CSRF': csrf };
		const refused = async ( r: any, what: string ) => {
			expect( r.status(), what ).toBe( 403 );
			const j = await r.json();
			expect( j.plus, what ).toBe( true );
			expect( j.error, what ).toContain( 'BoxLang+' );
		};
		await refused( await request.post( `${ API }/tasks/bxschedule/cleanup-sessions/pause`, { headers: h } ), 'task action' );
		await refused( await request.get( `${ API }/cachevalue/default?key=x` ), 'cache value' );
		await refused( await request.post( `${ API }/caches/default/clear`, { headers: h } ), 'cache clear' );
		await refused( await request.get( `${ API }/logfiles/download?file=bxlens-audit.log` ), 'log download' );
		await refused( await request.get( `${ API }/bundle` ), 'bundle' );
		await refused( await request.post( `${ API }/heapdump`, { headers: h, form: { confirm: 'true' } } ), 'heap dump' );
		await refused( await request.post( `${ API }/bar/layout`, { headers: h, form: { layout: '[]' } } ), 'bar layout' );
		const ai = await request.post( `${ API }/ai/ask`, { headers: h, form: { question: 'why slow?' } } );
		expect( ai.status() ).toBe( 409 );
		expect( ( await ai.json() ).error ).toContain( 'BoxLang+' );
	} );

	test( 'only 25 requests are kept and nothing is written to the disk store', async ( { request } ) => {
		for ( let i = 0; i < 30; i++ ) {
			await request.get( '/orders.bxm' );
		}
		await session( request );
		// The cap is applied by the watchdog every few seconds
		await expect.poll( async () => ( await ( await request.get( `${ API }/requests` ) ).json() ).capacity, { timeout: 15_000 } ).toBe( 25 );
		const reps = await ( await request.get( `${ API }/reports` ) ).json();
		expect( reps.persisted ).toBe( false );
		expect( reps.lifetime ).toBeUndefined();
		expect( existsSync( join( __dirname, '..', '..', 'harness', '.run-free', 'home', 'lens-data' ) ) ).toBe( false );
	} );

	test( 'the bar works but request cost and the slow sample are locked', async ( { page } ) => {
		await page.goto( '/orders.bxm' );
		await page.locator( '#bxlens .lens' ).waitFor();
		await page.keyboard.press( 'Control+`' );
		await page.locator( '#bxlens .tab', { hasText: /^\s*Request/ } ).first().click();
		await expect( page.locator( '#bxlens .panel' ) ).toContainText( 'BoxLang+' );
		await page.locator( '#bxlens .tab', { hasText: /^\s*Timeline/ } ).first().click();
		await expect( page.locator( '#bxlens .wf .r' ).first() ).toBeVisible();
	} );

	test( 'the console shows lock notes on the Plus actions', async ( { page } ) => {
		await page.goto( '/~bxlens/index.bxm' );
		await page.fill( '#pw', 'lens-demo' );
		await page.click( '.go' );
		await expect( page.locator( '.shell .app' ) ).toBeVisible();
		await expect( page.locator( '.top .lic' ) ).toContainText( 'Free' );
		await page.click( '.nav:has-text("Tasks")' );
		await expect( page.locator( '.main section:visible .plusnote' ) ).toContainText( 'BoxLang+' );
		await expect( page.locator( 'button:has-text("Reload all from tasks.json")' ) ).toBeDisabled();
		await page.click( '.nav:has-text("Environment")' );
		await expect( page.locator( 'a:has-text("Diagnostic bundle")' ) ).toHaveClass( /disabled/ );
		await page.click( '.nav:has-text("System")' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'Heap dumps need a BoxLang+ license' );
	} );

} );
