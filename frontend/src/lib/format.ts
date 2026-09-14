/** Small display helpers shared by the UI. */

/** Formats an ISO date-time / date string for display, tolerating nulls. */
export function formatDate(value?: string | null): string {
	if (!value) return '—';
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return '—';
	return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

/** Title-cases an enum-ish label, e.g. `BACKDROP` -> `Backdrop`. */
export function titleCase(value: string): string {
	return value
		.toLowerCase()
		.split('_')
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
		.join(' ');
}
