<script lang="ts">
	import ClapperboardIcon from '@lucide/svelte/icons/clapperboard';
	import PencilIcon from '@lucide/svelte/icons/pencil';
	import Trash2Icon from '@lucide/svelte/icons/trash-2';
	import { Card, CardContent } from '$lib/components/ui/card/index.js';
	import { Badge } from '$lib/components/ui/badge/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import type { Movie } from '$lib/graphql/types';

	let {
		movie,
		onedit,
		ondelete
	}: { movie: Movie; onedit: (movie: Movie) => void; ondelete: (movie: Movie) => void } = $props();
</script>

<Card size="sm" class="gap-0 overflow-hidden pt-0">
	<a href={`/movies/${movie.id}`} class="block focus-visible:outline-none">
		<span class="block aspect-2/3 w-full overflow-hidden bg-muted">
			{#if movie.artworkUrl}
				<img
					src={movie.artworkUrl}
					alt={`${movie.title} artwork`}
					loading="lazy"
					class="size-full object-cover transition-transform duration-200 hover:scale-105"
				/>
			{:else}
				<span class="flex size-full items-center justify-center text-muted-foreground">
					<ClapperboardIcon class="size-8" />
				</span>
			{/if}
		</span>
	</a>

	<CardContent class="gap-2 p-3">
		<div class="flex items-start justify-between gap-2 pb-2">
			<div class="min-w-0">
				<a href={`/movies/${movie.id}`} class="block truncate font-medium hover:underline">
					{movie.title}
				</a>
				<p class="text-xs text-muted-foreground">
					{movie.releaseYear ?? 'Year unknown'}
				</p>
			</div>
			<div class="flex shrink-0 gap-1">
				<Button variant="ghost" size="icon-xs" onclick={() => onedit(movie)}>
					<PencilIcon />
					<span class="sr-only">Edit {movie.title}</span>
				</Button>
				<Button variant="ghost" size="icon-xs" onclick={() => ondelete(movie)}>
					<Trash2Icon />
					<span class="sr-only">Delete {movie.title}</span>
				</Button>
			</div>
		</div>
		{#if movie.genre}
			<Badge variant="secondary" class="w-fit">{movie.genre}</Badge>
		{/if}
	</CardContent>
</Card>
