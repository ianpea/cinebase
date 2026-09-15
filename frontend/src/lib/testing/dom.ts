import { fireEvent } from '@testing-library/svelte';

/** Small DOM helpers shared by the component tests. Only test files import this module. */

/**
 * Submits the portaled dialog form.
 *
 * bits-ui renders dialog content into `document.body`, so it is outside the `render()` container.
 * Dispatching `submit` directly is more reliable than clicking the button, because jsdom does not
 * implement form submission navigation.
 */
export function submitForm() {
	const form = document.querySelector('form');
	if (!form) throw new Error('form not rendered');
	return fireEvent.submit(form);
}

/**
 * Puts a file into an `<input type="file">`, which jsdom will not do on its own.
 *
 * The property has to stay writable: Svelte's `bind:files` (used by the shadcn Input) writes the
 * bound value back onto `input.files`.
 */
export async function chooseFile(input: HTMLInputElement, file: File) {
	Object.defineProperty(input, 'files', { value: [file], configurable: true, writable: true });
	await fireEvent.change(input);
}

export function imageFile(name: string, type: string, size = 1024): File {
	return new File([new Uint8Array(size)], name, { type });
}
