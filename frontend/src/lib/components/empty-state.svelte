<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		description,
		children
	}: { title: string; description?: string; children?: Snippet } = $props();
</script>

<div class="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center">
	<p class="font-medium">{title}</p>
	{#if description}
		<p class="max-w-prose text-sm text-muted-foreground">{description}</p>
	{/if}
	{@render children?.()}
</div>
