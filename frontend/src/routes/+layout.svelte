<script lang="ts">
	import './layout.css';
	import ClapperboardIcon from '@lucide/svelte/icons/clapperboard';
	import { Toaster } from '$lib/components/ui/sonner/index.js';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>

<div class="min-h-screen">
	<header class="border-b">
		<div class="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-4">
			<a href="/" class="flex items-center gap-2 text-lg font-semibold">
				<ClapperboardIcon class="size-5" />
				Cinebase
			</a>
			<nav class="flex items-center gap-1 text-sm">
				<a href="/" class="rounded-md px-3 py-1.5 hover:bg-muted">Movies</a>
				<a href="/people" class="rounded-md px-3 py-1.5 hover:bg-muted">People</a>
			</nav>
		</div>
	</header>
	<main class="mx-auto max-w-5xl px-4 py-8">
		{@render children()}
	</main>
</div>

<Toaster />
