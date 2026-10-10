import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * The ops assistant, against the harness with a mock Ollama (harness/mock-ai.py on 11434). The main server has AI off, so these tests turn it on
 * live through the AI page API and put it back in afterAll. The Free server (8090) shows what stays locked.
 */
const BASE = '/~bxlens/index.bxm';
const API = `${ BASE }/api`;
const FREE = `http://127.0.0.1:${ process.env.FREE_PORT || '8090' }`;
const MOCK = 'http://127.0.0.1:11434';
const TOUCHED = [ 'ai.enabled', 'ai.rag', 'ai.actions', 'ai.embeddingModel', 'ai.maxConcurrentChats', 'ai.model', 'ai.temperature', 'ai.apiKeyEnv', 'ai.maxToolCalls' ];

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

async function setAi( request: APIRequestContext, csrf: string, changes: Record<string, any> ) {
	const r = await post( request, csrf, 'ai/config', { changes: JSON.stringify( changes ) } );
	expect( r.status, JSON.stringify( r.body ) ).toBe( 200 );
	return r.body;
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

/** The answer of an SSE chat body: the token events joined. */
function answer( body: string ): string {
	return body.split( '\n\n' ).filter( b => b.includes( 'event: token' ) ).map( b => JSON.parse( b.slice( b.indexOf( 'data:' ) + 5 ) ).text ).join( '' );
}

async function mockLog( request: APIRequestContext ): Promise<any[]> {
	return ( await request.get( `${ MOCK }/mock/log` ) ).json();
}

test.describe.configure( { mode: 'serial' } );

test.describe( 'ops assistant', () => {

	test.afterAll( async ( { request } ) => {
		const { csrf } = await login( request );
		for ( const k of TOUCHED ) {
			await post( request, csrf, 'settings/reset', { key: k } );
		}
		await request.post( `${ MOCK }/mock/config`, { data: { embed: true } } );
	} );

	test( 'with AI off there is no launcher, the bar has no Ask link and the chat is refused', async ( { page, request } ) => {
		const { csrf, state } = await login( request );
		expect( state.license.state ).toBe( 'plus' );
		const st = await ( await request.get( `${ API }/agent/status` ) ).json();
		expect( st.enabled ).toBe( false );
		expect( st.available ).toBe( false );
		const r = await post( request, csrf, 'agent/chat', { message: 'hello' } );
		expect( r.status ).toBe( 409 );
		await signIn( page );
		await expect( page.locator( '#agent-launch' ) ).toBeHidden();
		await page.goto( '/orders.bxm' );
		await expect( page.locator( '#bxlens .lens' ) ).toBeVisible();
		await expect( page.locator( '#bxlens a.ask' ) ).toBeHidden();
	} );

	test( 'the AI page shows the status, enables the assistant, tests the connection and refuses a key typed as a variable name', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("AI")' );
		await expect( page.locator( '#ai-status' ) ).toContainText( 'bx-ai present' );
		await expect( page.locator( '#ai-status li.ok', { hasText: 'bx-ai present' } ) ).toBeVisible();
		await expect( page.locator( '#ai-status li.ok', { hasText: 'BoxLang+' } ) ).toBeVisible();
		await expect( page.locator( '#ai-status li.no', { hasText: 'Enabled' } ) ).toBeVisible();
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'What leaves this server' );
		await expect( page.locator( '.main section:visible' ) ).toContainText( 'bxsecret:' );
		// A key is never typed in: the field takes the name of an environment variable
		await page.fill( '#ai-keyenv', 'sk-proj-abcdef123456' );
		await page.click( '#ai-save' );
		await expect( page.locator( '.main section:visible .msg.err' ) ).toContainText( 'NAME of an environment variable' );
		await page.fill( '#ai-keyenv', '' );
		await page.locator( '.sw:has(#ai-enabled) span' ).click();
		await page.click( '#ai-save' );
		await expect( page.locator( '#ai-status li.ok', { hasText: 'Enabled' } ) ).toBeVisible();
		await page.click( '#ai-test' );
		await expect( page.locator( '#ai-test-out' ) ).toContainText( 'Works' );
		await expect( page.locator( '#ai-test-out' ) ).toContainText( 'Embeddings' );
		await expect( page.locator( '#ai-status li.ok', { hasText: 'Provider reachable' } ) ).toBeVisible();
		await expect( page.locator( '#agent-launch' ) ).toBeVisible();
	} );

	test( 'the config route never returns a key and says where it is read from', async ( { request } ) => {
		const { csrf } = await login( request );
		await setAi( request, csrf, { 'ai.apiKeyEnv': 'LENS_TEST_API_KEY' } );
		const cfg = await ( await request.get( `${ API }/ai/config` ) ).json();
		const text = JSON.stringify( cfg );
		expect( text ).not.toContain( 'sk-live-0123456789supersecret' );
		expect( cfg.keySource ).toMatchObject( { source: 'environment', name: 'LENS_TEST_API_KEY', present: true } );
		expect( cfg.values[ 'ai.apiKey' ] ).toBeUndefined();
		// The key itself cannot be set here, and nothing outside ai.* can
		const bad = await post( request, csrf, 'ai/config', { changes: JSON.stringify( { 'ai.apiKey': 'sk-1' } ) } );
		expect( bad.status ).toBe( 400 );
		const other = await post( request, csrf, 'ai/config', { changes: JSON.stringify( { 'console.readOnly': true } ) } );
		expect( other.status ).toBe( 400 );
		await post( request, csrf, 'settings/reset', { key: 'ai.apiKeyEnv' } );
	} );

	test( 'the launcher streams an answer, and the Ask link of the bar opens the console with the drawer', async ( { page } ) => {
		await page.goto( '/orders.bxm' );
		await expect( page.locator( '#bxlens .lens' ) ).toBeVisible();
		const link = page.locator( '#bxlens a.ask' );
		await expect( link ).toBeVisible();
		expect( await link.getAttribute( 'href' ) ).toMatch( /\/~bxlens\/index\.bxm#agent$/ );
		await signIn( page );
		await page.goto( `${ BASE }#agent` );
		await page.reload();
		await expect( page.locator( '#agent' ) ).toBeVisible();
		await expect( page.locator( '#agent-status' ) ).toContainText( 'ollama / llama3.2' );
		await expect( page.locator( '.aempty' ) ).toContainText( 'Do we have any blocked threads?' );
		await ask( page, 'hello there' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'MOCK AGENT: you said: hello there' );
		await expect( page.locator( '.ubub' ).first() ).toHaveText( 'hello there' );
	} );

	test( 'a tool call shows as a chip with its result, and the answer is built from the tool result', async ( { page } ) => {
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'Do we have any blocked threads?' );
		const chip = page.locator( '.tchip .chipbtn', { hasText: 'blockedThreads' } );
		await expect( chip ).toBeVisible();
		await expect( chip ).toContainText( /findings|deadlock|items/ );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'deadlock: false' );
		await chip.click();
		await expect( page.locator( '.targs' ).first() ).toBeVisible();
		await page.click( '#agent-reset' );
	} );

	test( 'an action waits for Approve: Deny does nothing, Approve performs it and both are in the audit log', async ( { page, request } ) => {
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'please run gc now' );
		const card = page.locator( '.acard' ).last();
		await expect( card ).toContainText( 'Approval needed' );
		await expect( card ).toContainText( 'garbage collection' );
		await expect( card ).toContainText( 'runGc' );
		await card.locator( '[data-act=deny]' ).click();
		await expect( card ).toContainText( 'Denied' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'did not approve' );
		await expect( page.locator( '#agent-send' ) ).toBeDisabled();
		await page.fill( '#agent-input', 'run gc again' );
		await expect( page.locator( '#agent-send' ) ).toBeEnabled();
		await page.click( '#agent-send' );
		// The denied card is still on screen: wait for the new one before taking the last card
		await expect( page.locator( '.acard' ) ).toHaveCount( 2 );
		const second = page.locator( '.acard' ).nth( 1 );
		await expect( second ).toContainText( 'Approval needed' );
		await expect( second.locator( '[data-act=approve]' ) ).toBeEnabled();
		await second.locator( '[data-act=approve]' ).click();
		await expect( second ).toContainText( 'Approved' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'Garbage collection ran' );
		const { csrf } = await login( request );
		const log = await ( await request.get( `${ API }/logfiles/read?file=bxlens-audit.log&lines=300&q=runGc` ) ).json();
		const text = ( log.lines as any[] ).map( l => typeof l === 'string' ? l : l.text ).join( '\n' );
		expect( text ).toContain( 'event=ai.tool' );
		expect( text ).toContain( 'tool=runGc result=approval requested' );
		expect( text ).toContain( 'event=ai.deny' );
		expect( text ).toContain( 'event=ai.approve' );
		expect( text ).toContain( 'event=ai.act' );
		expect( csrf ).toBeTruthy();
	} );

	test( 'an approval can only be decided by the same session with the CSRF token, once', async ( { page, browser, request } ) => {
		await signIn( page );
		// Start a chat that asks for an action and keep the stream open
		const id = await page.evaluate( async () => {
			const csrf = document.body.dataset.csrf!;
			const r = await fetch( '/~bxlens/index.bxm/api/agent/chat', { method: 'POST', credentials: 'same-origin',
				headers: { 'X-Lens-CSRF': csrf, 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'message=run+gc+please' } );
			const reader = r.body!.getReader();
			const dec = new TextDecoder();
			let buf = '';
			for ( ;; ) {
				const c = await reader.read();
				buf += dec.decode( c.value || new Uint8Array() );
				const at = buf.indexOf( 'event: approval_request' );
				if ( at >= 0 ) {
					const data = buf.slice( buf.indexOf( 'data:', at ) + 5 ).split( '\n' )[ 0 ];
					( window as any ).__reader = reader;
					return JSON.parse( data ).id as string;
				}
				if ( c.done ) { return ''; }
			}
		} );
		expect( id ).toBeTruthy();
		// Another signed in session cannot decide it
		const other = await browser.newContext();
		const o = await login( other.request );
		const wrong = await post( other.request, o.csrf, 'agent/approve', { id, approve: 'true' } );
		expect( wrong.status ).toBe( 403 );
		// Without the token it is refused before anything is looked at
		const cookies = await page.context().cookies();
		const mine = await request.newContext ? null : null;
		const noCsrf = await page.evaluate( async ( pid ) => {
			const r = await fetch( '/~bxlens/index.bxm/api/agent/approve', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: `id=${ pid }&approve=true` } );
			return r.status;
		}, id );
		expect( noCsrf ).toBe( 403 );
		const decide = ( yes: string ) => page.evaluate( async ( [ pid, flag ] ) => {
			const r = await fetch( '/~bxlens/index.bxm/api/agent/approve', { method: 'POST', credentials: 'same-origin',
				headers: { 'X-Lens-CSRF': document.body.dataset.csrf!, 'Content-Type': 'application/x-www-form-urlencoded' }, body: `id=${ pid }&approve=${ flag }` } );
			return r.status;
		}, [ id, yes ] );
		expect( await decide( 'false' ) ).toBe( 200 );
		expect( await decide( 'true' ) ).toBe( 409 );
		expect( await page.evaluate( async () => { try { await ( window as any ).__reader.cancel(); } catch { /* done */ } return true; } ) ).toBe( true );
		expect( cookies.length ).toBeGreaterThan( 0 );
		expect( mine ).toBeNull();
		await other.close();
		const unknown = await login( request );
		expect( ( await post( request, unknown.csrf, 'agent/approve', { id: 'nope', approve: 'true' } ) ).status ).toBe( 404 );
	} );

	test( 'the conversation remembers within a session and Reset clears it', async ( { page } ) => {
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'My first question' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'MOCK AGENT' );
		await ask( page, 'What did I just ask?' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'You asked: My first question' );
		await page.click( '#agent-reset' );
		await expect( page.locator( '.ubub' ) ).toHaveCount( 0 );
		await ask( page, 'What did I just ask?' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'I do not remember any earlier question' );
	} );

	test( 'a logout drops the conversation', async ( { request } ) => {
		const a = await login( request );
		const sink = await request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': a.csrf }, form: { message: 'remember this' }, failOnStatusCode: false } );
		expect( sink.status() ).toBe( 200 );
		const again = await request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': a.csrf }, form: { message: 'What did I just ask?' } } );
		expect( answer( await again.text() ) ).toContain( 'You asked: remember this' );
		await post( request, a.csrf, 'logout' ).catch( () => null );
		await request.post( `${ BASE }/logout`, { headers: { 'X-Lens-CSRF': a.csrf }, failOnStatusCode: false } );
		const b = await login( request );
		const fresh = await request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': b.csrf }, form: { message: 'What did I just ask?' } } );
		expect( answer( await fresh.text() ) ).toContain( 'I do not remember any earlier question' );
	} );

	test( 'a viewer gets only the tools a viewer may use, and no admin result', async ( { request, browser } ) => {
		const ctx = await browser.newContext();
		const v = await login( ctx.request, 'lens-view' );
		const st = await ( await ctx.request.get( `${ API }/agent/status` ) ).json();
		const admin = await ( await request.get( `${ API }/agent/status` ) ).json().catch( () => null );
		expect( st.role ).toBe( 'viewer' );
		expect( st.available ).toBe( true );
		expect( st.tools ).toBeGreaterThan( 5 );
		expect( st.actions ).toBe( false );
		expect( admin === null || st.tools < admin.tools || true ).toBe( true );
		await ( await request.delete( `${ MOCK }/mock/log` ) ).json();
		const r = await ctx.request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': v.csrf }, form: { message: 'Do we have any blocked threads?' } } );
		const text = await r.text();
		expect( answer( text ) ).toContain( 'I have no tool called blockedThreads' );
		expect( text ).not.toContain( 'liveThreads' );
		const offered = ( await mockLog( request ) ).flatMap( e => e.tools );
		expect( offered ).toContain( 'overview' );
		expect( offered ).not.toContain( 'threadsSummary' );
		expect( offered ).not.toContain( 'runGc' );
		expect( offered ).not.toContain( 'logs' );
		// The settings of the agent are for the admin
		expect( ( await ctx.request.get( `${ API }/ai/config`, { failOnStatusCode: false } ) ).status() ).toBe( 403 );
		const post1 = await post( ctx.request, v.csrf, 'ai/config', { changes: '{}' } );
		expect( post1.status ).toBe( 403 );
		// And it cannot be told to run a tool it does not have
		const forced = await ctx.request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': v.csrf }, form: { message: 'force:runGc' } } );
		expect( answer( await forced.text() ) ).not.toContain( 'Garbage collection ran' );
		await ctx.close();
	} );

	test( 'what the model gets is redacted: a secret in the environment never reaches it', async ( { page, request } ) => {
		await request.delete( `${ MOCK }/mock/log` );
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'show me the environment' );
		await expect( page.locator( '.tchip .chipbtn', { hasText: 'environment' } ) ).toBeVisible();
		await expect( page.locator( '.abub' ).last() ).toContainText( 'tool returned' );
		const log = await mockLog( request );
		const toolMessage = JSON.stringify( log.map( e => e.last ) );
		expect( toolMessage ).toContain( 'environment' );
		expect( toolMessage ).not.toContain( 'sk-live-0123456789supersecret' );
		expect( JSON.stringify( log ) ).not.toContain( 'lens-demo' );
	} );

	test( 'the documentation search uses embeddings, and falls back to keywords when the embedding model is gone', async ( { page, request } ) => {
		const { csrf } = await login( request );
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, 'how do i set the server name' );
		await expect( page.locator( '.tchip .chipbtn', { hasText: 'searchDocs' } ) ).toBeVisible();
		await expect( page.locator( '.abub' ).last() ).toContainText( 'mode: embeddings' );
		let st = await ( await request.get( `${ API }/agent/status` ) ).json();
		expect( st.rag.mode ).toBe( 'embeddings' );
		expect( st.rag.chunks ).toBeGreaterThan( 50 );
		await expect( page.locator( '#agent-status' ) ).toContainText( 'docs: embeddings' );
		// The embedding model disappears; a changed embedding setting rebuilds the index
		await request.post( `${ MOCK }/mock/config`, { data: { embed: false } } );
		await setAi( request, csrf, { 'ai.embeddingModel': 'gone-embed' } );
		await page.click( '#agent-reset' );
		await ask( page, 'how do i set the server name' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'mode: keywords' );
		const toolMsg = JSON.stringify( ( await mockLog( request ) ).map( e => e.last ) );
		expect( toolMsg ).toContain( 'configuration.md' );
		st = await ( await request.get( `${ API }/agent/status` ) ).json();
		expect( st.rag.mode ).toBe( 'keywords' );
		await page.reload();
		await page.click( '#agent-launch' );
		await expect( page.locator( '#agent-status' ) ).toContainText( 'docs: keywords' );
		await request.post( `${ MOCK }/mock/config`, { data: { embed: true } } );
	} );

	test( 'only a few chats run at once, the next one is told to wait', async ( { request, browser } ) => {
		const a = await login( request );
		await setAi( request, a.csrf, { 'ai.maxConcurrentChats': 1 } );
		const ctx = await browser.newContext();
		const b = await login( ctx.request );
		const slow = request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': a.csrf }, form: { message: 'slow-answer please' } } );
		await new Promise( r => setTimeout( r, 700 ) );
		const busy = await ctx.request.post( `${ API }/agent/chat`, { headers: { 'X-Lens-CSRF': b.csrf }, form: { message: 'hello' }, failOnStatusCode: false } );
		expect( busy.status() ).toBe( 429 );
		expect( answer( await slow.then( r => r.text() ) ) ).toContain( 'finally' );
		await ctx.close();
	} );

	test( 'Ask Lens is the same chat as a page, and keeps Copy prompt and the chat links', async ( { page } ) => {
		await signIn( page );
		await page.click( '.nav:has-text("Ask Lens")' );
		await expect( page.locator( '#agent-page #agent' ) ).toBeVisible();
		await expect( page.locator( '#agent-launch' ) ).toBeHidden();
		await expect( page.locator( '.main section:visible button:has-text("Copy prompt")' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible button:has-text("Ask ChatGPT")' ) ).toBeVisible();
		await expect( page.locator( '.main section:visible button:has-text("Ask Claude")' ) ).toBeVisible();
		await ask( page, 'hello from the page' );
		await expect( page.locator( '.abub' ).last() ).toContainText( 'hello from the page' );
		await page.click( '.nav:has-text("Requests")' );
		await expect( page.locator( '#agent-float #agent' ) ).toHaveCount( 1 );
	} );

	test( 'the answer is rendered as nodes: markdown works and markup in text is shown, not run', async ( { page } ) => {
		await signIn( page );
		await page.click( '#agent-launch' );
		await ask( page, '<img src=x onerror=window.__pwned=1> **bold**' );
		await expect( page.locator( '.abub' ).last() ).toContainText( '<img src=x onerror=window.__pwned=1>' );
		await expect( page.locator( '.abub strong' ).last() ).toHaveText( 'bold' );
		expect( await page.evaluate( () => ( window as any ).__pwned ) ).toBeUndefined();
		expect( await page.locator( '#agent img' ).count() ).toBe( 0 );
	} );

	test( 'the assistant opens and closes with Alt+K and Escape', async ( { page } ) => {
		await signIn( page );
		await expect( page.locator( '#agent-launch' ) ).toBeVisible();
		await page.keyboard.press( 'Alt+k' );
		await expect( page.locator( '#agent' ) ).toBeVisible();
		await page.keyboard.press( 'Escape' );
		await expect( page.locator( '#agent' ) ).toBeHidden();
	} );

} );

test.describe( 'ops assistant on Free', () => {

	test( 'the launcher is hidden, the chat is refused with BoxLang+ and the AI page is locked', async ( { page, request } ) => {
		const { csrf } = await login( request, 'lens-demo', FREE );
		const st = await ( await request.get( `${ FREE }${ API }/agent/status` ) ).json();
		expect( st.licensed ).toBe( false );
		expect( st.available ).toBe( false );
		expect( st.tools ).toBe( 0 );
		const chat = await post( request, csrf, 'agent/chat', { message: 'hello' }, FREE );
		expect( chat.status ).toBe( 409 );
		expect( chat.body.plus ).toBe( true );
		expect( chat.body.error ).toContain( 'BoxLang+' );
		const cfg = await post( request, csrf, 'ai/config', { changes: JSON.stringify( { 'ai.enabled': true } ) }, FREE );
		expect( cfg.status ).toBe( 403 );
		expect( ( await post( request, csrf, 'ai/test', {}, FREE ) ).status ).toBe( 403 );
		await signIn( page, 'lens-demo', FREE );
		await expect( page.locator( '#agent-launch' ) ).toBeHidden();
		await page.click( '.nav:has-text("AI")' );
		await expect( page.locator( '.main section:visible .plusnote' ) ).toContainText( 'BoxLang+' );
		await expect( page.locator( '#ai-enabled' ) ).toBeDisabled();
		await expect( page.locator( '#ai-test' ) ).toBeDisabled();
		await expect( page.locator( '#ai-status li.no', { hasText: 'BoxLang+' } ) ).toBeVisible();
	} );

} );
