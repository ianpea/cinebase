<script lang="ts">
	import SearchIcon from '@lucide/svelte/icons/search';
	import XIcon from '@lucide/svelte/icons/x';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Button } from '$lib/components/ui/button/index.js';

	/**
	 * Search field with debounced output: `value` only updates once typing pauses, so the parent
	 * can query on every `value` change without hitting the API on each keystroke.
	 */
	let {
		value = $bindable(''),
		placeholder = 'Search…',
		delay = 300
	}: { value?: string; placeholder?: string; delay?: number } = $props();

	let text = $state(value);

	$effect(() => {
		const next = text;
		const timer = setTimeout(() => (value = next), delay);
		return () => clearTimeout(timer);
	});
</script>

<div class="relative">
	<SearchIcon
		class="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
	/>
	<Input bind:value={text} {placeholder} class="pl-8" aria-label={placeholder} />
	{#if text}
		<Button
			variant="ghost"
			size="icon-xs"
			class="absolute top-1/2 right-2 -translate-y-1/2"
			onclick={() => (text = '')}
		>
			<XIcon />
			<span class="sr-only">Clear search</span>
		</Button>
	{/if}
</div>
