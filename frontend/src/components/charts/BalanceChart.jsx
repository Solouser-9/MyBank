import { useState } from 'react';
import { formatKobo } from '../../utils/format';
import { ymLabel, compactNaira, niceStep, smoothPath, useWidth } from './chartHelpers';


function BalanceChart({ months, masked }) {

    const [ref, width] = useWidth();
    const [hover, setHover] = useState(null);
    const H = 240, PL = 64, PR = 16, PT = 16, PB = 28;
    const w = width;
    const iw = Math.max(w - PL - PR, 10);
    const ih = H - PT - PB;


    const vals = months.map(m => m.endBalanceKobo);
    const rawMax = Math.max(...vals, 0);
    const rawMin = Math.min(...vals, 0);
    const step = niceStep(rawMax - rawMin || 1);
    const top = Math.ceil(rawMax / step) * step;
    const bot = Math.floor(rawMin / step) * step;
    const ticks = [];
    for (let t = bot; t <= top + 1e-9; t += step) ticks.push(t);

    const X = i => PL + (months.length === 1 ? iw / 2 : (i / (months.length - 1)) * iw);
    const Y = v => PT + ih - ((v - bot) / ((top - bot) || 1)) * ih;
    const pts = months.map((m, i) => [X(i), Y(m.endBalanceKobo)]);
    const line = smoothPath(pts);
    const area = pts.length > 1
        ? `${line} L${pts[pts.length - 1][0]},${PT + ih} L${pts[0][0]},${PT + ih} Z`
        : '';


    const onMove = (e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const t = (e.clientX - rect.left - PL) / iw;
        const idx = Math.round(t * (months.length - 1));
        setHover(Math.max(0, Math.min(months.length - 1, idx)));
    };


    if (w === 0) return <div ref={ref} style={{ width: '100%' }} />;
    const every = Math.ceil(months.length / 6);
    const last = months.length - 1;


    return (
        <div ref={ref} style={{ position: 'relative' }}>
            <svg width={w} height={H} onMouseMove={onMove} onMouseLeave={() => setHover(null)}>
                <defs>
                    <linearGradient id="balanceFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" className="grad-a" />
                        <stop offset="100%" className="grad-b" />
                    </linearGradient>
                </defs>
                {ticks.map(t => (
                    <g key={t}>
                        <line x1={PL} x2={PL + iw} y1={Y(t)} y2={Y(t)} className="gl" />
                        <text x={PL - 8} y={Y(t) + 3} textAnchor="end" className="axis-label">
                            {masked ? '₦ ••' : compactNaira(t)}
                        </text>
                    </g>
                ))}
                {pts.length > 1 ? (
                    <>
                        <path d={area} fill="url(#balanceFill)" />
                        <path d={line} className="chart-line" />
                        <circle cx={pts[last][0]} cy={pts[last][1]} r="3.5" className="dot-main" />
                    </>
                ) : (
                    <circle cx={pts[0][0]} cy={pts[0][1]} r="4" className="dot-main" />
                )}
                {hover !== null && pts.length > 1 && (
                    <g>
                        <line x1={X(hover)} x2={X(hover)} y1={PT} y2={PT + ih} className="gl-strong" />
                        <circle cx={pts[hover][0]} cy={pts[hover][1]} r="4" className="dot-main" />
                    </g>
                )}
                {months.map((m, i) => i % every === 0 && (
                    <text key={m.ym} x={X(i)} y={H - 8} textAnchor="middle" className="axis-label">
                        {ymLabel(m.ym)}
                    </text>
                ))}
            </svg>
            {hover !== null && (
                <div className="chart-tooltip"
                     style={{ left: Math.max(70, Math.min(w - 70, pts[hover][0])), top: pts[hover][1] - 10 }}>
                    <div className="tt-label">{ymLabel(months[hover].ym, true)}</div>
                    <div className="tt-value">{masked ? '₦ ••••••' : formatKobo(months[hover].endBalanceKobo)}</div>
                </div>
            )}
        </div>
    );
}

export default BalanceChart;