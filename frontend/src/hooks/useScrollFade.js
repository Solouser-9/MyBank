import { useEffect, useRef } from 'react';

export function useScrollFade(deps = []) {

    const ref = useRef(null);

    useEffect(() => {
        const root = ref.current;

        if (!root) return;
        const targets = root.querySelectorAll('[data-fade]');
        if (targets.length === 0) return;

        // LAYER 3: Accessible scroll animations: skip the observer when reduced motion is requested.
        const reduce = typeof window.matchMedia === 'function'
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        if (reduce) {
            targets.forEach(t => t.classList.add('fade-on'));
            return;
        }

        // LAYER 3: Performance: IntersectionObserver toggles visibility without a scroll-event loop.

        const io = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                entry.target.classList.toggle('fade-on', entry.isIntersecting);
            });
        }, { threshold: 0.15, rootMargin: '0px 0px -12% 0px' });

        targets.forEach(t => io.observe(t));
        return () => io.disconnect();

    }, deps);

    return ref;
}