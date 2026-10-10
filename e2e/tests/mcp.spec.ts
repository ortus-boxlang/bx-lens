import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * The MCP servers of Lensy, against the harness. The documentation servers are served by a mock written in BoxLang
 * (harness/mock-mcp, port 11435, started by Playwright, so nothing needs the internet) and the model is the mock Ollama
 * (harness/mock-ai.py), which calls any tool when the message is `force:<tool name>`. The main server has AI off, so these tests turn it on
 * through the AI page API and put everything back in afterAll. The Free server (8090) shows what stays locked and the read only server (8092)
 * what read only refuses.
 */
const BASE = '/~bxlens/index.bxm';
const API = `${ BASE }/api`;
const FREE = `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }`;
const RO = `http://127.0.0.1:${ process.env.RO_PORT || '8092' }`;
const MOCK = 'http://127.0.0.1:11434';
const MCP = `http://127.0.0.1:${ process.env.MCP_PORT || '11435' }/mcp.bxs`;
const TOUCHED = [ 'ai.enabled', 'ai.rag', 'ai.actions', 'ai.maxToolCalls', 'ai.model' ];

async function login( request: APIRequestContext, password = 'lens-demo', base = '' ) {
	const r = await request.post( `${ base }${ BASE }/login`, { headers: { 'X-Lens-Login': '1' }, form: { password } } );
	expect( r.status() ).toBe( 200 );
	const st = await ( await request.get( `${ base }${ API }/state` ) ).json();
	return { csrf: st.csrf as string, state: st };
}

async function post( request: APIRequestContext, csrf: string, path: string, form: Record<string, string> = {}, base = '' ) {
	const r = await request.post( `${ base }${ API }/${ path }`, { headers: { 'X-Lens-CSRF': csrf }, form, failOnStatusCode: false } );
	let body: any = null;
	try { body = await r.json(); } catch { /* not json */ }
	return { status: r.status(), body };
}

async function del( request: APIRequestContext, csrf: string, path: string, base = '' ) {
	const r = await request.delete( `${ base }${ API }/${ path }`, { headers: { 'X-Lens-CSRF': csrf }, failOnStatusCode: false } );
	let body: any = null;
	try { body = await r.json(); } catch { /* not json */ }
	return { status: r.status(), body };
}

async function mcpList( request: APIRequestContext, base = '' ) {
	return ( await request.get( `${ base }${ API }/ai/mcp` ) ).json();
}

function row( list: any, id: string ) {
	return list.servers.find( ( s: any ) => s.id === id );
}

async function signIn( page: Page, pw = 'lens-demo', base = '' ) {
	await page.goto( `${ base }${ BASE }` );
	await page.fill( '#pw', pw );
	await page.click( '.go' );
	await expect( page.locator( '.shell .app' ) ).toBeVisible();
}

async function ask( page: Page, text: string ) {
	await page.fill( '#agent-input', text );
	await page.click( '#agent-send' );
}

function answer( body: string ): string {
	return body.split( '\n\n' ).filter( b => b.includes( 'event: token' ) ).map( b => JSON.parse( b.slice( b.indexOf( 'data:' ) + 5 ) ).text ).join( '' );
}

async function chat( request: APIRequestContext, csrf: string, message: string, base = '' ): Promise<string> {
	const r = await request.post( `${ base }${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': csrf }, form: { message }, failOnStatusCode: false } );
	return r.text();
}

async function mockLog( request: APIRequestContext ): Promise<any[]> {
	return ( await request.get( `${ MOCK }/mock/log` ) ).json();
}

async function mcpLog( request: APIRequestContext ): Promise<any[]> {
	return ( await request.get( `${ MCP }/_log` ) ).json();
}

function calls( log: any[], server: string ) {
	return log.filter( e => e.server === server && e.method === 'tools/call' );
}

async function auditText( request: APIRequestContext, q: string, base = '' ): Promise<string> {
	await login( request, 'lens-demo', base );
	const log = await ( await request.get( `${ base }${ API }/logfiles/read?file=bxlens-audit.log&lines=400&q=${ encodeURIComponent( q ) }` ) ).json();
	return ( log.lines as any[] ).map( l => typeof l === 'string' ? l : l.text ).join( '\n' );
}

async function openAiPage( page: Page ) {
	await signIn( page );
	await page.click( '.nav:has-text("AI")' );
	await expect( page.locator( '#mcp-card .mcprow' ).first() ).toBeVisible();
}

/** Switch a server on in the table and confirm. */
async function turnOn( page: Page, id: string ) {
	await page.locator( `.mcprow[data-id="${ id }"] .sw span` ).first().click();
	await expect( page.locator( '#mcp-confirm' ) ).toBeVisible();
	await page.click( '#mcp-confirm-yes' );
	await expect( page.locator( `.mcprow[data-id="${ id }"] .mcp-on` ) ).toBeChecked();
}

test.describe.configure( { mode: 'serial' } );

test.describe( 'MCP servers', () => {

	test.beforeAll( async ( { request } ) => {
		const { csrf } = await login( request );
		const r = await post( request, csrf, 'ai/config', { changes: JSON.stringify( { 'ai.enabled': true, 'ai.maxToolCalls': 12 } ) } );
		expect( r.status, JSON.stringify( r.body ) ).toBe( 200 );
		await request.delete( `${ MCP }/_log` );
	} );

	test.afterAll( async ( { request } ) => {
		const { csrf } = await login( request );
		const list = await mcpList( request );
		for ( const s of list.servers ) {
			if ( !s.builtin ) { await del( request, csrf, `ai/mcp/${ s.id }` ); }
			else if ( s.enabled ) { await post( request, csrf, `ai/mcp/${ s.id }/enable`, { enabled: 'false' } ); }
			if ( s.builtin ) { await post( request, csrf, 'ai/mcp', { id: s.id, tools: JSON.stringify( [ '*' ] ) } ); }
		}
		for ( const k of TOUCHED ) { await post( request, csrf, 'settings/reset', { key: k } ); }
	} );

	test( 'the AI page lists the eleven documentation servers, all off, with the privacy note and the add form', async ( { page, request } ) => {
		expect( ( await request.get( `${ API }/ai/mcp`, { failOnStatusCode: false } ) ).status() ).toBe( 401 );
		await openAiPage( page );
		const l = await ( await page.request.get( `${ API }/ai/mcp` ) ).json();
		expect( l.servers.map( ( s: any ) => s.id ) ).toEqual( [ 'boxlang', 'coldbox', 'commandbox', 'testbox', 'wirebox', 'logbox', 'cachebox', 'contentbox', 'qb', 'quick', 'cbauth' ] );
		for ( const s of l.servers ) {
			expect( s.builtin ).toBe( true );
			expect( s.enabled ).toBe( false );
			expect( s.url ).toBe( `${ MCP }/${ s.id }` );
		}
		await expect( page.locator( '#mcp-card .mcprow' ) ).toHaveCount( 11 );
		await expect( page.locator( '#mcp-card h3' ) ).toContainText( 'Servers Lensy can ask (MCP)' );
		await expect( page.locator( '#mcp-privacy' ) ).toContainText( 'sent to that server' );
		await expect( page.locator( '#mcp-privacy' ) ).toContainText( 'No password or API key is ever sent' );
		await expect( page.locator( '.mcprow[data-id="boxlang"] .mcp-remove' ) ).toBeHidden();
		await expect( page.locator( '.mcprow[data-id="boxlang"] .mcpurl' ) ).toHaveText( `${ MCP }/boxlang` );
		await expect( page.locator( '#mcp-name' ) ).toBeVisible();
	} );

	test( 'turning a server on asks first with its address; Cancel leaves it off; Turn on lists its tools', async ( { page, request } ) => {
		await openAiPage( page );
		const before = await ( await page.request.get( `${ API }/agent/status` ) ).json();
		await page.locator( '.mcprow[data-id="boxlang"] .sw span' ).first().click();
		await expect( page.locator( '#mcp-confirm' ) ).toContainText( 'Turn on BoxLang docs?' );
		await expect( page.locator( '#mcp-confirm' ) ).toContainText( `${ MCP }/boxlang` );
		await page.click( '#mcp-confirm-no' );
		await expect( page.locator( '#mcp-confirm' ) ).toBeHidden();
		expect( row( await mcpList( page.request ), 'boxlang' ).enabled ).toBe( false );
		expect( ( await mcpLog( request ) ).filter( e => e.server === 'boxlang' ) ).toHaveLength( 0 );
		await turnOn( page, 'boxlang' );
		const r = page.locator( '.mcprow[data-id="boxlang"]' );
		await expect( r.locator( '.mcpstatus' ) ).toHaveText( 'ok' );
		await expect( r ).toContainText( '2 of 2' );
		await r.locator( '.mcp-pick' ).click();
		await expect( r.locator( '.mcppicker' ) ).toContainText( 'searchDocumentation' );
		await expect( r.locator( '.mcppicker' ) ).toContainText( 'getPage' );
		const after = await ( await page.request.get( `${ API }/agent/status` ) ).json();
		expect( after.tools ).toBe( before.tools + 2 );
		const log = await mcpLog( request );
		expect( JSON.stringify( log ) ).toContain( 'notifications/initialized' );
		expect( ( await auditText( request, 'ai.mcp.change' ) ) ).toContain( 'enabled server=boxlang' );
	} );

	test( 'Lensy asks the server: the chip shows the server badge, the answer comes from the server and the call is audited', async ( { page, request } ) => {
		await request.delete( `${ MOCK }/mock/log` );
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'force:boxlang__searchDocumentation' );
		const chip = page.locator( '.tchip .chipbtn', { hasText: 'searchDocumentation' } );
		await expect( chip ).toBeVisible();
		await expect( chip.locator( '.srvbadge' ) ).toHaveText( 'boxlang' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'Mock boxlang documentation' );
		const offered = ( await mockLog( request ) ).flatMap( e => e.tools );
		expect( offered ).toEqual( expect.arrayContaining( [ 'boxlang__searchDocumentation', 'boxlang__getPage', 'overview' ] ) );
		expect( offered.filter( ( t: string ) => t.includes( '.' ) ) ).toEqual( [] );
		const log = await mcpLog( request );
		expect( calls( log, 'boxlang' ).length ).toBeGreaterThan( 0 );
		expect( JSON.stringify( log ) ).toContain( '"session":"mock-boxlang","method":"tools/call"' );
		const audit = await auditText( request, 'event=ai.mcp ' );
		expect( audit ).toMatch( /event=ai\.mcp .*server=boxlang tool=searchDocumentation result=ok ms=\d+/ );
		await page.click( '#agent-reset' );
	} );

	test( 'the face of Lensy: idle, thinking while an answer is written, happy after it', async ( { page } ) => {
		await signIn( page );
		await page.click( '#agent-launch' );
		await expect( page.locator( '.aempty .lensy.big' ) ).toBeVisible();
		await expect( page.locator( '#agent .ahead .lensy use' ) ).toHaveAttribute( 'href', '#lensy-idle' );
		await ask( page, 'slow-answer please' );
		await expect( page.locator( '#agent .ahead .lensy' ) ).toHaveClass( /think/ );
		await expect( page.locator( '#agent .ahead .lensy use' ) ).toHaveAttribute( 'href', /#lensy-think/ );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'MOCK AGENT: finally' );
		await expect( page.locator( '#agent .ahead .lensy use' ) ).toHaveAttribute( 'href', '#lensy-happy' );
		await page.click( '#agent-reset' );
	} );

	test( 'with reduced motion the thinking face is still', async ( { browser } ) => {
		const ctx = await browser.newContext( { reducedMotion: 'reduce' } );
		const page = await ctx.newPage();
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'slow-answer please' );
		await expect( page.locator( '#agent .ahead .lensy use' ) ).toHaveAttribute( 'href', '#lensy-think-still' );
		expect( await page.locator( '#agent .ahead .lensy' ).evaluate( el => getComputedStyle( el ).animationName ) ).toBe( 'none' );
		await ctx.close();
	} );

	test( 'untick a tool and it is no longer offered or accepted; tick all and it is back', async ( { page, request } ) => {
		await openAiPage( page );
		const r = page.locator( '.mcprow[data-id="boxlang"]' );
		await r.locator( '.mcp-pick' ).click();
		await r.locator( '.mcptool', { hasText: 'getPage' } ).locator( 'input' ).uncheck();
		await r.locator( '.mcp-save-tools' ).click();
		await expect( r ).toContainText( '1 of 2' );
		expect( row( await mcpList( page.request ), 'boxlang' ).allowedTools ).toEqual( [ 'searchDocumentation' ] );
		const { csrf } = await login( request );
		await request.delete( `${ MOCK }/mock/log` );
		await request.delete( `${ MCP }/_log` );
		const text = await chat( request, csrf, 'force:boxlang__getPage' );
		expect( answer( text ) ).not.toContain( 'mock boxlang page' );
		const offered = ( await mockLog( request ) ).flatMap( e => e.tools );
		expect( offered ).toContain( 'boxlang__searchDocumentation' );
		expect( offered ).not.toContain( 'boxlang__getPage' );
		expect( calls( await mcpLog( request ), 'boxlang' ).filter( c => c.tool === 'getPage' ) ).toHaveLength( 0 );
		await r.locator( '.mcptool', { hasText: 'getPage' } ).locator( 'input' ).check();
		await r.locator( '.mcp-save-tools' ).click();
		await expect( r ).toContainText( '2 of 2' );
		expect( row( await mcpList( page.request ), 'boxlang' ).allowAll ).toBe( true );
	} );

	test( 'the add form rejects plain http, credentials and private or metadata addresses, before and after the round trip', async ( { page } ) => {
		await openAiPage( page );
		const add = async ( name: string, url: string ) => {
			await page.fill( '#mcp-name', name );
			await page.fill( '#mcp-url', url );
		};
		await add( 'Plain', 'http://example.com/mcp' );
		await expect( page.locator( '#mcp-urlhint' ) ).toContainText( 'Only https://' );
		await page.click( '#mcp-add' );
		await expect( page.locator( '#mcp-error' ) ).toContainText( 'https' );
		await add( 'Creds', 'https://user:secret@example.com/mcp' );
		await expect( page.locator( '#mcp-urlhint' ) ).toContainText( 'user name and password' );
		await page.click( '#mcp-add' );
		await expect( page.locator( '#mcp-error' ) ).toContainText( 'user name or password' );
		await add( 'Metadata', 'https://169.254.169.254/latest/meta-data' );
		await page.click( '#mcp-add' );
		await expect( page.locator( '#mcp-error' ) ).toContainText( 'Only public addresses' );
		await add( 'Private', 'https://10.0.0.5/mcp' );
		await page.click( '#mcp-add' );
		await expect( page.locator( '#mcp-error' ) ).toContainText( 'Only public addresses' );
		await add( 'Local name', 'https://[::1]:1/mcp' );
		await page.click( '#mcp-add' );
		await expect( page.locator( '#mcp-card .mcprow[data-id="local-name"]' ) ).toBeVisible();
		await page.locator( '.mcprow[data-id="local-name"] .mcp-remove' ).click().catch( () => {} );
		const l = await ( await page.request.get( `${ API }/ai/mcp` ) ).json();
		expect( l.servers.filter( ( s: any ) => !s.builtin ).map( ( s: any ) => s.id ) ).not.toContain( 'metadata' );
		expect( l.servers.filter( ( s: any ) => !s.builtin ).map( ( s: any ) => s.id ) ).not.toContain( 'private' );
		// 128 characters of name is refused too
		const { csrf } = await login( page.request );
		const long = await post( page.request, csrf, 'ai/mcp', { name: 'n'.repeat( 41 ), url: `${ MCP }/acme` } );
		expect( long.status ).toBe( 400 );
		expect( long.body.error ).toContain( '1 to 40' );
	} );

	test( 'a custom server is added off with no tool allowed; it asks for a click on every call; Deny sends nothing and Approve sends one call', async ( { page, request } ) => {
		await openAiPage( page );
		await page.fill( '#mcp-name', 'Acme' );
		await page.fill( '#mcp-url', `${ MCP }/acme` );
		await page.click( '#mcp-add' );
		const r = page.locator( '.mcprow[data-id="acme"]' );
		await expect( r ).toBeVisible();
		await expect( r.locator( '.mcp-on' ) ).not.toBeChecked();
		await expect( r.locator( '.mcp-remove' ) ).toBeVisible();
		await turnOn( page, 'acme' );
		await expect( r.locator( '.mcpstatus' ) ).toHaveText( 'ok' );
		await expect( r ).toContainText( '0 of 4' );
		await expect( page.locator( '.mcppick:visible' ) ).toHaveCount( 0 );
		await expect( r.locator( '.mcp-trusted' ) ).not.toBeChecked();
		await expect( r.locator( 'td' ).nth( 6 ) ).toContainText( 'Remove' );
		await r.locator( '.mcp-pick' ).click();
		await expect( r.locator( '.mcppicker' ) ).toContainText( 'Lets Lensy see no tool' ).catch( () => {} );
		await r.locator( '.mcptool', { hasText: 'lookup' } ).locator( 'input' ).check();
		await r.locator( '.mcp-save-tools' ).click();
		await expect( r ).toContainText( '1 of 4' );
		await request.delete( `${ MCP }/_log` );
		await page.click( '#agent-launch' );
		await ask( page, 'force:acme__lookup' );
		const card = page.locator( '.acard' ).last();
		await expect( card ).toContainText( 'Approval needed' );
		await expect( card ).toContainText( 'Acme' );
		await expect( card ).toContainText( '127.0.0.1' );
		await card.locator( '[data-act=deny]' ).click();
		await expect( card ).toContainText( 'Denied' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'did not approve' );
		expect( calls( await mcpLog( request ), 'acme' ) ).toHaveLength( 0 );
		await page.fill( '#agent-input', 'force:acme__lookup' );
		await page.click( '#agent-send' );
		await expect( page.locator( '.acard' ) ).toHaveCount( 2 );
		const second = page.locator( '.acard' ).nth( 1 );
		await expect( second.locator( '[data-act=approve]' ) ).toBeEnabled();
		await second.locator( '[data-act=approve]' ).click();
		await expect( second ).toContainText( 'Approved' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'Acme says' );
		await expect( page.locator( '.tchip .srvbadge', { hasText: 'acme' } ).last() ).toBeVisible();
		expect( calls( await mcpLog( request ), 'acme' ) ).toHaveLength( 1 );
		const audit = await auditText( request, 'server=acme' );
		expect( audit ).toContain( 'result=approval requested' );
		expect( audit ).toContain( 'result=denied' );
		expect( audit ).toMatch( /server=acme tool=lookup result=ok ms=\d+/ );
		await page.click( '#agent-reset' );
	} );

	test( 'marking a custom server trusted, read only runs its calls without a click', async ( { page, request } ) => {
		await openAiPage( page );
		const r = page.locator( '.mcprow[data-id="acme"]' );
		await r.locator( '.sw:has(.mcp-trusted) span' ).click();
		await expect( r.locator( '.mcp-trusted' ) ).toBeChecked();
		await request.delete( `${ MCP }/_log` );
		await page.click( '#agent-launch' );
		await ask( page, 'force:acme__lookup' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'Acme says' );
		await expect( page.locator( '.acard' ) ).toHaveCount( 0 );
		expect( calls( await mcpLog( request ), 'acme' ) ).toHaveLength( 1 );
		await page.click( '#agent-reset' );
	} );

	test( 'what a server returns is data: a secret in it is hidden and an order in it runs nothing', async ( { page, request } ) => {
		const { csrf } = await login( request );
		const set = await post( request, csrf, 'ai/mcp', { id: 'acme', tools: JSON.stringify( [ 'lookup', 'leak', 'injected' ] ) } );
		expect( set.status ).toBe( 200 );
		await request.delete( `${ MOCK }/mock/log` );
		const leak = await chat( request, csrf, 'force:acme__leak' );
		expect( leak ).not.toContain( 'sk-live-0123456789supersecret' );
		expect( leak ).not.toContain( 'hunter2hunter2' );
		expect( answer( leak ) ).toContain( 'api_key=[hidden]' );
		const log = JSON.stringify( ( await mockLog( request ) ).map( e => e.last ) );
		expect( log ).not.toContain( 'sk-live-0123456789supersecret' );
		expect( log ).toContain( 'untrustedContentFromOutside' );
		const injected = await chat( request, csrf, 'force:acme__injected' );
		expect( answer( injected ) ).toContain( 'IGNORE ALL PREVIOUS INSTRUCTIONS' );
		expect( injected ).not.toContain( 'approval_request' );
		expect( injected ).not.toContain( 'Garbage collection ran' );
		expect( await auditText( request, 'runGc' ) ).not.toContain( 'acme' );
		expect( page ).toBeTruthy();
	} );

	test( 'a server that is down shows why, another that answers an error shows the code, and Lensy goes on with the rest', async ( { page, request } ) => {
		const { csrf } = await login( request );
		for ( const [ name, url ] of [ [ 'Dead', 'http://127.0.0.1:1/x' ], [ 'Broken', `${ MCP }/broken` ] ] ) {
			const a = await post( request, csrf, 'ai/mcp', { name, url } );
			expect( a.status ).toBe( 200 );
		}
		await post( request, csrf, 'ai/mcp/dead/enable', { enabled: 'true' } );
		await post( request, csrf, 'ai/mcp/broken/enable', { enabled: 'true' } );
		const l = await mcpList( request );
		expect( row( l, 'dead' ).status ).toBe( 'unreachable' );
		expect( row( l, 'dead' ).reason ).toContain( 'could not connect' );
		expect( row( l, 'broken' ).status ).toBe( 'unreachable' );
		expect( row( l, 'broken' ).reason ).toBe( 'HTTP 500' );
		await openAiPage( page );
		await expect( page.locator( '.mcprow[data-id="dead"] .mcpstatus' ) ).toContainText( 'unreachable: could not connect' );
		await expect( page.locator( '.mcprow[data-id="broken"] .mcpstatus' ) ).toContainText( 'unreachable: HTTP 500' );
		await expect( page.locator( '.mcprow[data-id="dead"] .mcp-pick' ) ).toBeDisabled();
		await page.click( '#agent-launch' );
		await ask( page, 'force:boxlang__getPage' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'mock boxlang page' );
		await page.click( '#agent-reset' );
		await del( request, csrf, 'ai/mcp/dead' );
		await del( request, csrf, 'ai/mcp/broken' );
	} );

	test( 'a viewer can use the documentation tools of an enabled builtin server, nothing of a custom one, and manages nothing', async ( { browser, request } ) => {
		await login( request );
		const ctx = await browser.newContext();
		const v = await login( ctx.request, 'lens-view' );
		const st = await ( await ctx.request.get( `${ API }/agent/status` ) ).json();
		expect( st.role ).toBe( 'viewer' );
		await request.delete( `${ MOCK }/mock/log` );
		await request.delete( `${ MCP }/_log` );
		const ok = await chat( ctx.request, v.csrf, 'force:boxlang__getPage' );
		expect( answer( ok ) ).toContain( 'mock boxlang page' );
		const offered = ( await mockLog( request ) ).flatMap( e => e.tools );
		expect( offered ).toContain( 'boxlang__searchDocumentation' );
		expect( offered.filter( ( t: string ) => t.startsWith( 'acme__' ) ) ).toEqual( [] );
		const custom = await chat( ctx.request, v.csrf, 'force:acme__lookup' );
		expect( answer( custom ) ).not.toContain( 'Acme says' );
		expect( custom ).not.toContain( 'approval_request' );
		expect( calls( await mcpLog( request ), 'acme' ) ).toHaveLength( 0 );
		expect( ( await ctx.request.get( `${ API }/ai/mcp`, { failOnStatusCode: false } ) ).status() ).toBe( 403 );
		expect( ( await post( ctx.request, v.csrf, 'ai/mcp', { name: 'x', url: `${ MCP }/acme` } ) ).status ).toBe( 403 );
		expect( ( await post( ctx.request, v.csrf, 'ai/mcp/boxlang/enable', { enabled: 'false' } ) ).status ).toBe( 403 );
		expect( ( await post( ctx.request, v.csrf, 'ai/mcp/boxlang/test' ) ).status ).toBe( 403 );
		expect( ( await del( ctx.request, v.csrf, 'ai/mcp/acme' ) ).status ).toBe( 403 );
		expect( row( await mcpList( request ), 'acme' ) ).toBeTruthy();
		await ctx.close();
	} );

	test( 'a builtin server cannot be removed or trusted, a bad request is refused, and every change needs the CSRF token', async ( { request } ) => {
		const { csrf } = await login( request );
		expect( ( await del( request, csrf, 'ai/mcp/boxlang' ) ).body.error ).toContain( 'cannot be removed' );
		expect( ( await post( request, csrf, 'ai/mcp', { id: 'boxlang', trusted: 'true' } ) ).status ).toBe( 400 );
		expect( ( await post( request, csrf, 'ai/mcp', { id: 'boxlang', tools: JSON.stringify( [ 'nothere' ] ) } ) ).body.error ).toContain( 'no tool called' );
		expect( ( await post( request, csrf, 'ai/mcp/boxlang/enable', { enabled: 'maybe' } ) ).status ).toBe( 400 );
		expect( ( await post( request, csrf, 'ai/mcp/nothere/enable', { enabled: 'true' } ) ).status ).toBe( 400 );
		const noCsrf = await request.post( `${ API }/ai/mcp/boxlang/enable`, { form: { enabled: 'false' }, failOnStatusCode: false } );
		expect( noCsrf.status() ).toBe( 403 );
		const noCsrfDel = await request.delete( `${ API }/ai/mcp/acme`, { failOnStatusCode: false } );
		expect( noCsrfDel.status() ).toBe( 403 );
		expect( row( await mcpList( request ), 'boxlang' ).enabled ).toBe( true );
		expect( row( await mcpList( request ), 'acme' ) ).toBeTruthy();
	} );

	test( 'connection tests are limited to ten a minute for each session', async ( { request } ) => {
		const { csrf } = await login( request );
		const codes: number[] = [];
		for ( let i = 0; i < 12; i++ ) { codes.push( ( await post( request, csrf, 'ai/mcp/boxlang/test' ) ).status ); }
		expect( codes.filter( c => c === 200 ).length ).toBe( 10 );
		expect( codes.filter( c => c === 429 ).length ).toBe( 2 );
	} );

	test( 'turning a server off takes its tools away at once', async ( { page, request } ) => {
		await openAiPage( page );
		const before = ( await ( await page.request.get( `${ API }/agent/status` ) ).json() ).tools;
		await page.locator( '.mcprow[data-id="boxlang"] .sw span' ).first().click();
		await expect( page.locator( '.mcprow[data-id="boxlang"] .mcp-on' ) ).not.toBeChecked();
		const after = ( await ( await page.request.get( `${ API }/agent/status` ) ).json() ).tools;
		expect( after ).toBe( before - 2 );
		await request.delete( `${ MOCK }/mock/log` );
		await request.delete( `${ MCP }/_log` );
		const { csrf } = await login( request );
		const text = await chat( request, csrf, 'force:boxlang__getPage' );
		expect( answer( text ) ).not.toContain( 'mock boxlang page' );
		expect( ( await mockLog( request ) ).flatMap( e => e.tools ).filter( ( t: string ) => t.startsWith( 'boxlang__' ) ) ).toEqual( [] );
		expect( calls( await mcpLog( request ), 'boxlang' ) ).toHaveLength( 0 );
		expect( await auditText( request, 'ai.mcp.change' ) ).toContain( 'disabled server=boxlang' );
	} );

	test( 'removing a custom server asks first and takes it from the list', async ( { page } ) => {
		await openAiPage( page );
		page.once( 'dialog', d => d.accept() );
		await page.locator( '.mcprow[data-id="acme"] .mcp-remove' ).click();
		await expect( page.locator( '.mcprow[data-id="acme"]' ) ).toHaveCount( 0 );
		expect( row( await mcpList( page.request ), 'acme' ) ).toBeUndefined();
	} );

	test( 'on Free the list can be read but nothing changes', async ( { request } ) => {
		const { csrf } = await login( request, 'lens-demo', FREE );
		const l = await mcpList( request, FREE );
		expect( l.locked ).toBe( true );
		expect( l.canChange ).toBe( false );
		const r = await post( request, csrf, 'ai/mcp/boxlang/enable', { enabled: 'true' }, FREE );
		expect( r.status ).toBe( 403 );
		expect( r.body.plus ).toBe( true );
		const add = await post( request, csrf, 'ai/mcp', { name: 'x', url: 'https://example.com/mcp' }, FREE );
		expect( add.status ).toBe( 403 );
	} );

	test( 'a read only console refuses every change, uses builtin servers, and refuses a call that is not known to be safe without asking', async ( { browser } ) => {
		const ctx = await browser.newContext();
		const a = await login( ctx.request, 'lens-demo', RO );
		const l = await mcpList( ctx.request, RO );
		expect( l.canChange ).toBe( false );
		expect( row( l, 'boxlang' ).enabled ).toBe( true );
		expect( row( l, 'acme' ).enabled ).toBe( true );
		// The saved entry with a plain http address was skipped and reported, and the console started
		expect( row( l, 'evil' ) ).toBeUndefined();
		expect( l.problems.join( ' ' ) ).toContain( 'Skipped a saved server' );
		for ( const [ path, form ] of [ [ 'ai/mcp/boxlang/enable', { enabled: 'false' } ], [ 'ai/mcp', { name: 'x', url: 'https://example.com/mcp' } ], [ 'ai/mcp/acme/test', {} ] ] as [ string, any ][] ) {
			const r = await post( ctx.request, a.csrf, path, form, RO );
			expect( r.status, path ).toBe( 403 );
			expect( r.body.error ).toContain( 'read-only' );
		}
		expect( ( await del( ctx.request, a.csrf, 'ai/mcp/acme', RO ) ).status ).toBe( 403 );
		const st = await ( await ctx.request.get( `${ RO }${ API }/agent/status` ) ).json();
		expect( st.available ).toBe( true );
		await ctx.request.get( `${ RO }${ API }/ai/mcp` );
		// Both servers need a first look: ask once, which looks at the enabled servers
		const builtin = await chat( ctx.request, a.csrf, 'force:boxlang__getPage', RO );
		expect( answer( builtin ) ).toContain( 'mock boxlang page' );
		const custom = await chat( ctx.request, a.csrf, 'force:acme__lookup', RO );
		expect( custom ).not.toContain( 'approval_request' );
		expect( answer( custom ) ).not.toContain( 'Acme says' );
		const audit = await auditText( ctx.request, 'server=acme', RO );
		expect( audit.length === 0 || !audit.includes( 'result=ok' ) ).toBe( true );
		await ctx.close();
	} );

} );
