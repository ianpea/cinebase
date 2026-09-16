/** Small display helpers shared by the UI. */

/** Matches a plain `yyyy-mm-dd` value, which `new Date` would otherwise read as UTC midnight. */
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Parses an ISO date / date-time string.
 *
 * `birthDate` arrives as a date-only string, and reading that as UTC midnight shifts it a day
 * backwards for every timezone behind UTC. Treating it as local midnight keeps the printed day.
 */
function parseDate(value: string): Date | null {
	const parts = DATE_ONLY.exec(value);
	if (parts) return new Date(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]));
	const date = new Date(value);
	return Number.isNaN(date.getTime()) ? null : date;
}

/** Renders a `Date` as `dd/mm/yyyy` with zero-padded day and month. */
function toDayFirst(date: Date): string {
	const day = String(date.getDate()).padStart(2, '0');
	const month = String(date.getMonth() + 1).padStart(2, '0');
	return `${day}/${month}/${date.getFullYear()}`;
}

/**
 * Formats an ISO date-time / date string as `dd/mm/yyyy`, tolerating nulls.
 */
export function formatDate(value?: string | null): string {
	if (!value) return '—';
	const date = parseDate(value);
	return date ? toDayFirst(date) : '—';
}

/** Title-cases an enum-ish label, e.g. `BACKDROP` -> `Backdrop`. */
export function titleCase(value: string): string {
	return value
		.toLowerCase()
		.split('_')
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
		.join(' ');
}
