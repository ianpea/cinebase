import type {AnyVariables, TypedDocumentNode} from '@urql/core';
import {client} from './client';
import {unwrap, type QueryResult, type QueryVariables} from './result';

/**
 * Thin wrappers around the browser urql client that turn `CombinedError`s into thrown
 * `Error`s, so pages can use plain `try/catch` and show one message per failure.
 *
 * Both result and variables types are read off the `TypedDocumentNode` itself, so every call
 * is fully typed without repeating generics at the call site.
 *
 * This module is browser-only: it resolves `/graphql` against the page, so it cannot serve an SSR
 * load. Server loads use `$lib/server/graphql.ts`, which sends the same documents over an
 * absolute URL and unwraps the result with the same helpers.
 */

// `errorMessage` is imported from many components; re-exporting keeps that import path intact.
export {errorMessage} from './result';

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
