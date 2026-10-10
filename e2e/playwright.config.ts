import { join } from 'path';
import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.PORT || '8085';
const FREE_PORT = process.env.FREE_PORT || '8090';
const ORM_PORT = process.env.ORM_PORT || '8091';
const RO_PORT = process.env.RO_PORT || '8092';
const MCP_PORT = process.env.MCP_PORT || '11435';
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
		// A stand-in for a local Ollama (harness/mock-ai.py): deterministic tool calls and embeddings, so the AI specs need no model
		{
			command: 'python3 ../harness/mock-ai.py',
			url: 'http://127.0.0.1:11434/api/tags',
			timeout: 30_000,
			reuseExistingServer: !process.env.CI,
		},
		// Stand-ins for the MCP servers (harness/mock-mcp, written in BoxLang, served by its own MiniServer): the specs need no internet.
				{
			command: 'bash ../harness/start-mock-mcp.sh',
			url: `http://127.0.0.1:${ MCP_PORT }/mcp.bxs/_log`,
			timeout: 120_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { MOCK_MCP_PORT: MCP_PORT },
		},
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { PORT, SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: process.env.LENS_LICENSE ?? 'plus', LENS_TEST_API_KEY: 'sk-live-0123456789supersecret', LENS_MCP_BASE: `http://127.0.0.1:${ MCP_PORT }/mcp.bxs` },
		},
		// A second server without a license, to prove what stays free and what is locked
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ FREE_PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { PORT: FREE_PORT, RUN_DIR: join( __dirname, '..', 'harness', '.run-free' ), SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: 'none', LENS_RELOAD_ASSETS: 'false', LENS_SERVER_HEADER: 'true', LENS_SERVER_NAME: 'free-node', LENS_SERVER_ADDRESS: '192.0.2.55' },
		},
		// A read only console with Lensy on and MCP servers already switched on (harness/seed), to prove what read only refuses
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ RO_PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: {
				PORT: RO_PORT, RUN_DIR: join( __dirname, '..', 'harness', '.run-ro' ), SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: 'plus', WITH_AI: '1',
				LENS_READONLY: 'true', LENS_SEED_OVERRIDES: join( __dirname, '..', 'harness', 'seed', 'readonly-mcp.json' ), LENS_MCP_BASE: `http://127.0.0.1:${ MCP_PORT }/mcp.bxs`,
			},
		},
		// A third server with bx-orm, to prove ORM SQL and statistics
		{
			command: 'bash ../harness/start.sh',
			url: `http://127.0.0.1:${ ORM_PORT }/index.bxm`,
			timeout: 240_000,
			reuseExistingServer: !process.env.CI,
			stdout: 'pipe',
			env: { PORT: ORM_PORT, RUN_DIR: join( __dirname, '..', 'harness', '.run-orm' ), SKIP_BUILD: process.env.SKIP_BUILD ?? '1', LENS_LICENSE: 'plus', WITH_ORM: '1' },
		},
	],

} );
