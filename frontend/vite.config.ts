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

			// There are no cookies, sessions or form actions here, so the CSRF check protects
			// nothing; the only form-encoded request it sees is the multipart artwork upload,
			// which it would 403 whenever the browser's Origin differs from ORIGIN (127.0.0.1 vs
			// localhost, a non-default FRONTEND_PORT, any non-loopback host). `'*'` is the
			// documented way to switch the check off (`checkOrigin` is deprecated in its favour).
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
