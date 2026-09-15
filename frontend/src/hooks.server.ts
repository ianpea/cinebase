import {env} from '$env/dynamic/private';
import {json, type Handle} from '@sveltejs/kit';

/**
 * Vite proxies /graphql and /uploads only in its dev server; adapter-node has none, so this hook
 * reproduces it. Browser URLs stay relative, which is why movie-service needs no CORS.
 * SSR loads bypass it and query movie-service directly (`$lib/server/graphql.ts`).
 */
const PROXIED_PREFIXES = ['/graphql', '/uploads'];

const DEFAULT_ORIGIN = 'http://localhost:8081';

export const handle: Handle = async ({event, resolve}) => {
    const {pathname, search} = event.url;

    const isProxied = PROXIED_PREFIXES.some(
        (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
    );
    if(!isProxied) return resolve(event);

    // Only the headers movie-service consumes; content-type carries the multipart upload boundary.
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
            // Buffered: streaming a body needs `duplex: 'half'` in undici, and uploads cap at 10 MB.
            body: hasBody ? await event.request.arrayBuffer() : undefined
        });
    } catch {
        // movie-service boots slower than this server, so a request can arrive before it listens.
        // GraphQL-shaped body so the existing client error handling shows it.
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
