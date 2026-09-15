import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-node';
import {sveltekit} from '@sveltejs/kit/vite';
import {defineConfig} from 'vitest/config';

export default defineConfig(({mode}) => ({
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes for project files; node_modules keeps its own config.
				runes: ({filename}) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
			},

			// build/ is what the frontend Dockerfile copies in.
			adapter: adapter({out: 'build'}),

			// No cookies, sessions or form actions exist, so the CSRF check protects nothing; the
			// only form-encoded request is the multipart artwork upload, which would 403 whenever
			// the browser's Origin differs from ORIGIN. `'*'` is the supported way to disable it
			// (`checkOrigin` is deprecated).
			csrf: {trustedOrigins: ['*']}
		})
	],
	server: {
		// Dev-only twin of the proxy in src/hooks.server.ts (adapter-node has no dev server).
		proxy: {
			'/graphql': {
				target: 'http://localhost:8081',
				changeOrigin: true
			},
			'/uploads': {
				target: 'http://localhost:8081',
				changeOrigin: true
			}
		}
	},

	// `svelte` maps `browser` to its client build and `default` to its server build, and
	// @testing-library/svelte needs the client one, so the condition has to be requested explicitly.
	resolve: mode === 'test' ? {conditions: ['browser']} : undefined,

	test: {
		environment: 'jsdom',
		include: ['src/**/*.{test,spec}.{js,ts}'],
		setupFiles: ['./vitest-setup.ts']
	}
}));
