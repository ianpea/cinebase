<script lang="ts">
	import UploadIcon from '@lucide/svelte/icons/upload';
	import { untrack } from 'svelte';
	import { Badge } from '$lib/components/ui/badge/index.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import * as Select from '$lib/components/ui/select/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import { errorMessage, mutate } from '$lib/graphql/api';
	import { RemoveMovieArtworkDocument } from '$lib/graphql/queries';
	import type { Artwork, ArtworkType } from '$lib/graphql/types';
	import { uploadMovieArtwork, validateArtworkFile } from '$lib/graphql/upload';
	import { titleCase } from '$lib/format';

	/**
	 * Artwork file selection with client-side validation, an image preview and upload progress.
	 * Replacing an artwork uploads the new file first, then removes the artwork it replaces
	 */
	let {
		movieId,
		replace = null,
		onuploaded,
		onclose
	}: {
		movieId: string;
		replace?: Artwork | null;
		onuploaded: () => void;
		onclose: () => void;
	} = $props();

	let artworkType = $state<ArtworkType>('POSTER');

	// Also passed to `Select.Root` as `items` so the trigger can render a friendly label.
	const ARTWORK_TYPES: { value: ArtworkType; label: string }[] = [
		{ value: 'POSTER', label: 'Poster' },
		{ value: 'BACKDROP', label: 'Backdrop' },
		{ value: 'STILL', label: 'Still' }
	];

	let file = $state<File | null>(null);
	let previewUrl = $state<string | null>(null);
	let error = $state<string | null>(null);
	let progress = $state(0);
	let uploading = $state(false);

	$effect(() => {
		artworkType = replace?.type ?? 'POSTER';
		untrack(() => clearSelection());
	});

	function clearSelection() {
		if (previewUrl) URL.revokeObjectURL(previewUrl);
		previewUrl = null;
		file = null;
		progress = 0;
		error = null;
	}

	function onFileChange(event: Event) {
		const chosen = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
		clearSelection();
		if (!chosen) return;
		const problem = validateArtworkFile(chosen);
		if (problem) {
			error = problem;
			return;
		}
		file = chosen;
		previewUrl = URL.createObjectURL(chosen);
	}

	async function upload(event: SubmitEvent) {
		event.preventDefault();
		if (!file) return;
		uploading = true;
		error = null;
		progress = 0;
		try {
			await uploadMovieArtwork({
				movieId,
				type: artworkType,
				file,
				onProgress: (percent) => (progress = percent)
			});
			if (replace) await mutate(RemoveMovieArtworkDocument, { id: replace.id });
			onuploaded();
		} catch (e) {
			error = errorMessage(e);
		} finally {
			uploading = false;
		}
	}
</script>

<form onsubmit={upload} class="space-y-4" novalidate>
	{#if replace}
		<div class="space-y-2">
			<Label>Replacing</Label>
			<div class="flex items-center gap-2 text-sm">
				<Badge variant="secondary">{titleCase(replace.type)}</Badge>
				<img src={replace.url} alt="" class="h-10 w-10 rounded object-cover" />
			</div>
		</div>
	{:else}
		<div class="space-y-2">
			<Label for="artwork-type">Artwork type</Label>
			<Select.Root
				type="single"
				items={ARTWORK_TYPES}
				value={artworkType}
				onValueChange={(value) => value && (artworkType = value as ArtworkType)}
			>
				<Select.Trigger id="artwork-type" class="w-full">
					<Select.Value placeholder="Select a type" />
				</Select.Trigger>
				<Select.Content>
					{#each ARTWORK_TYPES as option (option.value)}
						<Select.Item value={option.value}>{option.label}</Select.Item>
					{/each}
				</Select.Content>
			</Select.Root>
		</div>
	{/if}

	<div class="space-y-2">
		<Label for="artwork-file">{replace ? 'New file' : 'File'}</Label>
		<Input
			id="artwork-file"
			type="file"
			accept="image/png,image/jpeg,image/webp,image/gif,image/avif"
			disabled={uploading}
			onchange={onFileChange}
		/>
		<p class="text-xs text-muted-foreground">PNG, JPEG, WEBP, GIF or AVIF · up to 10 MB.</p>
	</div>

	{#if previewUrl}
		<div class="space-y-2">
			<Label>Preview</Label>
			<img
				src={previewUrl}
				alt="Selected artwork preview"
				class="max-h-56 w-full rounded-md object-contain ring-1 ring-foreground/10"
			/>
		</div>
	{/if}

	{#if uploading}
		<div class="space-y-1">
			<div
				class="h-2 w-full overflow-hidden rounded-full bg-muted"
				role="progressbar"
				aria-valuenow={progress}
				aria-valuemin="0"
				aria-valuemax="100"
			>
				<div class="h-full bg-primary transition-all" style={`width: ${progress}%`}></div>
			</div>
			<p class="text-xs text-muted-foreground">Uploading… {progress}%</p>
		</div>
	{/if}

	{#if error}
		<p class="text-sm text-destructive" role="alert">{error}</p>
	{/if}

	<div class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
		<Button variant="outline" type="button" disabled={uploading} onclick={onclose}>Cancel</Button>
		<Button type="submit" disabled={uploading || !file}>
			{#if uploading}
				<Spinner />
			{:else}
				<UploadIcon />
			{/if}
			{replace ? 'Replace artwork' : 'Upload artwork'}
		</Button>
	</div>
</form>
