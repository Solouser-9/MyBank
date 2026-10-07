import { useState } from 'react';
import { formatKobo } from '../../utils/format';
import { ymLabel, compactNaira, niceStep, useWidth } from './chartHelpers';


function FlowBars({ months, masked }) {

    const [ref, width] = useWidth();
    const [hover, setHover] = useState(null);
    const H = 240, PL = 64, PR = 16, PT = 20, PB = 28;
    const w = width;
    const iw = Math.max(w - PL - PR, 10);
    const ih = H - PT - PB;


    const max = Math.max(...months.map(m => Math.max(m.inflowKobo, m.outflowKobo)), 1);
    const step = niceStep(max);
    const top = Math.ceil(max / step) * step;
    const ticks = [];
    for (let t = 0; t <= top + 1e-9; t += step) ticks.push(t);
    const Y = v => PT + ih - (v / top) * ih;

    const band = iw / months.length;
    const groupW = Math.min(band * 0.62, 44);
    const barW = Math.max(groupW / 2 - 2, 4);

    if (w === 0) return <div ref={ref} style={{ width: '100%' }} />;
    const every = Math.ceil(months.length / 6);


    return (
        <div ref={ref} style={{ position: 'relative' }}>
            <div className="chart-legend">
                <span><i style={{ background: 'var(--credit)' }} /> Money in</span>
                <span><i style={{ background: 'var(--debit)' }} /> Money out</span>
            </div>
            <svg width={w} height={H} onMouseLeave={() => setHover(null)}>
                {ticks.map(t => (
                    <g key={t}>
                        <line x1={PL} x2={PL + iw} y1={Y(t)} y2={Y(t)}
                              className={t === 0 ? 'gl-strong' : 'gl'} />
                        <text x={PL - 8} y={Y(t) + 3} textAnchor="end" className="axis-label">
                            {masked ? '₦ ••' : compactNaira(t)}
                        </text>
                    </g>
                ))}
                {months.map((m, i) => {
                    const cx = PL + i * band + band / 2;
                    const hIn = ih * (m.inflowKobo / top);
                    const hOut = ih * (m.outflowKobo / top);
                    const net = m.inflowKobo - m.outflowKobo;
                    const dim = hover !== null && hover !== i ? 0.45 : 1;
                    return (
                        <g key={m.ym} onMouseEnter={() => setHover(i)}>
                            <rect x={PL + i * band} y={PT} width={band} height={ih} fill="transparent" />
                            <rect x={cx - barW - 1} y={Y(m.inflowKobo)} width={barW}
                                  height={Math.max(hIn, m.inflowKobo > 0 ? 2 : 0)} rx="3"
                                  className="bar-in" opacity={dim} />
                            <rect x={cx + 1} y={Y(m.outflowKobo)} width={barW}
                                  height={Math.max(hOut, m.outflowKobo > 0 ? 2 : 0)} rx="3"
                                  className="bar-out" opacity={dim} />

                            {band >= 34 && !masked && (m.inflowKobo > 0 || m.outflowKobo > 0) && (
                                <text x={cx} y={Math.min(Y(m.inflowKobo), Y(m.outflowKobo)) - 6}
                                      textAnchor="middle" className="net-label"
                                      style={{ fill: net >= 0 ? 'var(--credit)' : 'var(--debit)' }}>
                                    {net >= 0 ? '+' : '−'}{compactNaira(Math.abs(net))}
                                </text>
                            )}
                            {i % every === 0 && (
                                <text x={cx} y={H - 8} textAnchor="middle" className="axis-label">
                                    {ymLabel(m.ym)}
                                </text>
                            )}
                        </g>
                    );
                })}
            </svg>
            {hover !== null && (
                <div className="chart-tooltip"
                     style={{ left: Math.max(80, Math.min(w - 80, PL + hover * band + band / 2)),
                         top: Math.min(Y(months[hover].inflowKobo), Y(months[hover].outflowKobo)) - 14 }}>
                    <div className="tt-label">{ymLabel(months[hover].ym, true)}</div>
                    <div className="tt-value" style={{ color: 'var(--credit)' }}>
                        {masked ? '₦ ••••' : `+${compactNaira(months[hover].inflowKobo)}`}
                    </div>
                    <div className="tt-value" style={{ color: 'var(--debit)' }}>
                        {masked ? '₦ ••••' : `−${compactNaira(months[hover].outflowKobo)}`}
                    </div>
                </div>
            )}
        </div>
    );
}

export default FlowBars;