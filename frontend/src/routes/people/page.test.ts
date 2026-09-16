import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { tick } from 'svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PeoplePage from './+page.svelte';
import type { PageData } from './$types';
import { DeletePersonDocument, PeopleDocument } from '$lib/graphql/queries';
import { mutate, request } from '$lib/graphql/api';
import { makePerson } from '$lib/testing/fixtures';
import type { Person, PersonPage } from '$lib/graphql/types';

vi.mock('svelte-sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('$lib/graphql/api', async (importOriginal) => ({
	...(await importOriginal<typeof import('$lib/graphql/api')>()),
	mutate: vi.fn(),
	request: vi.fn()
}));

const mockedRequest = vi.mocked(request);
const mockedMutate = vi.mocked(mutate);

beforeEach(() => {
	mockedRequest.mockReset();
	mockedMutate.mockReset();
});

function peoplePage(items: Person[], total = items.length): PersonPage {
	return { items, total, page: 0, size: 12, totalPages: total === 0 ? 0 : Math.ceil(total / 12) };
}

/** The result the browser client's `request` resolves with. */
function peopleResponse(items: Person[], total = items.length) {
	return { people: peoplePage(items, total) } as never;
}

/** The data `+page.server.ts` hands to the page, so the first render is the server's result. */
function ssrPage(items: Person[], total?: number): PageData {
	return { people: peoplePage(items, total), peopleError: null };
}

function renderPage(data: PageData) {
	// SvelteKit hands every page `params`, `data` and `form`; this route has no params or actions.
	return render(PeoplePage, { props: { params: {}, data, form: null } });
}

/** Enough people to fill more than one page. */
function manyPeople(count: number): Person[] {
	return Array.from({ length: count }, (_, i) =>
		makePerson({ id: String(i), name: `Person ${i}` })
	);
}

const searchBox = () => screen.getByLabelText('Search people by name…');

/** A row is the only place the page prints a birth date, so read the whole row. */
function rowText(name: string): string {
	return screen.getByText(name).closest('li')?.textContent ?? '';
}

describe('People page', () => {
	it('renders the people the server load already fetched, without querying again', async () => {
		renderPage(ssrPage([makePerson({ name: 'Matthew McConaughey' })]));
		await tick();

		expect(screen.getByText('Matthew McConaughey')).toBeTruthy();
		expect(screen.queryByText('Loading people…')).toBeNull();
		// The point of the server load: hydration must not repeat the query it just performed.
		expect(mockedRequest).not.toHaveBeenCalled();
	});

	it('renders a row per person and the pagination summary from the server data', () => {
		renderPage(
			ssrPage([
				makePerson({ id: '1', name: 'Anne Hathaway' }),
				makePerson({ id: '2', name: 'Damien Chazelle' })
			])
		);

		expect(screen.getByText('Anne Hathaway')).toBeTruthy();
		expect(screen.getByText('Damien Chazelle')).toBeTruthy();
		expect(screen.getByText('2 people')).toBeTruthy();
	});

	it('invites the user to add a person when there are none', () => {
		renderPage(ssrPage([]));

		expect(screen.getByText('No people yet')).toBeTruthy();
		// The header action and the empty-state action.
		expect(screen.getAllByRole('button', { name: /Add person/ })).toHaveLength(2);
	});

	it('queries the server from the browser when the user paginates', async () => {
		mockedRequest.mockResolvedValue(peopleResponse([makePerson({ name: 'Keanu Reeves' })], 14));
		renderPage(ssrPage(manyPeople(14)));

		expect(screen.getByText('14 people · page 1 of 2')).toBeTruthy();

		await fireEvent.click(screen.getByRole('button', { name: /Next/ }));

		await waitFor(() => expect(screen.getByText('Keanu Reeves')).toBeTruthy());
		expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(PeopleDocument, {
			search: null,
			page: 1,
			size: 12
		});
	});

	it('queries the server from the browser when the user searches', async () => {
		mockedRequest.mockResolvedValue(peopleResponse([makePerson({ name: 'Keanu Reeves' })]));
		renderPage(ssrPage([makePerson({ name: 'Matthew McConaughey' })]));

		await fireEvent.input(searchBox(), { target: { value: 'keanu' } });

		await waitFor(() => expect(screen.getByText('Keanu Reeves')).toBeTruthy());
		expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(PeopleDocument, {
			search: 'keanu',
			page: 0,
			size: 12
		});
	});

	it('restarts from the first page when the search changes while paged, in one request', async () => {
		mockedRequest
			.mockResolvedValueOnce(peopleResponse([makePerson({ name: 'Page Two Person' })], 14))
			.mockResolvedValueOnce(peopleResponse([makePerson({ name: 'Keanu Reeves' })]));
		renderPage(ssrPage(manyPeople(14)));

		await fireEvent.click(screen.getByRole('button', { name: /Next/ }));
		await waitFor(() => expect(screen.getByText('Page Two Person')).toBeTruthy());

		await fireEvent.input(searchBox(), { target: { value: 'keanu' } });
		await waitFor(() => expect(screen.getByText('Keanu Reeves')).toBeTruthy());

		// Paging, then the search: exactly two requests, and the new search starts at page 0.
		// Regression: watching `search` and `page` in separate effects let the fetch effect run
		// before the page reset, so this sequence sent an extra `page: 1` query for the page the
		// user had just left.
		expect(mockedRequest).toHaveBeenCalledTimes(2);
		expect(mockedRequest).toHaveBeenLastCalledWith(PeopleDocument, {
			search: 'keanu',
			page: 0,
			size: 12
		});
	});

	it('shows the server load failure and retries it from the browser', async () => {
		mockedRequest.mockResolvedValue(peopleResponse([makePerson({ name: 'Keanu Reeves' })]));
		renderPage({ people: null, peopleError: 'movie-service is unreachable' });
		await tick();

		expect(screen.getByText('movie-service is unreachable')).toBeTruthy();
		// A failed server load must not be retried automatically on hydration either.
		expect(mockedRequest).not.toHaveBeenCalled();

		await fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

		await waitFor(() => expect(screen.getByText('Keanu Reeves')).toBeTruthy());
		expect(mockedRequest).toHaveBeenCalledOnce();
	});

	it('keeps the loading state for a browser-side query', async () => {
		let resolveRequest: (value: unknown) => void = () => {};
		mockedRequest.mockReturnValueOnce(
			new Promise((resolve) => (resolveRequest = resolve)) as never
		);

		renderPage({ people: null, peopleError: 'movie-service is unreachable' });
		await fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

		expect(screen.getByText('Loading people…')).toBeTruthy();

		resolveRequest(peopleResponse([makePerson()]));

		await waitFor(() => expect(screen.getByText('Matthew McConaughey')).toBeTruthy());
		expect(screen.queryByText('Loading people…')).toBeNull();
	});

	it('deletes the person the user confirms, then refreshes the list', async () => {
		mockedMutate.mockResolvedValueOnce({ deletePerson: true } as never);
		mockedRequest.mockResolvedValue(peopleResponse([]));
		renderPage(ssrPage([makePerson({ id: '4', name: 'Kevin Costner' })]));

		await fireEvent.click(screen.getByRole('button', { name: 'Delete Kevin Costner' }));
		await fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

		await waitFor(() =>
			expect(mockedMutate).toHaveBeenCalledExactlyOnceWith(DeletePersonDocument, { id: '4' })
		);
		// The list is refetched rather than patched locally, so it stays consistent with the server.
		await waitFor(() => expect(mockedRequest).toHaveBeenCalledOnce());
	});

	it('leaves the list alone when the delete fails', async () => {
		mockedMutate.mockRejectedValueOnce(new Error('person-service is unreachable'));
		renderPage(ssrPage([makePerson({ id: '4', name: 'Kevin Costner' })]));

		await fireEvent.click(screen.getByRole('button', { name: 'Delete Kevin Costner' }));
		await fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

		await waitFor(() => expect(mockedMutate).toHaveBeenCalledOnce());
		// No refetch: nothing changed on the server, so re-querying would only churn.
		expect(mockedRequest).not.toHaveBeenCalled();
		expect(screen.getByText('Kevin Costner')).toBeTruthy();
	});
});

describe('People page birth date column', () => {
	it('labels the birth date of a person who has one as dd/mm/yyyy', () => {
		renderPage(ssrPage([makePerson({ name: 'Anne Hathaway' })]));

		expect(rowText('Anne Hathaway')).toContain('Born 04/11/1969');
	});

	it('prints `Born —` when the person has no birth date', () => {
		renderPage(ssrPage([makePerson({ name: 'Brad Pitt', birthDate: null })]));

		expect(rowText('Brad Pitt')).toContain('Born —');
	});
});
