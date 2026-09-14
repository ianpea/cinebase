<script lang="ts">
	import { goto } from '$app/navigation';
	import { Button } from '$lib/components/ui/button';

	let movieId = $state('1');

	function openMovie(event: SubmitEvent) {
		event.preventDefault();
		const id = movieId.trim();
		if (id) goto(`/movies/${encodeURIComponent(id)}`);
	}
</script>

<section class="space-y-4">
	<h1 class="text-3xl font-bold tracking-tight">Cinebase</h1>
	<p class="max-w-prose text-muted-foreground">
		A small movie manager built with SvelteKit, GraphQL and gRPC. Open a movie to see its cast
		and creators, which are resolved by movie-service over gRPC from person-service.
	</p>

	<form onsubmit={openMovie} class="flex items-center gap-2">
		<label for="movie-id" class="text-sm font-medium">Movie ID</label>
		<input
			id="movie-id"
			bind:value={movieId}
			class="rounded-md border border-input bg-background px-3 py-2 text-sm"
			placeholder="e.g. 1"
		/>
		<Button type="submit">View movie</Button>
	</form>
</section>
