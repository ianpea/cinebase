<script lang="ts">
	import ChevronLeftIcon from '@lucide/svelte/icons/chevron-left';
	import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
	import { Button } from '$lib/components/ui/button/index.js';

	let {
		page = $bindable(0),
		onPageChange,
		totalPages,
		total,
		itemLabel = 'item',
		pluralLabel = `${itemLabel}s`,
		disabled = false
	}: {
		page?: number;
		onPageChange?: (page: number) => void;
		totalPages: number;
		total: number;
		itemLabel?: string;
		pluralLabel?: string;
		disabled?: boolean;
	} = $props();

	// The API is 0-based and pages are clamped there too, so guard the edges here as well.
	const canPrevious = $derived(page > 0);
	const canNext = $derived(page + 1 < totalPages);

	function goTo(next: number) {
		page = next;
		onPageChange?.(next);
	}
</script>

<div class="flex flex-wrap items-center justify-between gap-3">
	<p class="text-sm text-muted-foreground" aria-live="polite">
		{total}
		{total === 1 ? itemLabel : pluralLabel}{totalPages > 1
			? ` · page ${page + 1} of ${totalPages}`
			: ''}
	</p>
	{#if totalPages > 1}
		<div class="flex items-center gap-2">
			<Button
				variant="outline"
				size="sm"
				disabled={disabled || !canPrevious}
				onclick={() => goTo(page - 1)}
			>
				<ChevronLeftIcon />
				Previous
			</Button>
			<Button
				variant="outline"
				size="sm"
				disabled={disabled || !canNext}
				onclick={() => goTo(page + 1)}
			>
				Next
				<ChevronRightIcon />
			</Button>
		</div>
	{/if}
</div>
