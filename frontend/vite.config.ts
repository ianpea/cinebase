import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-auto';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => ({
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
			},

			// adapter-auto only supports some environments, see https://svelte.dev/docs/kit/adapter-auto for a list.
			// If your environment is not supported, or you settled on a specific environment, switch out the adapter.
			// See https://svelte.dev/docs/kit/adapters for more information about adapters.
			adapter: adapter()
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
	resolve: mode === 'test' ? { conditions: ['browser'] } : undefined,

	test: {
		environment: 'jsdom',
		include: ['src/**/*.{test,spec}.{js,ts}'],
		setupFiles: ['./vitest-setup.ts']
	}
}));
