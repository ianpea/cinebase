<script lang="ts">
	import { page } from '$app/state';
	import ServerCrashIcon from '@lucide/svelte/icons/server-crash';
	import TriangleAlertIcon from '@lucide/svelte/icons/triangle-alert';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Card from '$lib/components/ui/card/index.js';

	const isServerError = $derived(page.status >= 500);

	// SvelteKit's own message for a 5xx is just "Internal Error", which says nothing useful.
	const detail = $derived(page.error?.message);
	const message = $derived(
		isServerError && (!detail || detail === 'Internal Error')
			? 'The server ran into a problem. Please try again in a moment.'
			: (detail ?? 'Something unexpected happened.')
	);
</script>

<div class="mx-auto max-w-md py-8">
	<Card.Root class="items-center gap-5 p-(--card-spacing) text-center">
		<div class="bg-muted text-muted-foreground flex size-12 items-center justify-center rounded-full">
			{#if isServerError}
				<ServerCrashIcon class="size-6" />
			{:else}
				<TriangleAlertIcon class="size-6" />
			{/if}
		</div>

		<div class="space-y-1.5">
			<h1 class="text-lg font-semibold">
				{isServerError ? 'Something went wrong' : 'Page not found'}
			</h1>
			<p class="text-muted-foreground">{message}</p>
		</div>

		<div class="flex flex-wrap justify-center gap-2">
			<Button variant="outline" onclick={() => location.reload()}>Try again</Button>
			<Button href="/">Back to movies</Button>
		</div>
	</Card.Root>
</div>
