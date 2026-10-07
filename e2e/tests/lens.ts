import { expect, Page, Locator, test as base } from '@playwright/test';

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
