import { useState, useEffect } from 'react';

export function useBalanceHidden() {

    const [hidden, setHidden] = useState(
        () => localStorage.getItem('balanceHidden') === 'true'
    );

    // LAYER 3: Cross-tab synchronization: follow balance visibility changes made in another tab.
    useEffect(() => {
        const sync = () => setHidden(localStorage.getItem('balanceHidden') === 'true');
        window.addEventListener('storage', sync);
        return () => window.removeEventListener('storage', sync);
    }, []);

    const toggle = () => setHidden(h => {
        const next = !h;
        localStorage.setItem('balanceHidden', String(next));
        return next;
    });

    return [hidden, toggle];
}

export function useWindowBlur() {

    const [blurred, setBlurred] = useState(false);
    useEffect(() => {
        const onBlur = () => setBlurred(true);
        const onFocus = () => setBlurred(false);
        const onVis = () => setBlurred(document.hidden);
        window.addEventListener('blur', onBlur);
        window.addEventListener('focus', onFocus);
        document.addEventListener('visibilitychange', onVis);
        return () => {
            window.removeEventListener('blur', onBlur);
            window.removeEventListener('focus', onFocus);
            document.removeEventListener('visibilitychange', onVis);
        };
    }, []);
    return blurred;
}

export function usePrintScreenGuard() {

    useEffect(() => {
        const onKey = (e) => {
            // LAYER 3: Clipboard deterrent: replace clipboard content when PrintScreen is pressed.
            if (e.key === 'PrintScreen') {
                navigator.clipboard?.writeText(
                    'Screenshots of MyBank are not permitted; sensitive data protected.'
                ).catch(() => {});
            }
        };
        window.addEventListener('keyup', onKey);
        return () => window.removeEventListener('keyup', onKey);
    }, []);
}