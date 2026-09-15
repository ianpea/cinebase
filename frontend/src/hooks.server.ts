import {env} from '$env/dynamic/private';
import {json, type Handle} from '@sveltejs/kit';

/**
 * The frontend only ever talks to movie-service through relative paths: the urql client uses
 * `url: '/graphql'` and artwork images use `/uploads/...`.
 *
 * In development `vite.config.ts` proxies both of those to http://localhost:8081, but that proxy
 * belongs to the dev server and does not exist in the production Node server that Docker runs.
 * This hook performs the same forwarding, which keeps every URL relative and means movie-service
 * needs no CORS configuration.
 */
const PROXIED_PREFIXES = ['/graphql', '/uploads'];

const DEFAULT_ORIGIN = 'http://localhost:8081';

export const handle: Handle = async ({event, resolve}) => {
    const {pathname, search} = event.url;

    const isProxied = PROXIED_PREFIXES.some(
        (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
    );
    if(!isProxied) return resolve(event);

    // Forward only what movie-service consumes. `content-type` has to survive the hop because it
    // carries the multipart boundary for artwork uploads.
    const headers = new Headers();
    for(const name of ['accept', 'content-type']) {
        const value = event.request.headers.get(name);
        if(value) headers.set(name, value);
    }

    const method = event.request.method;
    const hasBody = method !== 'GET' && method !== 'HEAD';

    let upstream: Response;
    try {
        upstream = await fetch(new URL(`${pathname}${search}`, env.MOVIE_SERVICE_ORIGIN ?? DEFAULT_ORIGIN), {
            method,
            headers,
            // Buffered rather than streamed: a streaming body needs `duplex: 'half'` in undici, and
            // artwork uploads are capped at 10 MB on the server anyway.
            body: hasBody ? await event.request.arrayBuffer() : undefined
        });
    } catch {
        // movie-service takes a few seconds to boot, and this server starts instantly, so a request
        // can land here before the backend is listening. Answer with a GraphQL-shaped body so the
        // client's normal error handling shows something actionable instead of "Internal Error".
        return json(
            {errors: [{message: 'The movie service is unavailable. Please try again in a moment.'}]},
            {status: 503, headers: {'retry-after': '2'}}
        );
    }

    return new Response(upstream.body, {
        status: upstream.status,
        headers: {
            'content-type': upstream.headers.get('content-type') ?? 'application/octet-stream'
        }
    });
};
