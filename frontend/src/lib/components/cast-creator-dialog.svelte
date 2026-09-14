<script lang="ts">
	import UserIcon from '@lucide/svelte/icons/user';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import { errorMessage, mutate, request } from '$lib/graphql/api';
	import {
		AddCastMemberDocument,
		AddCreatorDocument,
		PeopleDocument,
		UpdateCastMemberDocument,
		UpdateCreatorDocument
	} from '$lib/graphql/queries';
	import type { Person } from '$lib/graphql/types';

	/** Existing relationship being edited, if any. */
	type Existing = { id: string; personId: string; personName: string; detail: string };

	/**
	 * One dialog for both relationship types: `mode="cast"` edits a character name,
	 * `mode="creator"` edits a job. Adding searches person-service (through movie-service gRPC);
	 * editing keeps the person fixed and only changes the detail field.
	 */
	let {
		open = $bindable(false),
		mode,
		movieId,
		existing = null,
		onsaved
	}: {
		open?: boolean;
		mode: 'cast' | 'creator';
		movieId: string;
		existing?: Existing | null;
		onsaved: () => void;
	} = $props();

	const editing = $derived(existing !== null);
	const detailLabel = $derived(mode === 'cast' ? 'Character name' : 'Job');
	const entityLabel = $derived(mode === 'cast' ? 'cast member' : 'creator');

	let personQuery = $state('');
	let matches = $state<Person[]>([]);
	let loadingPeople = $state(false);
	let peopleError = $state<string | null>(null);
	let selected = $state<{ id: string; name: string } | null>(null);
	let detail = $state('');
	let saving = $state(false);
	let error = $state<string | null>(null);

	// Reset the form each time the dialog opens.
	$effect(() => {
		if (!open) return;
		personQuery = '';
		error = null;
		selected = existing ? { id: existing.personId, name: existing.personName } : null;
		detail = existing?.detail ?? '';
	});

	// Debounced person search: only when picking a person (add mode).
	$effect(() => {
		if (!open || editing) return;
		const query = personQuery;
		const timer = setTimeout(() => void searchPeople(query), 300);
		return () => clearTimeout(timer);
	});

	async function searchPeople(query: string) {
		loadingPeople = true;
		peopleError = null;
		try {
			const data = await request(PeopleDocument, { search: query || null, page: 0, size: 10 });
			matches = data.people.items;
		} catch (e) {
			peopleError = errorMessage(e);
		} finally {
			loadingPeople = false;
		}
	}

	const validationError = $derived(
		!selected ? 'Select a person first.' : !detail.trim() ? `${detailLabel} is required.` : null
	);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!selected || validationError) return;
		saving = true;
		error = null;
		const value = detail.trim();
		try {
			if (mode === 'cast') {
				const input = { movieId, personId: selected.id, characterName: value };
				if (existing) await mutate(UpdateCastMemberDocument, { id: existing.id, input });
				else await mutate(AddCastMemberDocument, { input });
			} else {
				const input = { movieId, personId: selected.id, job: value };
				if (existing) await mutate(UpdateCreatorDocument, { id: existing.id, input });
				else await mutate(AddCreatorDocument, { input });
			}
			open = false;
			onsaved();
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
			<Dialog.Title>
				{editing ? `Edit ${entityLabel}` : `Add ${entityLabel}`}
			</Dialog.Title>
			<Dialog.Description>
				{editing
					? `Update the ${detailLabel.toLowerCase()} for ${existing?.personName}.`
					: `Search for an existing person and set their ${detailLabel.toLowerCase()}.`}
			</Dialog.Description>
		</Dialog.Header>

		<form onsubmit={submit} class="space-y-4" novalidate>
			{#if editing}
				<div class="space-y-2">
					<Label>Person</Label>
					<p class="text-sm font-medium">{existing?.personName}</p>
				</div>
			{:else}
				<div class="space-y-2">
					<Label for="person-search">Person</Label>
					<Input
						id="person-search"
						bind:value={personQuery}
						placeholder="Search people by name…"
						autocomplete="off"
					/>

					<div class="max-h-52 overflow-y-auto rounded-md border">
						{#if loadingPeople}
							<div class="flex items-center gap-2 px-3 py-3 text-sm text-muted-foreground">
								<Spinner /> Searching people…
							</div>
						{:else if peopleError}
							<p class="px-3 py-3 text-sm text-destructive">{peopleError}</p>
						{:else if matches.length === 0}
							<p class="px-3 py-3 text-sm text-muted-foreground">
								No people found. Add people on the <a href="/people" class="underline">People page</a>.
							</p>
						{:else}
							<ul class="divide-y">
								{#each matches as person (person.id)}
									<li>
										<button
											type="button"
											class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-muted"
											class:bg-muted={selected?.id === person.id}
											aria-pressed={selected?.id === person.id}
											onclick={() => (selected = { id: person.id, name: person.name })}
										>
											<UserIcon class="size-4 text-muted-foreground" />
											<span class="truncate">{person.name}</span>
										</button>
									</li>
								{/each}
							</ul>
						{/if}
					</div>

					{#if selected}
						<p class="text-sm text-muted-foreground">
							Selected: <span class="font-medium text-foreground">{selected.name}</span>
						</p>
					{/if}
				</div>
			{/if}

			<div class="space-y-2">
				<Label for="relationship-detail">{detailLabel}</Label>
				<Input
					id="relationship-detail"
					bind:value={detail}
					placeholder={mode === 'cast' ? 'e.g. Cooper' : 'e.g. Director'}
				/>
			</div>

			{#if validationError && (selected || editing)}
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
					{editing ? 'Save changes' : `Add ${entityLabel}`}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
