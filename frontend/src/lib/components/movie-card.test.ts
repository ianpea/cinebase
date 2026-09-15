import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import MovieCard from './movie-card.svelte';
import { makeMovie } from '$lib/testing/fixtures';

function renderCard(movie = makeMovie()) {
	const onedit = vi.fn();
	const ondelete = vi.fn();
	const result = render(MovieCard, { props: { movie, onedit, ondelete } });
	return { ...result, onedit, ondelete, movie };
}

describe('MovieCard', () => {
	it('shows the artwork, title, year and genre, and links to the detail page', () => {
		const { container } = renderCard();
		const artwork = screen.getByAltText('Interstellar artwork');

		expect(artwork.getAttribute('src')).toBe('/uploads/artworks/poster.png');
		expect(container.querySelector('a')?.getAttribute('href')).toBe('/movies/1');
		expect(screen.getByText('Interstellar')).toBeTruthy();
		expect(screen.getByText('2014')).toBeTruthy();
		expect(screen.getByText('Sci-Fi')).toBeTruthy();
	});

	it('falls back to an icon when the movie has no artwork', () => {
		renderCard(makeMovie({ artworkUrl: null }));

		expect(screen.queryByAltText('Interstellar artwork')).toBeNull();
		expect(screen.getByText('Interstellar')).toBeTruthy();
	});

	it('says the year is unknown when it is missing', () => {
		renderCard(makeMovie({ releaseYear: null }));

		expect(screen.getByText('Year unknown')).toBeTruthy();
	});

	it('omits the genre badge when the movie has no genre', () => {
		renderCard(makeMovie({ genre: null }));

		expect(screen.queryByText('Sci-Fi')).toBeNull();
	});

	it('asks the page to edit the movie', async () => {
		const { onedit, ondelete, movie } = renderCard();

		await fireEvent.click(screen.getByRole('button', { name: 'Edit Interstellar' }));

		expect(onedit).toHaveBeenCalledExactlyOnceWith(movie);
		expect(ondelete).not.toHaveBeenCalled();
	});

	it('asks the page to delete the movie', async () => {
		const { onedit, ondelete, movie } = renderCard();

		await fireEvent.click(screen.getByRole('button', { name: 'Delete Interstellar' }));

		expect(ondelete).toHaveBeenCalledExactlyOnceWith(movie);
		expect(onedit).not.toHaveBeenCalled();
	});
});
