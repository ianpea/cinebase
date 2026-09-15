import {render, screen} from '@testing-library/svelte';
import {describe, expect, it, vi} from 'vitest';
import ErrorPage from './+error.svelte';

const {page} = vi.hoisted(() => ({
    page: {status: 500, error: null as {message: string;} | null}
}));

vi.mock('$app/state', () => ({page}));

function renderError(status: number, message: string | null) {
    page.status = status;
    page.error = message === null ? null : {message};
    render(ErrorPage);
}

describe('error page', () => {
    it('replaces the terse "Internal Error" message and offers a way back', () => {
        renderError(500, 'Internal Error');
        expect(screen.getByRole('heading', {name: 'Something went wrong'})).toBeTruthy();
        expect(screen.getByText(/The server ran into a problem/)).toBeTruthy();
        expect(screen.getByRole('link', {name: 'Back to movies'}).getAttribute('href')).toBe('/');
    });

    it('shows a meaningful 5xx message thrown by the app', () => {
        renderError(503, 'The movie service is unavailable.');
        expect(screen.getByRole('heading', {name: 'Something went wrong'})).toBeTruthy();
        expect(screen.getByText('The movie service is unavailable.')).toBeTruthy();
    });

    it('keeps the framework wording for a 404', () => {
        renderError(404, 'Not Found');
        expect(screen.getByRole('heading', {name: 'Page not found'})).toBeTruthy();
        expect(screen.getByText('Not Found')).toBeTruthy();
    });
});
