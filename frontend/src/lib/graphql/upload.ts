import type { Artwork, ArtworkType } from './types';

/**
 * Artwork upload.
 *
 * movie-service speaks the GraphQL multipart request spec, which urql's fetch exchange cannot
 * produce. We use `XMLHttpRequest` directly so the UI can also report real upload progress
 * (`xhr.upload.onprogress`), which `fetch` cannot do.
 */

/** Mirrors `ArtworkService.ALLOWED_TYPES` on the server. */
export const ALLOWED_IMAGE_TYPES: Record<string, string> = {
	'image/png': 'PNG',
	'image/jpeg': 'JPEG',
	'image/webp': 'WEBP',
	'image/gif': 'GIF',
	'image/avif': 'AVIF'
};

export const MAX_ARTWORK_BYTES = 10 * 1024 * 1024;

/** Client-side pre-flight check so users get instant feedback before uploading. */
export function validateArtworkFile(file: File): string | null {
	if (!ALLOWED_IMAGE_TYPES[file.type.toLowerCase()]) {
		return `Unsupported artwork type '${file.type || 'unknown'}'. Allowed: PNG, JPEG, WEBP, GIF, AVIF.`;
	}
	if (file.size > MAX_ARTWORK_BYTES) {
		return `Artwork file must be at most ${MAX_ARTWORK_BYTES / (1024 * 1024)} MB.`;
	}
	return null;
}

const UPLOAD_DOCUMENT = `
	mutation UploadMovieArtwork($movieId: ID!, $type: ArtworkType!, $file: Upload!) {
		uploadMovieArtwork(movieId: $movieId, type: $type, file: $file) {
			id
			movieId
			url
			type
		}
	}
`;

export interface UploadArtworkArgs {
	movieId: string;
	type: ArtworkType;
	file: File;
	onProgress?: (percent: number) => void;
}

export function uploadMovieArtwork({
	movieId,
	type,
	file,
	onProgress
}: UploadArtworkArgs): Promise<Artwork> {
	return new Promise((resolve, reject) => {
		const form = new FormData();
		// GraphQL multipart request spec: operations, then the variable map, then the files.
		form.append(
			'operations',
			JSON.stringify({
				query: UPLOAD_DOCUMENT,
				variables: { movieId, type, file: null }
			})
		);
		form.append('map', JSON.stringify({ 0: ['variables.file'] }));
		form.append('0', file);

		const xhr = new XMLHttpRequest();
		xhr.open('POST', '/graphql');

		xhr.upload.onprogress = (event) => {
			if (event.lengthComputable) onProgress?.(Math.round((event.loaded / event.total) * 100));
		};

		xhr.onerror = () => reject(new Error('Upload failed. Check your connection and try again.'));
		xhr.onabort = () => reject(new Error('Upload cancelled.'));

		xhr.onload = () => {
			let payload: { data?: { uploadMovieArtwork?: Artwork }; errors?: { message: string }[] };
			try {
				payload = JSON.parse(xhr.responseText);
			} catch {
				reject(new Error(`Upload failed (HTTP ${xhr.status}).`));
				return;
			}
			if (payload.errors?.length) {
				reject(new Error(payload.errors.map((e) => e.message).join('; ')));
				return;
			}
			const artwork = payload.data?.uploadMovieArtwork;
			if (!artwork) {
				reject(new Error(`Upload failed (HTTP ${xhr.status}).`));
				return;
			}
			onProgress?.(100);
			resolve(artwork);
		};

		xhr.send(form);
	});
}
