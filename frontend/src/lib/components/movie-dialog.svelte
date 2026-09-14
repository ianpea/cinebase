<script lang="ts">
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import { Textarea } from '$lib/components/ui/textarea/index.js';
	import { errorMessage, mutate } from '$lib/graphql/api';
	import { CreateMovieDocument, UpdateMovieDocument } from '$lib/graphql/queries';
	import type { Movie, MovieInput } from '$lib/graphql/types';

	/** Reusable dialog for creating and editing movie metadata. */
	let {
		open = $bindable(false),
		movie = null,
		onsaved
	}: { open?: boolean; movie?: Movie | null; onsaved: (movie: Movie) => void } = $props();

	let title = $state('');
	let synopsis = $state('');
	let releaseYear = $state('');
	let genre = $state('');
	let saving = $state(false);
	let error = $state<string | null>(null);

	// Reload the form whenever the dialog opens, so it reflects the movie being edited.
	$effect(() => {
		if (!open) return;
		title = movie?.title ?? '';
		synopsis = movie?.synopsis ?? '';
		releaseYear = movie?.releaseYear ? String(movie.releaseYear) : '';
		genre = movie?.genre ?? '';
		error = null;
	});

	/** Client-side validation mirroring the server's `MovieInput` constraints. */
	const validationError = $derived.by(() => {
		if (!title.trim()) return 'Title is required.';
		if (title.trim().length > 255) return 'Title must be at most 255 characters.';
		if (releaseYear.trim()) {
			const year = Number.parseInt(releaseYear.trim(), 10);
			if (Number.isNaN(year)) return 'Release year must be a number.';
			if (year < 1888 || year > 2100) return 'Release year must be between 1888 and 2100.';
		}
		return null;
	});

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (validationError) return;
		saving = true;
		error = null;
		const input: MovieInput = {
			title: title.trim(),
			synopsis: synopsis.trim() || null,
			releaseYear: releaseYear.trim() ? Number.parseInt(releaseYear.trim(), 10) : null,
			genre: genre.trim() || null
		};
		try {
			const data = movie
				? await mutate(UpdateMovieDocument, { id: movie.id, input })
				: await mutate(CreateMovieDocument, { input });
			open = false;
			onsaved(movie ? data.updateMovie : data.createMovie);
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
			<Dialog.Title>{movie ? 'Edit movie' : 'Add movie'}</Dialog.Title>
			<Dialog.Description>
				{movie ? 'Update the movie metadata.' : 'Add a movie to your library.'}
			</Dialog.Description>
		</Dialog.Header>

		<form onsubmit={submit} class="space-y-4" novalidate>
			<div class="space-y-2">
				<Label for="movie-title">Title</Label>
				<Input id="movie-title" bind:value={title} placeholder="e.g. Interstellar" />
			</div>
			<div class="grid grid-cols-2 gap-4">
				<div class="space-y-2">
					<Label for="movie-year">Release year</Label>
					<Input id="movie-year" bind:value={releaseYear} inputmode="numeric" placeholder="2014" />
				</div>
				<div class="space-y-2">
					<Label for="movie-genre">Genre</Label>
					<Input id="movie-genre" bind:value={genre} placeholder="Sci-Fi" />
				</div>
			</div>
			<div class="space-y-2">
				<Label for="movie-synopsis">Synopsis</Label>
				<Textarea id="movie-synopsis" bind:value={synopsis} rows={3} placeholder="Short summary…" />
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
					{movie ? 'Save changes' : 'Add movie'}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
