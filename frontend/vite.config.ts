import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-node';
import {sveltekit} from '@sveltejs/kit/vite';
import {defineConfig} from 'vitest/config';

export default defineConfig(({mode}) => ({
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({filename}) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
			},

			// adapter-node produces a standalone Node server in build/ that Docker runs.
			adapter: adapter({out: 'build'}),

			// `/graphql` and `/uploads` are not SvelteKit routes — `src/hooks.server.ts` passes
			// them straight through to movie-service. SvelteKit's CSRF check guards its own form
			// actions using ambient cookies, and this app has none (no auth by design), so the
			// check protects nothing here. Left on, it rejects the multipart artwork upload
			// whenever the browser's Origin differs from the configured ORIGIN — e.g. reaching the
			// app at 127.0.0.1:3000 instead of localhost:3000 — while JSON queries keep working,
			// which is a confusing failure to debug. `'*'` is the documented way to switch the
			// origin check off (`checkOrigin` is deprecated in its favour).
			csrf: {trustedOrigins: ['*']}
		})
	],
	server: {
		proxy: {
			// Forward GraphQL requests to movie-service during local development.
			'/graphql': {
				target: 'http://localhost:8081',
				changeOrigin: true
			},
			// Artwork files are served by movie-service from its uploads directory.
			'/uploads': {
				target: 'http://localhost:8081',
				changeOrigin: true
			}
		}
	},

	// Component tests run under Vitest (`mode === 'test'`). `svelte` maps the `browser` condition
	// to its client build and `default` to its server build, and `@testing-library/svelte` needs
	// the client one, so the condition has to be requested explicitly.
	resolve: mode === 'test' ? {conditions: ['browser']} : undefined,

	test: {
		environment: 'jsdom',
		include: ['src/**/*.{test,spec}.{js,ts}'],
		setupFiles: ['./vitest-setup.ts']
	}
}));
