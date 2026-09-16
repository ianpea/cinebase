import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PersonDialog from './person-dialog.svelte';
import { mutate } from '$lib/graphql/api';
import type { Person } from '$lib/graphql/types';
import { dateSegmentText, submitForm, typeDate } from '$lib/testing/dom';
import { makePerson } from '$lib/testing/fixtures';

vi.mock('$lib/graphql/api', async (importOriginal) => ({
	...(await importOriginal<typeof import('$lib/graphql/api')>()),
	mutate: vi.fn(),
	request: vi.fn()
}));

const mockedMutate = vi.mocked(mutate);

beforeEach(() => mockedMutate.mockReset());

function open(person: Person | null = null) {
	const onsaved = vi.fn();
	render(PersonDialog, { props: { open: true, person, onsaved } });
	return { onsaved };
}

describe('PersonDialog', () => {
	it('requires a name before it will save', async () => {
		open();

		expect(screen.getByRole('alert').textContent).toBe('Name is required.');
		expect(screen.getByRole('button', { name: 'Add person' }).hasAttribute('disabled')).toBe(true);

		await fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'Denis Villeneuve' } });

		expect(screen.queryByRole('alert')).toBeNull();
	});

	it('creates a person and omits empty optional fields', async () => {
		const created = makePerson({ id: '3', name: 'Denis Villeneuve' });
		mockedMutate.mockResolvedValueOnce({ createPerson: created } as never);
		const { onsaved } = open();

		await fireEvent.input(screen.getByLabelText('Name'), {
			target: { value: '  Denis Villeneuve  ' }
		});
		await fireEvent.input(screen.getByLabelText('Biography'), { target: { value: '  ' } });
		await submitForm();

		expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
			input: { name: 'Denis Villeneuve', biography: null, birthDate: null }
		});
		expect(onsaved).toHaveBeenCalledExactlyOnceWith(created);
	});

	it('updates the person being edited', async () => {
		const existing = makePerson({ id: '4', name: 'Denis Villeneuve', birthDate: '1967-10-03' });
		mockedMutate.mockResolvedValueOnce({ updatePerson: existing } as never);
		const { onsaved } = open(existing);

		expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Denis Villeneuve');
		expect([dateSegmentText('day'), dateSegmentText('month'), dateSegmentText('year')]).toEqual([
			'03',
			'10',
			'1967'
		]);

		await submitForm();

		expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
			id: '4',
			input: { name: 'Denis Villeneuve', biography: 'American actor', birthDate: '1967-10-03' }
		});
		expect(onsaved).toHaveBeenCalledExactlyOnceWith(existing);
	});

	it('shows an empty dd/mm/yyyy field when the person has no birth date', () => {
		open();

		const field = document.querySelector('[data-date-field-input]') as HTMLElement;

		expect(field.textContent?.replace(/\s+/g, '')).toBe('dd/mm/yyyy');
	});

	it('offers a collapsed calendar to pick the date instead of typing it', () => {
		open();

		const trigger = screen.getByRole('button', { name: 'Pick a date' });

		expect(trigger.getAttribute('aria-expanded')).toBe('false');
	});

	it('fills the birth date from a day picked in the calendar', async () => {
		const created = makePerson({ id: '3', name: 'Denis Villeneuve' });
		mockedMutate.mockResolvedValueOnce({ createPerson: created } as never);
		open();

		// The calendar opens on the current month, so today is always on screen to pick.
		const today = new Date();
		const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

		await fireEvent.click(screen.getByRole('button', { name: 'Pick a date' }));
		await waitFor(() => {
			expect(document.querySelector(`[data-calendar-day][data-value="${iso}"]`)).not.toBeNull();
		});
		await fireEvent.click(document.querySelector(`[data-calendar-day][data-value="${iso}"]`)!);

		await fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'Denis Villeneuve' } });
		await submitForm();

		expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
			input: { name: 'Denis Villeneuve', biography: null, birthDate: iso }
		});
	});

	it('sends a typed birth date as the ISO date the API stores', async () => {
		const created = makePerson({ id: '3', name: 'Denis Villeneuve' });
		mockedMutate.mockResolvedValueOnce({ createPerson: created } as never);
		open();

		await fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'Denis Villeneuve' } });
		await typeDate({ day: '03', month: '10', year: '1967' });

		expect(screen.queryByRole('alert')).toBeNull();
		await submitForm();

		expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
			input: { name: 'Denis Villeneuve', biography: null, birthDate: '1967-10-03' }
		});
	});

	it('treats a half-typed birth date as no date at all', async () => {
		const created = makePerson({ id: '3', name: 'Denis Villeneuve' });
		mockedMutate.mockResolvedValueOnce({ createPerson: created } as never);
		open();

		await fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'Denis Villeneuve' } });
		await typeDate({ day: '03', month: '10', year: '' });
		await submitForm();

		expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
			input: { name: 'Denis Villeneuve', biography: null, birthDate: null }
		});
	});

	it('shows the server error when saving fails', async () => {
		mockedMutate.mockRejectedValueOnce(new Error('name must not be blank'));
		const { onsaved } = open();

		await fireEvent.input(screen.getByLabelText('Name'), { target: { value: 'Denis' } });
		await submitForm();

		expect(screen.getByRole('alert').textContent).toBe('name must not be blank');
		expect(onsaved).not.toHaveBeenCalled();
	});
});
