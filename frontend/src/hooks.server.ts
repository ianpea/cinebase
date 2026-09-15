import {env} from '$env/dynamic/private';
import {json, type Handle} from '@sveltejs/kit';

/**
 * The browser only ever reaches movie-service through relative paths (`url: '/graphql'`, artwork
 * images `/uploads/...`). `vite.config.ts` proxies both during development, but that proxy lives in
 * the dev server and not in the adapter-node server Docker runs, so this hook reproduces it in
 * production. Keeping every browser URL relative means movie-service needs no CORS configuration.
 *
 * SSR loads bypass this — `$lib/server/graphql.ts` queries movie-service directly.
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
            // Buffered because streaming a body needs `duplex: 'half'` in undici, and uploads are
            // capped at 10 MB on the server anyway.
            body: hasBody ? await event.request.arrayBuffer() : undefined
        });
    } catch {
        // movie-service boots slower than this server, so a request can arrive before it is
        // listening. Answer in GraphQL shape so the client surfaces this instead of "Internal Error".
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
