import {describe, expect, it} from 'vitest';
import {formatDate, titleCase} from './format';

describe('formatDate', () => {
    it('formats an ISO date-time for display', () => {
        expect(formatDate('2024-03-05T10:00:00Z')).toBe(
            new Date('2024-03-05T10:00:00Z').toLocaleDateString(undefined, {
                year: 'numeric',
                month: 'short',
                day: 'numeric'
            })
        );
    });

    it('renders a dash when the value is missing', () => {
        expect(formatDate(null)).toBe('—');
        expect(formatDate(undefined)).toBe('—');
        expect(formatDate('')).toBe('—');
    });

    it('renders a dash when the value cannot be parsed', () => {
        expect(formatDate('not-a-date')).toBe('—');
    });
});

describe('titleCase', () => {
    it('turns an enum-ish label into words', () => {
        expect(titleCase('BACKDROP')).toBe('Backdrop');
        expect(titleCase('RELEASE_YEAR')).toBe('Release Year');
    });

    it('leaves an already title-cased label alone', () => {
        expect(titleCase('Poster')).toBe('Poster');
    });
});
