<script lang="ts">
	import { Alert, AlertDescription, AlertTitle } from '$lib/components/ui/alert/index.js';
	import { Button } from '$lib/components/ui/button/index.js';

	let {
		message,
		title = 'Something went wrong',
		onretry
	}: { message: string; title?: string; onretry?: () => void } = $props();
</script>

<Alert variant="destructive">
	<AlertTitle>{title}</AlertTitle>
	<AlertDescription>
		<p>{message}</p>
		{#if onretry}
			<Button variant="outline" size="sm" class="mt-2" onclick={onretry}>Try again</Button>
		{/if}
	</AlertDescription>
</Alert>
