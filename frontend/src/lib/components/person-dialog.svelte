<script lang="ts">
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import { Textarea } from '$lib/components/ui/textarea/index.js';
	import { errorMessage, mutate } from '$lib/graphql/api';
	import { CreatePersonDocument, UpdatePersonDocument } from '$lib/graphql/queries';
	import type { Person, PersonInput } from '$lib/graphql/types';

	/** Reusable dialog for creating and editing a person's details. */
	let {
		open = $bindable(false),
		person = null,
		onsaved
	}: { open?: boolean; person?: Person | null; onsaved: (person: Person) => void } = $props();

	let name = $state('');
	let biography = $state('');
	let birthDate = $state('');
	let saving = $state(false);
	let error = $state<string | null>(null);

	$effect(() => {
		if (!open) return;
		name = person?.name ?? '';
		biography = person?.biography ?? '';
		birthDate = person?.birthDate ?? '';
		error = null;
	});

	const validationError = $derived(
		!name.trim()
			? 'Name is required.'
			: name.trim().length > 255
				? 'Name must be at most 255 characters.'
				: null
	);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (validationError) return;
		saving = true;
		error = null;
		const input: PersonInput = {
			name: name.trim(),
			biography: biography.trim() || null,
			birthDate: birthDate.trim() || null
		};
		try {
			const data = person
				? await mutate(UpdatePersonDocument, { id: person.id, input })
				: await mutate(CreatePersonDocument, { input });
			open = false;
			onsaved(person ? data.updatePerson : data.createPerson);
		} catch (e) {
			error = errorMessage(e);
		} finally {
			saving = false;
		}
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Content>
		<Dialog.Header>
			<Dialog.Title>{person ? 'Edit person' : 'Add person'}</Dialog.Title>
			<Dialog.Description>
				{person ? 'Update this person’s details.' : 'Add a person who can be cast or credited.'}
			</Dialog.Description>
		</Dialog.Header>

		<form onsubmit={submit} class="space-y-4" novalidate>
			<div class="space-y-2">
				<Label for="person-name">Name</Label>
				<Input id="person-name" bind:value={name} placeholder="e.g. Matthew McConaughey" />
			</div>
			<div class="space-y-2">
				<Label for="person-birth-date">Birth date</Label>
				<Input id="person-birth-date" type="date" bind:value={birthDate} />
			</div>
			<div class="space-y-2">
				<Label for="person-biography">Biography</Label>
				<Textarea id="person-biography" bind:value={biography} rows={3} placeholder="Short bio…" />
			</div>

			{#if validationError}
				<p class="text-sm text-destructive" role="alert">{validationError}</p>
			{:else if error}
				<p class="text-sm text-destructive" role="alert">{error}</p>
			{/if}

			<Dialog.Footer>
				<Button variant="outline" type="button" disabled={saving} onclick={() => (open = false)}>
					Cancel
				</Button>
				<Button type="submit" disabled={saving || !!validationError}>
					{#if saving}<Spinner />{/if}
					{person ? 'Save changes' : 'Add person'}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
