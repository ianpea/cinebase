<script lang="ts">
	import PlusIcon from '@lucide/svelte/icons/plus';
	import { untrack } from 'svelte';
	import { toast } from 'svelte-sonner';
	import ConfirmDialog from '$lib/components/confirm-dialog.svelte';
	import EmptyState from '$lib/components/empty-state.svelte';
	import ErrorState from '$lib/components/error-state.svelte';
	import LoadingState from '$lib/components/loading-state.svelte';
	import MovieCard from '$lib/components/movie-card.svelte';
	import MovieDialog from '$lib/components/movie-dialog.svelte';
	import Pagination from '$lib/components/pagination.svelte';
	import SearchInput from '$lib/components/search-input.svelte';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { errorMessage, mutate, request } from '$lib/graphql/api';
	import { DeleteMovieDocument, MoviesDocument } from '$lib/graphql/queries';
	import type { Movie, MoviePage, MovieSortField, SortDirection } from '$lib/graphql/types';

	const PAGE_SIZE = 12;

	// Declared once and used for both the trigger label lookup (`items`) and the menu entries.
	const SORT_FIELDS: { value: MovieSortField; label: string }[] = [
		{ value: 'CREATED_AT', label: 'Recently added' },
		{ value: 'TITLE', label: 'Title' },
		{ value: 'RELEASE_YEAR', label: 'Release year' }
	];
	const SORT_DIRECTIONS: { value: SortDirection; label: string }[] = [
		{ value: 'DESC', label: 'Descending' },
		{ value: 'ASC', label: 'Ascending' }
	];

	let search = $state('');
	let sortField = $state<MovieSortField>('CREATED_AT');
	let sortDirection = $state<SortDirection>('DESC');
	let page = $state(0);

	let result = $state<MoviePage | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let dialogOpen = $state(false);
	let editing = $state<Movie | null>(null);
	let confirmOpen = $state(false);
	let pendingDelete = $state<Movie | null>(null);
	let deleting = $state(false);

	async function load() {
		loading = true;
		error = null;
		try {
			const data = await request(MoviesDocument, {
				search: search.trim() || null,
				sort: { field: sortField, direction: sortDirection },
				page,
				size: PAGE_SIZE
			});
			result = data.movies;
		} catch (e) {
			error = errorMessage(e);
			result = null;
		} finally {
			loading = false;
		}
	}

	// Reload for the current search, sort and page. The search input already debounces.
	$effect(() => {
		void [search, sortField, sortDirection, page];
		void load();
	});

	// Any change to the query or sorting starts again from the first page.
	$effect(() => {
		void [search, sortField, sortDirection];
		untrack(() => (page = 0));
	});

	function addMovie() {
		editing = null;
		dialogOpen = true;
	}

	function editMovie(movie: Movie) {
		editing = movie;
		dialogOpen = true;
	}

	function onSaved(movie: Movie) {
		toast.success(editing ? `Updated “${movie.title}”.` : `Added “${movie.title}”.`);
		void load();
	}

	function askDelete(movie: Movie) {
		pendingDelete = movie;
		confirmOpen = true;
	}

	async function confirmDelete() {
		if (!pendingDelete) return;
		deleting = true;
		try {
			await mutate(DeleteMovieDocument, { id: pendingDelete.id });
			toast.success(`Deleted “${pendingDelete.title}”.`);
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
			<h1 class="text-3xl font-bold tracking-tight">Movies</h1>
			<p class="text-sm text-muted-foreground">Browse, search and manage your movie library.</p>
		</div>
		<Button onclick={addMovie}>
			<PlusIcon />
			Add movie
		</Button>
	</div>

	<div class="flex flex-wrap items-end gap-3">
		<div class="min-w-56 flex-1">
			<SearchInput bind:value={search} placeholder="Search movies by title…" />
		</div>
		<div class="space-y-1">
			<Label for="sort-field" class="text-xs text-muted-foreground">Sort by</Label>
			<Select.Root
				type="single"
				items={SORT_FIELDS}
				value={sortField}
				onValueChange={(value) => value && (sortField = value as MovieSortField)}
			>
				<Select.Trigger id="sort-field"><Select.Value placeholder="Sort by" /></Select.Trigger>
				<Select.Content>
					{#each SORT_FIELDS as item (item.value)}
						<Select.Item value={item.value}>{item.label}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
		</div>
		<div class="space-y-1">
			<Label for="sort-direction" class="text-xs text-muted-foreground">Order</Label>
			<Select.Root
				type="single"
				items={SORT_DIRECTIONS}
				value={sortDirection}
				onValueChange={(value) => value && (sortDirection = value as SortDirection)}
			>
				<Select.Trigger id="sort-direction"><Select.Value placeholder="Order" /></Select.Trigger>
				<Select.Content>
					{#each SORT_DIRECTIONS as item (item.value)}
						<Select.Item value={item.value}>{item.label}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
		</div>
	</div>

	{#if loading && !result}
		<LoadingState label="Loading movies…" />
	{:else if error}
		<ErrorState message={error} onretry={load} />
	{:else if result && result.items.length === 0}
		<EmptyState
			title={search ? 'No movies match your search' : 'No movies yet'}
			description={search
				? 'Try a different title or clear the search.'
				: 'Add your first movie to get started.'}
		>
			{#if !search}
				<Button onclick={addMovie}><PlusIcon /> Add movie</Button>
			{/if}
		</EmptyState>
	{:else if result}
		<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4" class:opacity-60={loading}>
			{#each result.items as movie (movie.id)}
				<MovieCard {movie} onedit={editMovie} ondelete={askDelete} />
			{/each}
		</div>
		<Pagination
			bind:page
			totalPages={result.totalPages}
			total={result.total}
			itemLabel="movie"
			disabled={loading}
		/>
	{/if}
</section>

<MovieDialog bind:open={dialogOpen} movie={editing} onsaved={onSaved} />

<ConfirmDialog
	bind:open={confirmOpen}
	title="Delete movie"
	description={`This permanently deletes “${pendingDelete?.title ?? ''}” and its artwork.`}
	pending={deleting}
	onconfirm={confirmDelete}
/>

