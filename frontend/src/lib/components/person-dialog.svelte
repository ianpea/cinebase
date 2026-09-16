<script lang="ts">
	import CalendarIcon from '@lucide/svelte/icons/calendar';
	import ChevronLeftIcon from '@lucide/svelte/icons/chevron-left';
	import ChevronRightIcon from '@lucide/svelte/icons/chevron-right';
	import { parseDate, type CalendarDate } from '@internationalized/date';
	import { DatePicker } from 'bits-ui';
	import { Button } from '$lib/components/ui/button/index.js';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import { Textarea } from '$lib/components/ui/textarea/index.js';
	import { errorMessage, mutate } from '$lib/graphql/api';
	import { CreatePersonDocument, UpdatePersonDocument } from '$lib/graphql/queries';
	import type { Person, PersonInput } from '$lib/graphql/types';

	/** Reusable dialog for creating and editing a person's details. */
	let {
		open = $bindable(false),
		person = null,
		onsaved
	}: { open?: boolean; person?: Person | null; onsaved: (person: Person) => void } = $props();

	let name = $state('');
	let biography = $state('');
	let birthDate = $state<CalendarDate | undefined>(undefined);
	let saving = $state(false);
	let error = $state<string | null>(null);

	$effect(() => {
		if (!open) return;
		name = person?.name ?? '';
		biography = person?.biography ?? '';
		birthDate = person?.birthDate ? parseDate(person.birthDate) : undefined;
		error = null;
	});

	const validationError = $derived(
		!name.trim()
			? 'Name is required.'
			: name.trim().length > 255
				? 'Name must be at most 255 characters.'
				: null
	);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (validationError) return;
		saving = true;
		error = null;
		const input: PersonInput = {
			name: name.trim(),
			biography: biography.trim() || null,
			birthDate: birthDate?.toString() ?? null
		};
		try {
			const data = person
				? await mutate(UpdatePersonDocument, { id: person.id, input })
				: await mutate(CreatePersonDocument, { input });
			open = false;
			onsaved(person ? data.updatePerson : data.createPerson);
		} catch (e) {
			error = errorMessage(e);
		} finally {
			saving = false;
		}
	}
</script>

<Dialog.Root bind:open>
	<Dialog.Content>
		<Dialog.Header>
			<Dialog.Title>{person ? 'Edit person' : 'Add person'}</Dialog.Title>
			<Dialog.Description>
				{person ? 'Update this person’s details.' : 'Add a person who can be cast or credited.'}
			</Dialog.Description>
		</Dialog.Header>

		<form onsubmit={submit} class="space-y-4" novalidate>
			<div class="space-y-2">
				<Label for="person-name">Name</Label>
				<Input id="person-name" bind:value={name} placeholder="e.g. Matthew McConaughey" />
			</div>
			<div class="space-y-2">
				<DatePicker.Root
					bind:value={birthDate}
					locale="en-GB"
					weekdayFormat="short"
					disableDaysOutsideMonth
				>
					<DatePicker.Label class="block text-sm leading-none font-medium select-none">
						Birth date
					</DatePicker.Label>
					<DatePicker.Input
						class="flex h-9 w-full items-center rounded-md border border-input bg-transparent px-2 text-base shadow-xs transition-[color,box-shadow] focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50 md:text-sm dark:bg-input/30"
					>
						{#snippet children({ segments })}
							{#each segments as { part, value }, i (part + i)}
								<DatePicker.Segment
									{part}
									class={part === 'literal'
										? 'text-muted-foreground'
										: 'px-1 py-1 outline-none aria-[valuetext=Empty]:text-muted-foreground'}
								>
									{value}
								</DatePicker.Segment>
							{/each}
							<DatePicker.Trigger
								aria-label="Pick a date"
								class="ml-auto inline-flex size-7 items-center justify-center rounded-md text-muted-foreground outline-none hover:bg-accent hover:text-accent-foreground"
							>
								<CalendarIcon class="size-4" />
							</DatePicker.Trigger>
						{/snippet}
					</DatePicker.Input>
					<DatePicker.Content
						sideOffset={6}
						class="z-60 rounded-md border bg-popover p-3 text-popover-foreground shadow-md"
					>
						<DatePicker.Calendar>
							{#snippet children({ months, weekdays })}
								<DatePicker.Header class="flex items-center justify-between gap-2 pb-2">
									<DatePicker.PrevButton
										aria-label="Previous month"
										class="inline-flex size-7 items-center justify-center rounded-md outline-none hover:bg-accent"
									>
										<ChevronLeftIcon class="size-4" />
									</DatePicker.PrevButton>
									<DatePicker.Heading class="text-sm font-medium" />
									<DatePicker.NextButton
										aria-label="Next month"
										class="inline-flex size-7 items-center justify-center rounded-md outline-none hover:bg-accent"
									>
										<ChevronRightIcon class="size-4" />
									</DatePicker.NextButton>
								</DatePicker.Header>
								{#each months as month (month.value)}
									<DatePicker.Grid class="w-full border-collapse select-none">
										<DatePicker.GridHead>
											<DatePicker.GridRow class="flex w-full">
												{#each weekdays as day, i (i)}
													<DatePicker.HeadCell
														class="w-8 pb-1 text-center text-xs font-normal text-muted-foreground"
													>
														{day.slice(0, 2)}
													</DatePicker.HeadCell>
												{/each}
											</DatePicker.GridRow>
										</DatePicker.GridHead>
										<DatePicker.GridBody>
											{#each month.weeks as weekDates (weekDates)}
												<DatePicker.GridRow class="flex w-full">
													{#each weekDates as date (date)}
														<DatePicker.Cell
															{date}
															month={month.value}
															class="relative size-8 p-0 text-center"
														>
															<DatePicker.Day
																class="group inline-flex size-8 items-center justify-center rounded-md text-sm outline-none hover:bg-accent data-outside-month:pointer-events-none data-outside-month:text-muted-foreground/50 data-selected:bg-primary data-selected:text-primary-foreground data-disabled:pointer-events-none data-disabled:text-muted-foreground/50"
															>
																<span
																	class="absolute top-1 hidden size-1 rounded-full bg-primary group-data-today:block"
																></span>
																{date.day}
															</DatePicker.Day>
														</DatePicker.Cell>
													{/each}
												</DatePicker.GridRow>
											{/each}
										</DatePicker.GridBody>
									</DatePicker.Grid>
								{/each}
							{/snippet}
						</DatePicker.Calendar>
					</DatePicker.Content>
				</DatePicker.Root>
			</div>
			<div class="space-y-2">
				<Label for="person-biography">Biography</Label>
				<Textarea id="person-biography" bind:value={biography} rows={3} placeholder="Short bio…" />
			</div>

			{#if validationError}
				<p class="text-sm text-destructive" role="alert">{validationError}</p>
			{:else if error}
				<p class="text-sm text-destructive" role="alert">{error}</p>
			{/if}

			<Dialog.Footer>
				<Button variant="outline" type="button" disabled={saving} onclick={() => (open = false)}>
					Cancel
				</Button>
				<Button type="submit" disabled={saving || !!validationError}>
					{#if saving}<Spinner />{/if}
					{person ? 'Save changes' : 'Add person'}
				</Button>
			</Dialog.Footer>
		</form>
	</Dialog.Content>
</Dialog.Root>
