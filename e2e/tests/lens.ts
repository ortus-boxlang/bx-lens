import { expect, Page, Locator, APIRequestContext, test as base } from '@playwright/test';

/** Helpers for driving the Lens bar. */
export class Lens {
	constructor( public page: Page ) {}

	get root(): Locator { return this.page.locator( '#bxlens' ); }
	get bar(): Locator { return this.page.locator( '#bxlens .lens' ); }
	get panel(): Locator { return this.page.locator( '#bxlens .panel' ); }
	/** Table rows of whatever panel is showing. Hidden custom panels stay in the DOM, so only visible rows count. */
	get rows(): Locator { return this.page.locator( '#bxlens tbody tr:visible' ); }
	get strip(): Locator { return this.page.locator( '#bxlens .dock .bar' ).first(); }
	tab( name: string ): Locator { return this.page.locator( '#bxlens .tab', { hasText: new RegExp( `^\\s*${ name }\\b` ) } ).first(); }

	async visit( path: string ) {
		await this.page.goto( path );
		await expect( this.bar ).toBeVisible();
	}

	/** Open the panel on a tab. */
	async open( name: string ) {
		// The bar loads its markup after the page, so wait for it before pressing keys
		await expect( this.bar ).toBeVisible();
		if ( !( await this.panel.isVisible() ) ) {
			await this.page.keyboard.press( 'Control+`' );
			await expect( this.panel ).toBeVisible();
		}
		await this.tab( name ).click();
		await expect( this.tab( name ) ).toHaveAttribute( 'aria-selected', 'true' );
	}

	/** The request payload Lens embedded in the page. */
	async data(): Promise<any> {
		return this.page.evaluate( () => JSON.parse( document.getElementById( 'bxlens-data' )!.textContent! ) );
	}
}

export const test = base.extend<{ lens: Lens }>( {
	lens: async ( { page }, use ) => { await use( new Lens( page ) ); },
} );
export { expect };

/** Sign in to the console with the API and read its JSON routes, for checks that belong to the console (issues, history). */
export async function consoleApi( request: APIRequestContext, password = 'lens-demo' ) {
	const r = await request.post( '/~bxlens/index.bxm/login', { headers: { 'X-Lens-Login': '1' }, form: { password } } );
	expect( r.status() ).toBe( 200 );
	return {
		get: async ( path: string ) => {
			const res = await request.get( `/~bxlens/index.bxm/api/${ path }`, { failOnStatusCode: false } );
			return { status: res.status(), json: res.status() === 200 ? await res.json() : null };
		},
	};
}
