import {fireEvent, render, screen, waitFor} from '@testing-library/svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import CastCreatorDialog from './cast-creator-dialog.svelte';
import {mutate, request} from '$lib/graphql/api';
import {submitForm} from '$lib/testing/dom';
import {makePerson} from '$lib/testing/fixtures';

vi.mock('$lib/graphql/api', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/api')>()),
    mutate: vi.fn(),
    request: vi.fn()
}));

const mockedMutate = vi.mocked(mutate);
const mockedRequest = vi.mocked(request);

beforeEach(() => {
    mockedMutate.mockReset();
    mockedRequest.mockReset();
});

const personPage = (...people: ReturnType<typeof makePerson>[]) =>
    ({
        people: {
            items: people,
            total: people.length,
            page: 0,
            size: 10,
            totalPages: 1
        }
    }) as never;

function open(props: {
    mode?: 'cast' | 'creator';
    existing?: {id: string; personId: string; personName: string; detail: string;} | null;
} = {}) {
    const onsaved = vi.fn();
    render(CastCreatorDialog, {
        props: {
            open: true,
            mode: props.mode ?? 'cast',
            movieId: '1',
            existing: props.existing ?? null,
            onsaved
        }
    });
    return {onsaved};
}

/** The dialog searches people on a 300 ms debounce. */
const findPerson = (name: string) => waitFor(() => screen.getByText(name));

describe('CastCreatorDialog', () => {
    beforeEach(() => mockedRequest.mockResolvedValue(personPage(makePerson())));

    it('only searches after the debounce and lists the matches', async () => {
        open();
        expect(mockedRequest).not.toHaveBeenCalled();

        await findPerson('Matthew McConaughey');

        expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            search: null,
            page: 0,
            size: 10
        });
    });

    it('asks for a person before it will save', async () => {
        open();
        await findPerson('Matthew McConaughey');

        expect(screen.getByRole('button', {name: 'Add cast member'}).hasAttribute('disabled')).toBe(true);
    });

    it('requires a character name once a person is selected', async () => {
        open();
        await fireEvent.click(await screen.findByRole('button', {name: 'Matthew McConaughey'}));

        expect(screen.getByRole('alert').textContent).toBe('Character name is required.');
        expect(screen.getByRole('button', {name: 'Add cast member'}).hasAttribute('disabled')).toBe(true);

        await fireEvent.input(screen.getByLabelText('Character name'), {target: {value: 'Cooper'}});

        expect(screen.queryByRole('alert')).toBeNull();
        expect(screen.getByRole('button', {name: 'Add cast member'}).hasAttribute('disabled')).toBe(false);
    });

    it('adds a cast member for the selected person', async () => {
        mockedMutate.mockResolvedValueOnce({} as never);
        const {onsaved} = open();
        await fireEvent.click(await screen.findByRole('button', {name: 'Matthew McConaughey'}));
        await fireEvent.input(screen.getByLabelText('Character name'), {target: {value: '  Cooper  '}});

        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            input: {movieId: '1', personId: '7', characterName: 'Cooper'}
        });
        expect(onsaved).toHaveBeenCalledOnce();
    });

    it('adds a creator with a job instead of a character name', async () => {
        mockedMutate.mockResolvedValueOnce({} as never);
        open({mode: 'creator'});
        await fireEvent.click(await screen.findByRole('button', {name: 'Matthew McConaughey'}));
        await fireEvent.input(screen.getByLabelText('Job'), {target: {value: 'Director'}});

        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            input: {movieId: '1', personId: '7', job: 'Director'}
        });
    });

    it('shows an empty result message when no person matches', async () => {
        mockedRequest.mockResolvedValue(personPage());
        open();

        await waitFor(() => expect(screen.getByText(/No people found/)).toBeTruthy());
    });

    it('shows the search error when person-service cannot be reached', async () => {
        mockedRequest.mockRejectedValueOnce(new Error('person-service is unreachable'));
        open();

        await waitFor(() => expect(screen.getByText('person-service is unreachable')).toBeTruthy());
    });

    describe('editing an existing relationship', () => {
        it('keeps the person fixed and updates the character name', async () => {
            mockedMutate.mockResolvedValueOnce({} as never);
            const {onsaved} = open({
                existing: {id: '1', personId: '7', personName: 'Matthew McConaughey', detail: 'Cooper'}
            });

            // Editing never searches: the person is already known.
            expect(screen.queryByLabelText('Person')).toBeNull();
            expect(screen.getByText('Matthew McConaughey')).toBeTruthy();
            expect((screen.getByLabelText('Character name') as HTMLInputElement).value).toBe('Cooper');
            expect(mockedRequest).not.toHaveBeenCalled();

            await fireEvent.input(screen.getByLabelText('Character name'), {target: {value: 'Joseph Cooper'}});
            await submitForm();

            expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
                id: '1',
                input: {movieId: '1', personId: '7', characterName: 'Joseph Cooper'}
            });
            expect(onsaved).toHaveBeenCalledOnce();
        });

        it('updates the job of an existing creator', async () => {
            mockedMutate.mockResolvedValueOnce({} as never);
            open({
                mode: 'creator',
                existing: {id: '2', personId: '8', personName: 'Christopher Nolan', detail: 'Director'}
            });

            await fireEvent.input(screen.getByLabelText('Job'), {target: {value: 'Producer'}});
            await submitForm();

            expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
                id: '2',
                input: {movieId: '1', personId: '8', job: 'Producer'}
            });
        });
    });
});
