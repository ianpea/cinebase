import {fireEvent, render, screen} from '@testing-library/svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import PersonDialog from './person-dialog.svelte';
import {mutate} from '$lib/graphql/api';
import type {Person} from '$lib/graphql/types';
import {submitForm} from '$lib/testing/dom';
import {makePerson} from '$lib/testing/fixtures';

vi.mock('$lib/graphql/api', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/api')>()),
    mutate: vi.fn(),
    request: vi.fn()
}));

const mockedMutate = vi.mocked(mutate);

beforeEach(() => mockedMutate.mockReset());

function open(person: Person | null = null) {
    const onsaved = vi.fn();
    render(PersonDialog, {props: {open: true, person, onsaved}});
    return {onsaved};
}

function classTokens(element: Element): string[] {
    return element.className.split(/\s+/);
}

describe('PersonDialog', () => {
    it('requires a name before it will save', async () => {
        open();

        expect(screen.getByRole('alert').textContent).toBe('Name is required.');
        expect(screen.getByRole('button', {name: 'Add person'}).hasAttribute('disabled')).toBe(true);

        await fireEvent.input(screen.getByLabelText('Name'), {target: {value: 'Denis Villeneuve'}});

        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('creates a person and omits empty optional fields', async () => {
        const created = makePerson({id: '3', name: 'Denis Villeneuve'});
        mockedMutate.mockResolvedValueOnce({createPerson: created} as never);
        const {onsaved} = open();

        await fireEvent.input(screen.getByLabelText('Name'), {target: {value: '  Denis Villeneuve  '}});
        await fireEvent.input(screen.getByLabelText('Biography'), {target: {value: '  '}});
        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            input: {name: 'Denis Villeneuve', biography: null, birthDate: null}
        });
        expect(onsaved).toHaveBeenCalledExactlyOnceWith(created);
    });

    it('updates the person being edited', async () => {
        const existing = makePerson({id: '4', name: 'Denis Villeneuve', birthDate: '1967-10-03'});
        mockedMutate.mockResolvedValueOnce({updatePerson: existing} as never);
        const {onsaved} = open(existing);

        expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Denis Villeneuve');
        expect((screen.getByLabelText('Birth date') as HTMLInputElement).value).toBe('1967-10-03');

        await submitForm();

        expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            id: '4',
            input: {name: 'Denis Villeneuve', biography: 'American actor', birthDate: '1967-10-03'}
        });
        expect(onsaved).toHaveBeenCalledExactlyOnceWith(existing);
    });

    it('tells the user that an empty birth date is a valid answer', () => {
        // The native date input draws `mm/dd/yyyy` in the normal text colour, so an untouched
        // field looks like it already holds a date. The hint is what disambiguates it.
        open();

        const hint = screen.getByText('Optional. Leave empty if the birth date is unknown.');
        expect(screen.getByLabelText('Birth date').getAttribute('aria-describedby')).toBe(hint.id);
        expect((screen.getByLabelText('Birth date') as HTMLInputElement).value).toBe('');
    });

    it('mutes the date field while it is empty so the placeholder is not read as a value', async () => {
        // The muted colour cascades into the date field's internal parts, which Chromium otherwise
        // paints in the normal foreground colour.
        open();
        const empty = screen.getByLabelText('Birth date');
        expect(classTokens(empty)).toContain('text-muted-foreground');

        await fireEvent.input(empty, {target: {value: '1967-10-03'}});
        expect(classTokens(screen.getByLabelText('Birth date'))).not.toContain('text-muted-foreground');
    });

    it('does not mute the date field when the person already has one', () => {
        open(makePerson({id: '4', birthDate: '1967-10-03'}));

        expect(classTokens(screen.getByLabelText('Birth date'))).not.toContain('text-muted-foreground');
    });

    it('shows the server error when saving fails', async () => {
        mockedMutate.mockRejectedValueOnce(new Error('name must not be blank'));
        const {onsaved} = open();

        await fireEvent.input(screen.getByLabelText('Name'), {target: {value: 'Denis'}});
        await submitForm();

        expect(screen.getByRole('alert').textContent).toBe('name must not be blank');
        expect(onsaved).not.toHaveBeenCalled();
    });
});
