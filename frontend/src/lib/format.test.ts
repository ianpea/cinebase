import { describe, expect, it } from 'vitest';
import { formatDate, titleCase } from './format';

describe('formatDate', () => {
	it('formats a date-only value as dd/mm/yyyy with padded parts', () => {
		expect(formatDate('1969-11-04')).toBe('04/11/1969');
		expect(formatDate('1995-01-07')).toBe('07/01/1995');
	});

	it('keeps the stored day for a date-only value', () => {
		// `new Date('1969-11-04')` is UTC midnight, which prints as the 3rd west of UTC.
		expect(formatDate('1969-11-04')).toBe('04/11/1969');
	});

	it('formats a date-time value as dd/mm/yyyy', () => {
		expect(formatDate('2024-03-05T10:00:00Z')).toMatch(/^\d{2}\/\d{2}\/\d{4}$/);
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
