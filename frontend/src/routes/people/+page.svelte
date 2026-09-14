<script lang="ts">
	import PencilIcon from '@lucide/svelte/icons/pencil';
	import PlusIcon from '@lucide/svelte/icons/plus';
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import { untrack } from 'svelte';
	import { toast } from 'svelte-sonner';
	import ConfirmDialog from '$lib/components/confirm-dialog.svelte';
	import EmptyState from '$lib/components/empty-state.svelte';
	import ErrorState from '$lib/components/error-state.svelte';
	import LoadingState from '$lib/components/loading-state.svelte';
	import Pagination from '$lib/components/pagination.svelte';
	import PersonDialog from '$lib/components/person-dialog.svelte';
	import SearchInput from '$lib/components/search-input.svelte';
	import { Button } from '$lib/components/ui/button/index.js';
	import { errorMessage, mutate, request } from '$lib/graphql/api';
	import { DeletePersonDocument, PeopleDocument } from '$lib/graphql/queries';
	import type { Person, PersonPage } from '$lib/graphql/types';
	import { formatDate } from '$lib/format';

	const PAGE_SIZE = 12;

	let search = $state('');
	let page = $state(0);

	let result = $state<PersonPage | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let dialogOpen = $state(false);
	let editing = $state<Person | null>(null);
	let confirmOpen = $state(false);
	let pendingDelete = $state<Person | null>(null);
	let deleting = $state(false);

	async function load() {
		loading = true;
		error = null;
		try {
			const data = await request(PeopleDocument, {
				search: search.trim() || null,
				page,
				size: PAGE_SIZE
			});
			result = data.people;
		} catch (e) {
			error = errorMessage(e);
			result = null;
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		void [search, page];
		void load();
	});

	// A new search always starts from the first page.
	$effect(() => {
		void search;
		untrack(() => (page = 0));
	});

	function addPerson() {
		editing = null;
		dialogOpen = true;
	}

	function editPerson(person: Person) {
		editing = person;
		dialogOpen = true;
	}

	function onSaved(person: Person) {
		toast.success(editing ? `Updated ${person.name}.` : `Added ${person.name}.`);
		void load();
	}

	function askDelete(person: Person) {
		pendingDelete = person;
		confirmOpen = true;
	}

	async function confirmDelete() {
		if (!pendingDelete) return;
		deleting = true;
		try {
			await mutate(DeletePersonDocument, { id: pendingDelete.id });
			toast.success(`Deleted ${pendingDelete.name}.`);
			confirmOpen = false;
			await load();
		} catch (e) {
			toast.error(errorMessage(e));
		} finally {
			deleting = false;
		}
	}
</script>

<section class="space-y-6">
	<div class="flex flex-wrap items-center justify-between gap-3">
		<div>
			<h1 class="text-3xl font-bold tracking-tight">People</h1>
			<p class="text-sm text-muted-foreground">
				People can be added to a movie as cast or as a creator.
			</p>
		</div>
		<Button onclick={addPerson}>
			<PlusIcon />
			Add person
		</Button>
	</div>

	<div class="max-w-md">
		<SearchInput bind:value={search} placeholder="Search people by name…" />
	</div>

	{#if loading && !result}
		<LoadingState label="Loading people…" />
	{:else if error}
		<ErrorState message={error} onretry={load} />
	{:else if result && result.items.length === 0}
		<EmptyState
			title={search ? 'No people match your search' : 'No people yet'}
			description={search
				? 'Try a different name or clear the search.'
				: 'Add actors and directors so you can attach them to movies.'}
		>
			{#if !search}
				<Button onclick={addPerson}><PlusIcon /> Add person</Button>
			{/if}
		</EmptyState>
	{:else if result}
		<ul class="divide-y rounded-lg ring-1 ring-foreground/10" class:opacity-60={loading}>
			{#each result.items as person (person.id)}
				<li class="flex flex-wrap items-center justify-between gap-3 p-3">
					<div class="min-w-0">
						<p class="font-medium">{person.name}</p>
						<p class="text-xs text-muted-foreground">
							Born {formatDate(person.birthDate)}
						</p>
						{#if person.biography}
							<p class="line-clamp-2 max-w-prose text-sm text-muted-foreground">
								{person.biography}
							</p>
						{/if}
					</div>
					<div class="flex gap-1">
						<Button variant="ghost" size="icon-sm" onclick={() => editPerson(person)}>
							<PencilIcon />
							<span class="sr-only">Edit {person.name}</span>
						</Button>
						<Button variant="ghost" size="icon-sm" onclick={() => askDelete(person)}>
							<Trash2Icon />
							<span class="sr-only">Delete {person.name}</span>
						</Button>
					</div>
				</li>
			{/each}
		</ul>
		<Pagination
			bind:page
			totalPages={result.totalPages}
			total={result.total}
			itemLabel="person"
			pluralLabel="people"
			disabled={loading}
		/>
	{/if}
</section>

<PersonDialog bind:open={dialogOpen} person={editing} onsaved={onSaved} />

<ConfirmDialog
	bind:open={confirmOpen}
	title="Delete person"
	description={`This deletes ${pendingDelete?.name ?? 'this person'} and their cast and creator credits.`}
	pending={deleting}
	onconfirm={confirmDelete}
/>
