<script lang="ts">
	import { Spinner } from '$lib/components/ui/spinner/index.js';

	let { label = 'Loading…' }: { label?: string } = $props();
</script>

<div class="flex items-center justify-center gap-2 py-12 text-sm text-muted-foreground" role="status">
	<Spinner />
	<span>{label}</span>
</div>
