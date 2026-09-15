import { describe, expect, it } from 'vitest';
import { ALLOWED_IMAGE_TYPES, MAX_ARTWORK_BYTES, validateArtworkFile } from './upload';

function file(type: string, size: number): File {
	const content = new Uint8Array(size);
	return new File([content], 'artwork', { type });
}

describe('validateArtworkFile', () => {
	it('accepts every image type the server allows', () => {
		for (const type of Object.keys(ALLOWED_IMAGE_TYPES)) {
			expect(validateArtworkFile(file(type, 1024))).toBeNull();
		}
	});

	it('accepts a file of exactly the maximum size', () => {
		expect(validateArtworkFile(file('image/png', MAX_ARTWORK_BYTES))).toBeNull();
	});

	it('rejects an unsupported type', () => {
		expect(validateArtworkFile(file('application/pdf', 1024))).toBe(
			"Unsupported artwork type 'application/pdf'. Allowed: PNG, JPEG, WEBP, GIF, AVIF."
		);
	});

	it('rejects a file one byte over the maximum size', () => {
		expect(validateArtworkFile(file('image/png', MAX_ARTWORK_BYTES + 1))).toBe(
			'Artwork file must be at most 10 MB.'
		);
	});

	it('matches the server-side limits', () => {
		expect(MAX_ARTWORK_BYTES).toBe(10 * 1024 * 1024);
	});
});
