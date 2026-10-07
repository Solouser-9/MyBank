





import { useState, useEffect, useRef } from 'react';

export const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

export const ymLabel = (ym, withYear = false) => {

    const [y, m] = ym.split('-').map(Number);
    return withYear ? `${MONTHS[m - 1]} ${String(y).slice(2)}` : MONTHS[m - 1];
};

export const compactNaira = (kobo) => {

    const n = kobo / 100;
    const sign = n < 0 ? '-' : '';
    const a = Math.abs(n);
    if (a >= 1_000_000) return `${sign}₦${(a / 1_000_000).toFixed(1)}M`;
    if (a >= 1_000) return `${sign}₦${(a / 1_000).toFixed(1)}k`;
    return `${sign}₦${Math.round(a)}`;
};

export const isoLocal = (d) =>

    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const daysInclusive = (from, to) => {

    const [fy, fm, fd] = from.split('-').map(Number);
    const [ty, tm, td] = to.split('-').map(Number);
    return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86400000) + 1;
};


export function niceStep(span, target = 4) {

    const raw = span / target;
    const mag = Math.pow(10, Math.floor(Math.log10(raw || 1)));
    for (const m of [1, 2, 2.5, 5, 10]) if (raw <= m * mag) return m * mag;
    return 10 * mag;
}


export function smoothPath(pts) {

    if (pts.length < 2) return '';
    let d = `M${pts[0][0]},${pts[0][1]}`;
    for (let i = 0; i < pts.length - 1; i++) {
        const p0 = pts[Math.max(i - 1, 0)], p1 = pts[i],
            p2 = pts[i + 1], p3 = pts[Math.min(i + 2, pts.length - 1)];
        d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6}`
            + ` ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6}`
            + ` ${p2[0]},${p2[1]}`;
    }
    return d;
}


export function useWidth() {

    const ref = useRef(null);
    const [width, setWidth] = useState(0);
    useEffect(() => {

        const el = ref.current;
        if (!el || typeof ResizeObserver === 'undefined') return;
        const ro = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
        ro.observe(el);
        return () => ro.disconnect();
    }, []);
    return [ref, width];
}