import { test, expect, Page, APIRequestContext } from '@playwright/test';

// Runs against the third harness server, which has bx-orm 1.7.2 or later (see playwright.config.ts). It announces onORMQuery, onORMFlush and
// onORMException, which Lens listens to. The harness app sets announceQueryParams=false and generateStatistics=true.
const ORM = `http://127.0.0.1:${ process.env.ORM_PORT || '8091' }`;
const BASE = '/~bxlens/index.bxm';
const API = `${ BASE }/api`;

test.use( { baseURL: ORM } );

async function signIn( page: Page ) {
	await page.goto( BASE );
	await page.fill( '#pw', 'lens-demo' );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

async function session( request: APIRequestContext ) {
	const r = await request.post( `${ BASE }/login`, { headers: { 'X-Lens-Login': '1' }, form: { password: 'lens-demo' } } );
	expect( r.status() ).toBe( 200 );
	return ( await ( await request.get( `${ API }/state` ) ).json() ).csrf as string;
}

/** The bar payload of a page, with the queries it ran. */
async function pageData( page: Page, path: string ) {
	await page.goto( path );
	await expect( page.locator( '#bxlens .lens' ) ).toBeVisible();
	return page.evaluate( () => JSON.parse( document.getElementById( 'bxlens-data' )!.textContent! ) );
}

test.describe( 'ORM events', () => {

	test( 'ORM SQL shows in the request with the ORM label, the kind, the time of the event and no parameter values', async ( { page } ) => {
		const data = await pageData( page, '/orm.bxm?save=1' );
		const orm = data.data.queries.filter( ( q: any ) => q.orm );
		expect( orm.length ).toBeGreaterThan( 2 );
		const kinds = orm.map( ( q: any ) => q.kind );
		expect( kinds ).toContain( 'insert' );
		expect( kinds ).toContain( 'select' );
		for ( const q of orm ) {
			expect( q.sql ).toContain( 'orm_books' );
			expect( q.datasource ).toBe( 'demo' );
			expect( q.span ).toBeGreaterThan( 0 );
			expect( q.ms ).toBeGreaterThanOrEqual( 0 );
			expect( q.file ).toContain( 'orm.bxm' );
			// The app keeps announceQueryParams off, so the events carry no values although queries.includeParams is on in this harness
			expect( q.params ).toBeUndefined();
		}
		const insert = orm.find( ( q: any ) => q.kind === 'insert' );
		expect( insert.rows ).toBe( 1 );
		// Each statement is a query span on the timeline
		const spans = data.data.spans.filter( ( s: any ) => s.type === 'query' && s.detail && s.detail.orm );
		expect( spans.length ).toBe( orm.length );
		expect( spans[ 0 ].detail.kind ).toBeTruthy();
	} );

	test( 'the bar labels the statement ORM and shows its kind', async ( { page } ) => {
		await pageData( page, '/orm.bxm?save=1' );
		await page.keyboard.press( 'Control+`' );
		await page.locator( '#bxlens .tab', { hasText: /^\s*Queries\b/ } ).first().click();
		const row = page.locator( '#bxlens tbody tr:visible', { hasText: 'orm_books' } ).first();
		await expect( row.locator( '.pill', { hasText: 'ORM' } ) ).toBeVisible();
		await expect( row.locator( '.pill', { hasText: /^(select|insert)$/ } ) ).toBeVisible();
	} );

	test( 'the console Queries page lists ORM SQL with the ORM label and the kind', async ( { page, request } ) => {
		await request.get( '/orm.bxm?save=1' );
		await signIn( page );
		await page.click( '.nav:has-text("Queries")' );
		await page.fill( 'input[placeholder="Filter statements"]', 'orm_books' );
		const row = page.locator( '.main section:visible tbody tr.row' ).first();
		await expect( row ).toContainText( 'orm_books' );
		await expect( row.locator( '.chipx', { hasText: /^ORM (select|insert)$/ } ) ).toBeVisible();
	} );

	test( 'a failed statement shows its error in the request, in the query statistics and in the ORM failures', async ( { page, request } ) => {
		const data = await pageData( page, '/orm.bxm?fail=1' );
		const failed = data.data.queries.filter( ( q: any ) => q.orm && q.error );
		expect( failed.length ).toBe( 1 );
		expect( failed[ 0 ].kind ).toBe( 'insert' );
		expect( failed[ 0 ].error ).toContain( 'truncation' );
		await expect( page.locator( '#failure' ) ).toHaveText( 'The insert failed' );

		await session( request );
		const orm = await ( await request.get( `${ API }/orm` ) ).json();
		expect( orm.totals.errors ).toBeGreaterThan( 0 );
		expect( orm.totals.failures[ 0 ].error ).toContain( 'truncation' );
		expect( orm.totals.failures[ 0 ].datasource ).toBe( 'demo' );
		const stats = await ( await request.get( `${ API }/queries` ) ).json();
		const st = stats.statements.find( ( s: any ) => s.orm && s.failures > 0 );
		expect( st ).toBeTruthy();
		expect( st.sql ).toContain( 'insert into orm_books' );
	} );

	test( 'the ORM page shows the integration, the event totals, the failures and the statistics', async ( { page, request } ) => {
		await request.get( '/orm.bxm?save=1' );
		await request.get( '/orm.bxm?fail=1' );
		await signIn( page );
		await page.click( '.nav:has-text("ORM")' );
		const sec = page.locator( '.main section:visible' );
		await expect( sec.locator( '#orm-integration' ) ).toContainText( 'On' );
		await expect( sec ).toContainText( 'lensHarness / demo' );
		await expect( sec ).toContainText( 'Recent failures' );
		await expect( sec ).toContainText( 'truncation' );
		await expect( sec ).toContainText( 'statistics on' );
		await expect( sec ).toContainText( 'Queries run' );
		// startup DDL and statements outside a request count too
		await session( request );
		const ds = ( await ( await request.get( `${ API }/orm` ) ).json() ).totals.datasources[ 0 ];
		expect( ds.ddl ).toBeGreaterThan( 0 );
		expect( ds.unattributed ).toBeGreaterThan( 0 );
		expect( ds.flushes ).toBeGreaterThan( 0 );
		expect( ds.flushInserts ).toBeGreaterThan( 0 );
	} );

	test( 'the ORM page refreshes by itself', async ( { page, request } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("ORM")' );
		const sec = page.locator( '.main section:visible' );
		const before = await sec.locator( '.tiles .card' ).first().locator( '.big' ).innerText();
		await request.get( '/orm.bxm?save=1' );
		await expect( async () => {
			expect( Number( await sec.locator( '.tiles .card' ).first().locator( '.big' ).innerText() ) ).toBeGreaterThan( Number( before ) );
		} ).toPass( { timeout: 12_000 } );
	} );

	test( 'an admin switches the statistics off and on from the ORM page', async ( { page, request } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("ORM")' );
		const sec = page.locator( '.main section:visible' );
		try {
			await sec.getByRole( 'button', { name: 'Turn statistics off' } ).click();
			await expect( sec ).toContainText( 'Statistics are off in this app: set generateStatistics in ormSettings, or turn them on here' );
			await expect( sec.getByRole( 'button', { name: 'Turn statistics on' } ) ).toBeEnabled();
			await sec.getByRole( 'button', { name: 'Turn statistics on' } ).click();
			await expect( sec.getByRole( 'button', { name: 'Turn statistics off' } ) ).toBeVisible();
		} finally {
			await session( request );
			const st = await ( await request.get( `${ API }/state` ) ).json();
			await request.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': st.csrf }, form: { app: 'lensHarness', enabled: 'true' } } );
		}
		const log = await ( await request.get( `${ API }/logfiles/read?file=bxlens-audit.log&lines=50&q=event%3Dorm.statistics` ) ).json();
		expect( log.lines.length ).toBeGreaterThan( 0 );
		expect( log.lines[ log.lines.length - 1 ] ).toContain( 'event=orm.statistics' );
	} );

	test( 'the statistics route: admin only, needs a CSRF token, checks the application, and the read-only rules', async ( { request, browser } ) => {
		const csrf = await session( request );
		const form = { app: 'lensHarness', enabled: 'true' };
		expect( ( await request.post( `${ API }/orm/statistics`, { form } ) ).status() ).toBe( 403 );
		expect( ( await request.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': csrf }, form: { app: 'nope', enabled: 'true' } } ) ).status() ).toBe( 404 );
		expect( ( await request.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': csrf }, form: { app: 'lensHarness', enabled: 'maybe' } } ) ).status() ).toBe( 400 );
		expect( ( await request.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': csrf }, form } ) ).status() ).toBe( 200 );
		// A viewer sees the page but cannot change anything
		const ctx = await browser.newContext( { baseURL: ORM } );
		const viewer = ctx.request;
		expect( ( await viewer.post( `${ BASE }/login`, { headers: { 'X-Lens-Login': '1' }, form: { password: 'lens-view' } } ) ).status() ).toBe( 200 );
		const vcsrf = ( await ( await viewer.get( `${ API }/state` ) ).json() ).csrf;
		expect( ( await viewer.get( `${ API }/orm` ) ).status() ).toBe( 200 );
		expect( ( await viewer.post( `${ API }/orm/statistics`, { headers: { 'X-Lens-CSRF': vcsrf }, form } ) ).status() ).toBe( 403 );
		expect( ( await viewer.post( `${ API }/integrations/orm`, { headers: { 'X-Lens-CSRF': vcsrf }, form: { enabled: 'false' } } ) ).status() ).toBe( 403 );
		const orm = await ( await viewer.get( `${ API }/orm` ) ).json();
		expect( orm.canChange ).toBe( false );
		expect( ( await ( await viewer.get( `${ API }/integrations` ) ).json() ).canChange ).toBe( false );
		await ctx.close();
	} );

} );

test.describe( 'Integrations with bx-orm installed', () => {

	test( 'the Modules page lists the integration as on, and an admin turns it off and on without a restart', async ( { page, request } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Modules")' );
		const row = page.locator( '#integrations tr[data-integration="orm"]' );
		await expect( row ).toContainText( 'BoxLang ORM' );
		await expect( row.locator( '.chipx' ) ).toHaveText( 'On' );
		const toggle = row.locator( 'input[type=checkbox]' );
		await expect( toggle ).toBeEnabled();
		await expect( toggle ).toBeChecked();
		try {
			await toggle.evaluate( ( el: HTMLInputElement ) => el.click() );
			await expect( row.locator( '.chipx' ) ).toHaveText( 'Available, off' );
			// Off: the page runs its SQL and Lens shows none of it
			const off = await pageData( page, '/orm.bxm?save=1' );
			expect( off.data.queries.filter( ( q: any ) => q.orm ) ).toHaveLength( 0 );
			await page.goto( BASE );
			await page.click( '.nav:has-text("Modules")' );
			await row.locator( 'input[type=checkbox]' ).evaluate( ( el: HTMLInputElement ) => el.click() );
			await expect( row.locator( '.chipx' ) ).toHaveText( 'On' );
		} finally {
			await session( request );
			const st = await ( await request.get( `${ API }/state` ) ).json();
			await request.post( `${ API }/integrations/orm`, { headers: { 'X-Lens-CSRF': st.csrf }, form: { enabled: 'true' } } );
		}
		// On again: the listeners are back
		const on = await pageData( page, '/orm.bxm?save=1' );
		expect( on.data.queries.filter( ( q: any ) => q.orm ).length ).toBeGreaterThan( 0 );
		const log = await ( await request.get( `${ API }/logfiles/read?file=bxlens-audit.log&lines=50&q=event%3Dintegration.change` ) ).json();
		expect( log.lines.length ).toBeGreaterThan( 0 );
		expect( log.lines[ log.lines.length - 1 ] ).toContain( 'event=integration.change' );
	} );

	test( 'the same status shows on the ORM page', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("ORM")' );
		await expect( page.locator( '#orm-integration' ) ).toContainText( 'On' );
		await expect( page.locator( '#orm-integration' ) ).toContainText( 'Listening' );
	} );

} );
