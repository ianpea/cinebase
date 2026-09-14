<script lang="ts">
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';

	let {
		open = $bindable(false),
		title,
		description,
		confirmLabel = 'Delete',
		pending = false,
		onconfirm
	}: {
		open?: boolean;
		title: string;
		description: string;
		confirmLabel?: string;
		pending?: boolean;
		onconfirm: () => void;
	} = $props();
</script>

<Dialog.Root bind:open>
	<Dialog.Content>
		<Dialog.Header>
			<Dialog.Title>{title}</Dialog.Title>
			<Dialog.Description>{description}</Dialog.Description>
		</Dialog.Header>
		<Dialog.Footer>
			<Button variant="outline" disabled={pending} onclick={() => (open = false)}>Cancel</Button>
			<Button variant="destructive" disabled={pending} onclick={onconfirm}>
				{#if pending}<Spinner />{/if}
				{confirmLabel}
			</Button>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
