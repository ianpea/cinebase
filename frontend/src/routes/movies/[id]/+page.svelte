<script lang="ts">
	import { page } from '$app/stores';
	import { queryStore } from '@urql/svelte';
	import { client } from '$lib/graphql/client';
	import { MovieDetailDocument } from '$lib/graphql/queries';
	import type { MovieDetailQuery, MovieDetailQueryVariables } from '$lib/graphql/types';

	export const ssr = false;

	const result = queryStore<MovieDetailQuery, MovieDetailQueryVariables>({
		client,
		query: MovieDetailDocument,
		variables: { id: $page.params.id ?? '' }
	});

	const movie = $derived($result.data?.movie ?? null);
	const fetching = $derived($result.fetching);
	const error = $derived($result.error);
</script>

{#if fetching}
	<p class="text-muted-foreground">Loading movie…</p>
{:else if error}
	<p class="text-destructive">Failed to load movie: {error.message}</p>
{:else if movie}
	<article class="space-y-8">
		<header class="space-y-2">
			<h1 class="text-3xl font-bold tracking-tight">{movie.title}</h1>
			<div class="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
				{#if movie.releaseYear}
					<span>{movie.releaseYear}</span>
				{/if}
				{#if movie.genre}
					<span>· {movie.genre}</span>
				{/if}
			</div>
		</header>

		{#if movie.synopsis}
			<p class="max-w-prose leading-relaxed">{movie.synopsis}</p>
		{/if}

		<section class="space-y-3">
			<h2 class="text-xl font-semibold">Cast</h2>
			{#if movie.cast.length}
				<ul class="divide-y">
					{#each movie.cast as member (member.id)}
						<li class="flex items-baseline justify-between gap-4 py-2">
							<span class="font-medium">{member.person.name}</span>
							<span class="text-muted-foreground">as {member.characterName}</span>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="text-muted-foreground">No cast listed.</p>
			{/if}
		</section>

		<section class="space-y-3">
			<h2 class="text-xl font-semibold">Creators</h2>
			{#if movie.creators.length}
				<ul class="divide-y">
					{#each movie.creators as creator (creator.id)}
						<li class="flex items-baseline justify-between gap-4 py-2">
							<span class="font-medium">{creator.person.name}</span>
							<span class="text-muted-foreground">{creator.job}</span>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="text-muted-foreground">No creators listed.</p>
			{/if}
		</section>
	</article>
{:else}
	<p class="text-muted-foreground">Movie not found.</p>
{/if}
