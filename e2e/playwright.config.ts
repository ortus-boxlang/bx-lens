import { join } from 'path';
import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.PORT || '8085';
const FREE_PORT = process.env.FREE_PORT || '8090';
const chromium = process.env.LENS_CHROMIUM; // optional path to a Chromium build, for machines where `playwright install` is not possible

export default defineConfig( {
	testDir: './tests',
	globalSetup: './global-setup.ts',
	// The harness is one shared server with one shared history, so tests run one at a time
	fullyParallel: false,
	workers: 1,
	retries: process.env.CI ? 1 : 0,
	timeout: 30_000,
	expect: { timeout: 7_000 },
	reporter: process.env.CI ? [ [ 'list' ], [ 'html', { open: 'never' } ], [ 'junit', { outputFile: 'test-results/junit.xml' } ] ] : [ [ 'list' ] ],
	use: {
		baseURL: `http://127.0.0.1:${ PORT }`,
		viewport: { width: 1280, height: 760 },
		colorScheme: 'dark',
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure',
		launchOptions: chromium ? { executablePath: chromium, args: [ '--no-sandbox' ] } : { args: [ '--no-sandbox' ] },
	},
	projects: [ { name: 'chromium', use: { ...devices[ 'Desktop Chrome' ], viewport: { width: 1280, height: 760 } } } ],
	// Starts the demo harness: MiniServer, the freshly built module and a Derby in-memory database
	webServer: [
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { PORT, SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: process.env.LENS_LICENSE ?? 'plus' },
		},
		// A second server without a license, to prove what stays free and what is locked
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ FREE_PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { PORT: FREE_PORT, RUN_DIR: join( __dirname, '..', 'harness', '.run-free' ), SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: 'none' },
		},
	],

} );
