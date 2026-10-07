export function getTheme() {

    return localStorage.getItem('theme') || 'light';
}

export function applyTheme(theme) {

    document.documentElement.dataset.theme = theme;
    localStorage.setItem('theme', theme);
}






export function toggleTheme(e) {

    const next = getTheme() === 'dark' ? 'light' : 'dark';

    const reduceMotion = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const click = e && typeof e.clientX === 'number' && !(e.clientX === 0 && e.clientY === 0)
        ? { x: e.clientX, y: e.clientY }
        : null;


    if (!reduceMotion && click && typeof document.startViewTransition === 'function') {
        const endRadius = Math.hypot(
            Math.max(click.x, window.innerWidth - click.x),
            Math.max(click.y, window.innerHeight - click.y)
        );







        document.documentElement.classList.add('theme-sweeping');

        const transition = document.startViewTransition(() => applyTheme(next));

        transition.ready.then(() => {
            document.documentElement.animate(
                {
                    clipPath: [
                        `circle(0px at ${click.x}px ${click.y}px)`,
                        `circle(${endRadius}px at ${click.x}px ${click.y}px)`
                    ]
                },
                {
                    duration: 650,
                    easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
                    pseudoElement: '::view-transition-new(root)'
                }
            );
        }).catch(() => {   });

        transition.finished.finally(() => {
            document.documentElement.classList.remove('theme-sweeping');
        });

        return next;
    }




    if (!reduceMotion) {
        document.documentElement.classList.add('theme-transitioning');
        void document.documentElement.offsetHeight;
        applyTheme(next);
        const raw = getComputedStyle(document.documentElement)
            .getPropertyValue('--theme-fade-duration') || '0.35s';
        const ms = (parseFloat(raw) * 1000) + 50;
        setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), ms);
        return next;
    }


    applyTheme(next);
    return next;
}