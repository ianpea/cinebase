import {cleanup} from '@testing-library/svelte';
import {afterEach, vi} from 'vitest';

// Unmount every component rendered by a test so the next test starts with an empty document.
afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
});

// jsdom does not implement these browser APIs, but bits-ui (dialogs, selects) and the artwork
// preview rely on them. Minimal stubs are enough for a DOM-only test run.
if(!('ResizeObserver' in globalThis)) {
    class ResizeObserverStub {
        observe() { }
        unobserve() { }
        disconnect() { }
    }
    Object.defineProperty(globalThis, 'ResizeObserver', {value: ResizeObserverStub, writable: true});
}

if(!('matchMedia' in window)) {
    Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
            matches: false,
            media: query,
            onchange: null,
            addEventListener: () => { },
            removeEventListener: () => { },
            addListener: () => { },
            removeListener: () => { },
            dispatchEvent: () => false
        })
    });
}

if(!Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = () => { };
}

// The artwork upload previews create blob URLs. jsdom implements this, but older jsdom versions
// and non-jsdom environments may not, so keep a fallback.
URL.createObjectURL ??= () => 'blob:test-preview';
URL.revokeObjectURL ??= () => { };
