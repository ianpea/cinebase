import {fireEvent, render, screen} from '@testing-library/svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import MovieDialog from './movie-dialog.svelte';
import {mutate} from '$lib/graphql/api';
import {makeMovie} from '$lib/testing/fixtures';

vi.mock('$lib/graphql/api', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/api')>()),
    mutate: vi.fn(),
    request: vi.fn()
}));

const mockedMutate = vi.mocked(mutate);

beforeEach(() => mockedMutate.mockReset());

function open(props: {movie?: ReturnType<typeof makeMovie> | null; onsaved?: () => void;} = {}) {
    const onsaved = props.onsaved ?? vi.fn();
    const result = render(MovieDialog, {props: {open: true, movie: props.movie ?? null, onsaved}});
    return {...result, onsaved};
}

function submitForm() {
    // bits-ui portals the dialog content to document.body, so it is not inside the render container.
    const form = document.querySelector('form');
    if(!form) throw new Error('form not rendered');
    return fireEvent.submit(form);
}

describe('MovieDialog', () => {
    it('requires a title before it will save', async () => {
        open();

        expect(screen.getByRole('alert').textContent).toBe('Title is required.');
        expect(screen.getByRole('button', {name: 'Add movie'}).hasAttribute('disabled')).toBe(true);

        await fireEvent.input(screen.getByLabelText('Title'), {target: {value: 'Interstellar'}});

        expect(screen.queryByRole('alert')).toBeNull();
        expect(screen.getByRole('button', {name: 'Add movie'}).hasAttribute('disabled')).toBe(false);
    });

    it('rejects a release year outside the supported range', async () => {
        open();

        await fireEvent.input(screen.getByLabelText('Title'), {target: {value: 'Interstellar'}});
        await fireEvent.input(screen.getByLabelText('Release year'), {target: {value: '1850'}});

        expect(screen.getByRole('alert').textContent).toBe('Release year must be between 1888 and 2100.');
    });

    it('creates a movie and hands the saved record back to the page', async () => {
        const created = makeMovie({id: '9', title: 'Arrival', releaseYear: 2016});
        mockedMutate.mockResolvedValueOnce({createMovie: created} as never);
        const {onsaved} = open();

        await fireEvent.input(screen.getByLabelText('Title'), {target: {value: '  Arrival  '}});
        await fireEvent.input(screen.getByLabelText('Release year'), {target: {value: '2016'}});
        await fireEvent.input(screen.getByLabelText('Genre'), {target: {value: 'Sci-Fi'}});
        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            input: {title: 'Arrival', synopsis: null, releaseYear: 2016, genre: 'Sci-Fi'}
        });
        expect(onsaved).toHaveBeenCalledExactlyOnceWith(created);
    });

    it('updates the movie when one is being edited', async () => {
        const existing = makeMovie({id: '4', title: 'Arrival'});
        mockedMutate.mockResolvedValueOnce({updateMovie: existing} as never);
        open({movie: existing});

        expect((screen.getByLabelText('Title') as HTMLInputElement).value).toBe('Arrival');
        expect(screen.getByRole('button', {name: 'Save changes'})).toBeTruthy();

        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            id: '4',
            input: {title: 'Arrival', synopsis: 'A trip through a wormhole.', releaseYear: 2014, genre: 'Sci-Fi'}
        });
    });

    it('shows the server error when saving fails', async () => {
        mockedMutate.mockRejectedValueOnce(new Error('title must be at most 255 characters'));
        const {onsaved} = open();

        await fireEvent.input(screen.getByLabelText('Title'), {target: {value: 'Arrival'}});
        await submitForm();

        expect(screen.getByRole('alert').textContent).toBe('title must be at most 255 characters');
        expect(onsaved).not.toHaveBeenCalled();
    });
});
