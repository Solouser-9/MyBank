import { flushSync } from 'react-dom';








export function withPageFade(update) {

    const reduce = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (typeof document.startViewTransition !== 'function' || reduce) {
        update();
        return;
    }
    document.startViewTransition(() => {



        flushSync(update);
    });
}







export function navigateWithFade(navigate, to, options = {}) {

    const reduce = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (typeof document.startViewTransition !== 'function' || reduce) {
        navigate(to, options);
        return;
    }
    const t = document.startViewTransition(() => {
        flushSync(() => navigate(to, options));
    });


    t.finished.finally(() => {   }).catch(() => {});
}