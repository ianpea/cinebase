import type {CombinedError, TypedDocumentNode} from '@urql/core';

/**
 * Result handling shared by the browser client (`api.ts`) and the server-side client used by SSR
 * loads (`$lib/server/graphql.ts`), so both unwrap a result and report a failure the same way.
 */

/** The `Data` type a document was declared with. */
export type QueryResult<Doc> = Doc extends TypedDocumentNode<infer Result, never> ? Result : never;

/** The variables type a document was declared with. */
export type QueryVariables<Doc> = Doc extends TypedDocumentNode<never, infer Vars> ? Vars : never;

/** Returns the data of a GraphQL result, or throws the most specific message it carries. */
export function unwrap(result: {error?: CombinedError; data?: unknown;}): unknown {
    if(result.error) {
        // Surface the server's GraphQL messages (e.g. validation) rather than the generic wrapper.
        const messages = result.error.graphQLErrors.map((e) => e.message);
        throw new Error(messages.length ? messages.join('; ') : result.error.message);
    }
    if(result.data == null) throw new Error('The server returned no data.');
    return result.data;
}

/** Normalises anything thrown (Error, CombinedError, string) into a display message. */
export function errorMessage(error: unknown): string {
    if(error instanceof Error && error.message) return error.message;
    const combined = error as CombinedError | null;
    if(combined?.graphQLErrors?.length) return combined.graphQLErrors.map((e) => e.message).join('; ');
    if(typeof error === 'string') return error;
    return 'Something went wrong. Please try again.';
}
