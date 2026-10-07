import { useLayoutEffect } from 'react';
import { useLocation } from 'react-router-dom';





export default function ScrollToTop() {

    const { pathname } = useLocation();
    useLayoutEffect(() => {

        const pc = document.querySelector('.page-container');
        if (pc) pc.scrollTop = 0;
        window.scrollTo(0, 0);
    }, [pathname]);

    return null;
}