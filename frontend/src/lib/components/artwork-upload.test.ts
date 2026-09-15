import {fireEvent, render, screen} from '@testing-library/svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import ArtworkUpload from './artwork-upload.svelte';
import {mutate} from '$lib/graphql/api';
import {uploadMovieArtwork} from '$lib/graphql/upload';
import type {Artwork} from '$lib/graphql/types';
import {chooseFile, imageFile, submitForm} from '$lib/testing/dom';
import {makeArtwork} from '$lib/testing/fixtures';

vi.mock('$lib/graphql/api', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/api')>()),
    mutate: vi.fn(),
    request: vi.fn()
}));

vi.mock('$lib/graphql/upload', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/upload')>()),
    uploadMovieArtwork: vi.fn()
}));

const mockedMutate = vi.mocked(mutate);
const mockedUpload = vi.mocked(uploadMovieArtwork);

beforeEach(() => {
    mockedMutate.mockReset();
    mockedUpload.mockReset();
});

function open(replace: Artwork | null = null) {
    const onuploaded = vi.fn();
    const onclose = vi.fn();
    render(ArtworkUpload, {props: {movieId: '1', replace, onuploaded, onclose}});
    return {onuploaded, onclose};
}

const fileInput = () => screen.getByLabelText(/^(File|New file)$/) as HTMLInputElement;

describe('ArtworkUpload', () => {
    it('rejects an unsupported file type before uploading', async () => {
        open();

        await chooseFile(fileInput(), imageFile('notes.pdf', 'application/pdf'));

        expect(screen.getByRole('alert').textContent).toBe(
            "Unsupported artwork type 'application/pdf'. Allowed: PNG, JPEG, WEBP, GIF, AVIF."
        );
        expect(screen.queryByAltText('Selected artwork preview')).toBeNull();
        expect(screen.getByRole('button', {name: 'Upload artwork'}).hasAttribute('disabled')).toBe(true);
    });

    it('rejects a file over the size limit', async () => {
        open();

        await chooseFile(fileInput(), imageFile('poster.png', 'image/png', 10 * 1024 * 1024 + 1));

        expect(screen.getByRole('alert').textContent).toBe('Artwork file must be at most 10 MB.');
        expect(screen.queryByAltText('Selected artwork preview')).toBeNull();
    });

    it('previews a valid file and enables the upload', async () => {
        open();

        await chooseFile(fileInput(), imageFile('poster.png', 'image/png'));

        expect(screen.queryByRole('alert')).toBeNull();
        expect(screen.getByAltText('Selected artwork preview').getAttribute('src')).toMatch(/^blob:/);
        expect(screen.getByRole('button', {name: 'Upload artwork'}).hasAttribute('disabled')).toBe(false);
    });

    it('uploads the selected file and tells the page to refresh', async () => {
        const stored = makeArtwork();
        mockedUpload.mockResolvedValueOnce(stored);
        const {onuploaded} = open();
        const file = imageFile('poster.png', 'image/png');
        await chooseFile(fileInput(), file);

        await submitForm();

        expect(mockedUpload).toHaveBeenCalledExactlyOnceWith({
            movieId: '1',
            type: 'POSTER',
            file,
            onProgress: expect.any(Function)
        });
        expect(onuploaded).toHaveBeenCalledOnce();
        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('reports upload progress while the request is in flight', async () => {
        mockedUpload.mockImplementationOnce(({onProgress}) => {
            onProgress?.(42);
            return new Promise(() => { }); // never settles: the upload is still running
        });
        open();
        await chooseFile(fileInput(), imageFile('poster.png', 'image/png'));

        await submitForm();

        expect(screen.getByRole('progressbar').getAttribute('aria-valuenow')).toBe('42');
        expect(screen.getByText('Uploading… 42%')).toBeTruthy();
    });

    it('shows the server message when the upload fails', async () => {
        mockedUpload.mockRejectedValueOnce(new Error('Artwork file must be at most 10 MB'));
        const {onuploaded} = open();
        await chooseFile(fileInput(), imageFile('poster.png', 'image/png'));

        await submitForm();

        expect(screen.getByRole('alert').textContent).toBe('Artwork file must be at most 10 MB');
        expect(onuploaded).not.toHaveBeenCalled();
    });

    it('removes the artwork it replaced once the new upload succeeds', async () => {
        const replaced = makeArtwork({id: '10', type: 'BACKDROP'});
        mockedUpload.mockResolvedValueOnce(makeArtwork({id: '11'}));
        mockedMutate.mockResolvedValueOnce({removeMovieArtwork: true} as never);
        open(replaced);

        // Replace mode fixes the type and shows what is being replaced.
        expect(screen.queryByLabelText('Artwork type')).toBeNull();
        expect(screen.getByText('Replacing')).toBeTruthy();

        await chooseFile(fileInput(), imageFile('backdrop.png', 'image/png'));
        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {id: '10'});
    });

    it('closes without uploading when cancelled', async () => {
        const {onclose} = open();

        await fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));

        expect(onclose).toHaveBeenCalledOnce();
        expect(mockedUpload).not.toHaveBeenCalled();
    });
});
