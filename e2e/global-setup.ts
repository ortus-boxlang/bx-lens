import { request, FullConfig } from '@playwright/test';

// The first request to any template compiles it, which Lens would report as a slow template and a slow query.
// Visit every page once before the tests so they see steady state numbers.
const PAGES = [
	'index', 'orders', 'n-plus-one', 'slow', 'caught-exception', 'http', 'cache', 'timers', 'functions', 'forms', 'session', 'transaction', 'extend', 'xss',
	'api/orders.json', 'api/rates.json', 'events',
];

export default async function globalSetup( config: FullConfig ) {
	const baseURL = config.projects[ 0 ].use.baseURL!;
	const ctx = await request.newContext( { baseURL } );
	for ( let round = 0; round < 2; round++ ) {
		for ( const p of PAGES ) {
			await ctx.get( `/${ p }.bxm`, { timeout: 60_000 } );
		}
	}
	await ctx.post( '/forms.bxm', { form: { email: 'warm@example.com', password: 'x' } } );
	await ctx.get( '/error.bxm', { failOnStatusCode: false } );
	await ctx.dispose();
}
