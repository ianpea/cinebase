import type { AnyVariables, CombinedError, TypedDocumentNode } from '@urql/core';
import { client } from './client';

/**
 * Thin wrappers around the urql client that turn `CombinedError`s into thrown
 * `Error`s, so pages can use plain `try/catch` and show one message per failure.
 *
 * Both result and variables types are read off the `TypedDocumentNode` itself, so every call
 * is fully typed without repeating generics at the call site.
 */

/** The `Data` type a document was declared with. */
export type QueryResult<Doc> = Doc extends TypedDocumentNode<infer Result, never> ? Result : never;

/** The variables type a document was declared with. */
export type QueryVariables<Doc> = Doc extends TypedDocumentNode<never, infer Vars> ? Vars : never;

function unwrap(result: { error?: CombinedError; data?: unknown }): unknown {
	if (result.error) {
		// Surface the server's GraphQL messages (e.g. validation) rather than the generic wrapper.
		const messages = result.error.graphQLErrors.map((e) => e.message);
		throw new Error(messages.length ? messages.join('; ') : result.error.message);
	}
	if (result.data == null) throw new Error('The server returned no data.');
	return result.data;
}

/** Runs a GraphQL query and resolves with its data. */
export async function request<Doc extends TypedDocumentNode<any, any>>(
	query: Doc,
	variables: QueryVariables<Doc>
): Promise<QueryResult<Doc>> {
	const result = await client.query(query, variables as AnyVariables).toPromise();
	return unwrap(result) as QueryResult<Doc>;
}

/** Runs a GraphQL mutation and resolves with its data. */
export async function mutate<Doc extends TypedDocumentNode<any, any>>(
	query: Doc,
	variables: QueryVariables<Doc>
): Promise<QueryResult<Doc>> {
	const result = await client.mutation(query, variables as AnyVariables).toPromise();
	return unwrap(result) as QueryResult<Doc>;
}

/** Normalises anything thrown (Error, CombinedError, string) into a display message. */
export function errorMessage(error: unknown): string {
	if (error instanceof Error && error.message) return error.message;
	const combined = error as CombinedError | null;
	if (combined?.graphQLErrors?.length) return combined.graphQLErrors.map((e) => e.message).join('; ');
	if (typeof error === 'string') return error;
	return 'Something went wrong. Please try again.';
}
