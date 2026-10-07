import { useState } from 'react';
import { formatKobo } from '../../utils/format';
import { ymLabel, compactNaira, niceStep, useWidth } from './chartHelpers';










function WaterfallChart({ opening, months, masked }) {

    const [ref, width] = useWidth();
    const [hover, setHover] = useState(null);
    const H = 260, PL = 64, PR = 16, PT = 16, PB = 28;
    const w = width;
    const iw = Math.max(w - PL - PR, 10);
    const ih = H - PT - PB;


    const steps = [{ label: 'Start', kind: 'fixed', from: 0, to: opening }];
    let prev = opening;
    months.forEach(m => {
        const delta = m.endBalanceKobo - prev;
        steps.push({ label: ymLabel(m.ym), ym: m.ym,
            kind: delta >= 0 ? 'up' : 'down',
            from: prev, to: m.endBalanceKobo, net: delta });
        prev = m.endBalanceKobo;
    });
    steps.push({ label: 'Now', kind: 'fixed', from: 0, to: prev });


    const all = steps.flatMap(s => [s.from, s.to]);
    const rawMax = Math.max(...all, 0);
    const rawMin = Math.min(...all, 0);
    const step = niceStep(rawMax - rawMin || 1);
    const top = Math.ceil(rawMax / step) * step;
    const bot = Math.floor(rawMin / step) * step;
    const ticks = [];
    for (let t = bot; t <= top + 1e-9; t += step) ticks.push(t);
    const Y = v => PT + ih - ((v - bot) / ((top - bot) || 1)) * ih;

    const n = steps.length;
    const band = iw / n;
    const barW = Math.min(band * 0.6, 44);


    if (w === 0) return <div ref={ref} style={{ width: '100%' }} />;
    const every = Math.ceil(n / 8);


    return (
        <div ref={ref} style={{ position: 'relative' }}>
            <svg width={w} height={H} onMouseLeave={() => setHover(null)}>
                {ticks.map(t => (
                    <g key={t}>
                        <line x1={PL} x2={PL + iw} y1={Y(t)} y2={Y(t)} className="gl" />
                        <text x={PL - 8} y={Y(t) + 3} textAnchor="end" className="axis-label">
                            {masked ? '₦ ••' : compactNaira(t)}
                        </text>
                    </g>
                ))}
                {steps.map((s, i) => {
                    const cx = PL + i * band + band / 2;
                    const yTop = Y(Math.max(s.from, s.to));
                    const h = s.from === s.to ? 0 : Math.max(Math.abs(Y(s.from) - Y(s.to)), 2);
                    const cls = s.kind === 'fixed' ? 'bar-fixed' : s.kind === 'up' ? 'bar-in' : 'bar-out';
                    return (
                        <g key={i} onMouseEnter={() => setHover(i)}>
                            <rect x={PL + i * band} y={PT} width={band} height={ih} fill="transparent" />
                            <rect x={cx - barW / 2} y={yTop} width={barW} height={h} rx="2"
                                  className={cls} opacity={hover !== null && hover !== i ? 0.5 : 1} />
                            {i < n - 1 && (
                                <line x1={cx + barW / 2} x2={PL + (i + 1) * band + band / 2 - barW / 2}
                                      y1={Y(steps[i].to)} y2={Y(steps[i].to)}
                                      className="gl-strong" strokeDasharray="3 3" />
                            )}
                            {(s.kind === 'fixed' || i % every === 0) && (
                                <text x={cx} y={H - 8} textAnchor="middle" className="axis-label">{s.label}</text>
                            )}
                        </g>
                    );
                })}
            </svg>
            {hover !== null && (() => {
                const s = steps[hover];
                return (
                    <div className="chart-tooltip"
                         style={{ left: Math.max(80, Math.min(w - 80, PL + hover * band + band / 2)),
                             top: Y(Math.max(s.from, s.to)) - 10 }}>
                        <div className="tt-label">{s.kind === 'fixed' ? s.label : ymLabel(s.ym, true)}</div>
                        <div className="tt-value">
                            {masked ? '₦ ••••••'
                                : s.kind === 'fixed'
                                    ? formatKobo(s.to)
                                    : `${s.net >= 0 ? '+' : '−'}${compactNaira(Math.abs(s.net))} → ${formatKobo(s.to)}`}
                        </div>
                    </div>
                );
            })()}
        </div>
    );
}

export default WaterfallChart;