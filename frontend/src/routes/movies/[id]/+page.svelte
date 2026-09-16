<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import ArrowLeftIcon from '@lucide/svelte/icons/arrow-left';
	import PencilIcon from '@lucide/svelte/icons/pencil';
	import PlusIcon from '@lucide/svelte/icons/plus';
	import RefreshCwIcon from '@lucide/svelte/icons/refresh-cw';
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import { toast } from 'svelte-sonner';
	import type { PageProps } from './$types';
	import ArtworkUpload from '$lib/components/artwork-upload.svelte';
	import CastCreatorDialog from '$lib/components/cast-creator-dialog.svelte';
	import ConfirmDialog from '$lib/components/confirm-dialog.svelte';
	import ErrorState from '$lib/components/error-state.svelte';
	import LoadingState from '$lib/components/loading-state.svelte';
	import MovieDialog from '$lib/components/movie-dialog.svelte';
	import { Badge } from '$lib/components/ui/badge/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { errorMessage, mutate, request } from '$lib/graphql/api';
	import {
		DeleteMovieDocument,
		MovieDetailDocument,
		RemoveCastMemberDocument,
		RemoveCreatorDocument,
		RemoveMovieArtworkDocument
	} from '$lib/graphql/queries';
	import type { Artwork, Movie, MovieCast, MovieCreator } from '$lib/graphql/types';
	import { titleCase } from '$lib/format';

	/** Shared shape for the cast/creator dialog when editing an existing relationship. */
	type Relation = { id: string; personId: string; personName: string; detail: string };

	let { data }: PageProps = $props();

	const movieId = $derived(page.params.id ?? '');

	// Initial result is from SSR loaded data. Subsequent results are re-fetched when need.
	// svelte-ignore state_referenced_locally
	let movie = $state<Movie | null>(data.movie);
	let loading = $state(false);
	// svelte-ignore state_referenced_locally
	let error = $state<string | null>(data.movieError);

	let movieDialogOpen = $state(false);

	let artworkOpen = $state(false);
	let artworkKey = $state(0);
	let replaceArtwork = $state<Artwork | null>(null);

	let castOpen = $state(false);
	let editingCast = $state<Relation | null>(null);
	let creatorOpen = $state(false);
	let editingCreator = $state<Relation | null>(null);

	let confirmOpen = $state(false);
	let confirmPending = $state(false);
	let pendingConfirm = $state<{
		title: string;
		description: string;
		action: () => Promise<void>;
	} | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			const detail = await request(MovieDetailDocument, { id: movieId });
			movie = detail.movie;
		} catch (e) {
			error = errorMessage(e);
			movie = null;
		} finally {
			loading = false;
		}
	}

	// SvelteKit re-runs `+page.server.ts` on every navigation to this route — including a
	// client-side one to another id — so new server data is applied here instead of being fetched
	// a second time by the browser.
	$effect(() => {
		movie = data.movie;
		error = data.movieError;
	});

	function askConfirm(title: string, description: string, action: () => Promise<void>) {
		pendingConfirm = { title, description, action };
		confirmOpen = true;
	}

	async function runConfirm() {
		if (!pendingConfirm) return;
		confirmPending = true;
		try {
			await pendingConfirm.action();
			confirmOpen = false;
		} catch (e) {
			toast.error(errorMessage(e));
		} finally {
			confirmPending = false;
		}
	}

	function openArtworkDialog(artwork: Artwork | null) {
		replaceArtwork = artwork;
		artworkKey += 1;
		artworkOpen = true;
	}

	function editCast(member: MovieCast) {
		editingCast = {
			id: member.id,
			personId: member.person.id,
			personName: member.person.name,
			detail: member.characterName
		};
		castOpen = true;
	}

	function editCreator(creator: MovieCreator) {
		editingCreator = {
			id: creator.id,
			personId: creator.person.id,
			personName: creator.person.name,
			detail: creator.job
		};
		creatorOpen = true;
	}
</script>

{#if loading && !movie}
	<LoadingState label="Loading movie…" />
{:else if error}
	<ErrorState message={error} onretry={load} />
{:else if !movie}
	<div class="space-y-4">
		<ErrorState title="Movie not found" message={`No movie with id ${movieId}.`} />
		<Button variant="outline" href="/"><ArrowLeftIcon /> Back to movies</Button>
	</div>
{:else}
	<article class="space-y-8">
		<div>
			<Button variant="ghost" size="sm" href="/"><ArrowLeftIcon /> Movies</Button>
		</div>

		<header class="flex flex-wrap items-start justify-between gap-4">
			<div class="space-y-2">
				<h1 class="text-3xl font-bold tracking-tight">{movie.title}</h1>
				<div class="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
					{#if movie.releaseYear}<span>{movie.releaseYear}</span>{/if}
					{#if movie.genre}<Badge variant="secondary">{movie.genre}</Badge>{/if}
				</div>
			</div>
			<div class="flex gap-2">
				<Button variant="outline" onclick={() => (movieDialogOpen = true)}>
					<PencilIcon />
					Edit
				</Button>
				<Button
					variant="destructive"
					onclick={() => {
						const target = movie;
						if (!target) return;
						askConfirm(
							'Delete movie',
							`This permanently deletes “${target.title}” and its artwork.`,
							async () => {
								await mutate(DeleteMovieDocument, { id: target.id });
								toast.success(`Deleted “${target.title}”.`);
								await goto('/');
							}
						);
					}}
				>
					<Trash2Icon />
					Delete
				</Button>
			</div>
		</header>

		{#if movie.synopsis}
			<p class="max-w-prose leading-relaxed">{movie.synopsis}</p>
		{/if}

		<section class="space-y-3">
			<div class="flex items-center justify-between gap-3">
				<h2 class="text-xl font-semibold">Artwork</h2>
				<Button size="sm" variant="outline" onclick={() => openArtworkDialog(null)}>
					<PlusIcon />
					Add artwork
				</Button>
			</div>

			{#if movie.artworks.length === 0}
				<p class="text-sm text-muted-foreground">No artwork yet.</p>
			{:else}
				<ul class="grid grid-cols-2 gap-4 sm:grid-cols-3">
					{#each movie.artworks as artwork (artwork.id)}
						<li class="overflow-hidden rounded-lg ring-1 ring-foreground/10">
							<img
								src={artwork.url}
								alt={`${movie.title} ${titleCase(artwork.type)}`}
								class="aspect-3/2 w-full object-cover"
							/>
							<div class="flex items-center justify-between gap-2 p-2">
								<Badge variant="secondary">{titleCase(artwork.type)}</Badge>
								<div class="flex gap-1">
									<Button variant="ghost" size="icon-xs" onclick={() => openArtworkDialog(artwork)}>
										<RefreshCwIcon />
										<span class="sr-only">Replace {titleCase(artwork.type)}</span>
									</Button>
									<Button
										variant="ghost"
										size="icon-xs"
										onclick={() =>
											askConfirm(
												'Remove artwork',
												'This removes the file and takes it out of the gallery.',
												async () => {
													await mutate(RemoveMovieArtworkDocument, { id: artwork.id });
													toast.success('Artwork removed.');
													await load();
												}
											)}
									>
										<Trash2Icon />
										<span class="sr-only">Remove {titleCase(artwork.type)}</span>
									</Button>
								</div>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<section class="space-y-3">
			<div class="flex items-center justify-between gap-3">
				<h2 class="text-xl font-semibold">Cast</h2>
				<Button
					size="sm"
					variant="outline"
					onclick={() => {
						editingCast = null;
						castOpen = true;
					}}
				>
					<PlusIcon />
					Add cast
				</Button>
			</div>

			{#if movie.cast.length === 0}
				<p class="text-sm text-muted-foreground">No cast listed yet.</p>
			{:else}
				<ul class="divide-y">
					{#each movie.cast as member (member.id)}
						<li class="flex flex-wrap items-center justify-between gap-2 py-2">
							<div>
								<span class="font-medium">{member.person.name}</span>
								<span class="text-muted-foreground"> as {member.characterName}</span>
							</div>
							<div class="flex gap-1">
								<Button variant="ghost" size="icon-xs" onclick={() => editCast(member)}>
									<PencilIcon />
									<span class="sr-only">Edit {member.person.name}</span>
								</Button>
								<Button
									variant="ghost"
									size="icon-xs"
									onclick={() =>
										askConfirm(
											'Remove cast member',
											`This removes ${member.person.name} from the cast.`,
											async () => {
												await mutate(RemoveCastMemberDocument, { id: member.id });
												toast.success(`${member.person.name} removed from cast.`);
												await load();
											}
										)}
								>
									<Trash2Icon />
									<span class="sr-only">Remove {member.person.name}</span>
								</Button>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<section class="space-y-3">
			<div class="flex items-center justify-between gap-3">
				<h2 class="text-xl font-semibold">Creators</h2>
				<Button
					size="sm"
					variant="outline"
					onclick={() => {
						editingCreator = null;
						creatorOpen = true;
					}}
				>
					<PlusIcon />
					Add creator
				</Button>
			</div>

			{#if movie.creators.length === 0}
				<p class="text-sm text-muted-foreground">No creators listed yet.</p>
			{:else}
				<ul class="divide-y">
					{#each movie.creators as creator (creator.id)}
						<li class="flex flex-wrap items-center justify-between gap-2 py-2">
							<div>
								<span class="font-medium">{creator.person.name}</span>
								<span class="text-muted-foreground"> · {creator.job}</span>
							</div>
							<div class="flex gap-1">
								<Button variant="ghost" size="icon-xs" onclick={() => editCreator(creator)}>
									<PencilIcon />
									<span class="sr-only">Edit {creator.person.name}</span>
								</Button>
								<Button
									variant="ghost"
									size="icon-xs"
									onclick={() =>
										askConfirm(
											'Remove creator',
											`This removes ${creator.person.name} from the credits.`,
											async () => {
												await mutate(RemoveCreatorDocument, { id: creator.id });
												toast.success(`${creator.person.name} removed from credits.`);
												await load();
											}
										)}
								>
									<Trash2Icon />
									<span class="sr-only">Remove {creator.person.name}</span>
								</Button>
							</div>
						</li>
					{/each}
				</ul>
			{/if}
		</section>
	</article>

	<MovieDialog
		bind:open={movieDialogOpen}
		{movie}
		onsaved={(updated) => {
			toast.success(`Updated “${updated.title}”.`);
			void load();
		}}
	/>

	<Dialog.Root bind:open={artworkOpen}>
		<Dialog.Content>
			<Dialog.Header>
				<Dialog.Title>{replaceArtwork ? 'Replace artwork' : 'Add artwork'}</Dialog.Title>
				<Dialog.Description>
					{replaceArtwork
						? 'Upload a new file, then the current artwork is removed.'
						: 'Upload an image for this movie.'}
				</Dialog.Description>
			</Dialog.Header>
			{#if artworkOpen}
				{#key artworkKey}
					<ArtworkUpload
						movieId={movie.id}
						replace={replaceArtwork}
						onuploaded={() => {
							artworkOpen = false;
							toast.success(replaceArtwork ? 'Artwork replaced.' : 'Artwork uploaded.');
							void load();
						}}
						onclose={() => (artworkOpen = false)}
					/>
				{/key}
			{/if}
		</Dialog.Content>
	</Dialog.Root>

	<CastCreatorDialog
		bind:open={castOpen}
		mode="cast"
		movieId={movie.id}
		existing={editingCast}
		onsaved={() => {
			toast.success(editingCast ? 'Cast character updated.' : 'Cast member added.');
			void load();
		}}
	/>

	<CastCreatorDialog
		bind:open={creatorOpen}
		mode="creator"
		movieId={movie.id}
		existing={editingCreator}
		onsaved={() => {
			toast.success(editingCreator ? 'Creator job updated.' : 'Creator added.');
			void load();
		}}
	/>

	<ConfirmDialog
		bind:open={confirmOpen}
		title={pendingConfirm?.title ?? ''}
		description={pendingConfirm?.description ?? ''}
		pending={confirmPending}
		onconfirm={runConfirm}
	/>
{/if}
