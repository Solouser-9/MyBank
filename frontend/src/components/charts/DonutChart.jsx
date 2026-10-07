import { useState } from 'react';
import { formatKobo } from '../../utils/format';









function DonutChart({ categories, totalOut, masked }) {

    const OPACITIES = [1, 0.55, 0.25];
    const size = 200, stroke = 30, hoverGrow = 4;
    const r = (size - stroke - hoverGrow) / 2, cx = size / 2, cy = size / 2;
    const C = 2 * Math.PI * r;
    const [hover, setHover] = useState(null);


    let acc = 0;
    const slices = categories.map((c, i) => {
        const frac = totalOut > 0 ? c.totalKobo / totalOut : 0;
        const dash = frac * C;
        const offset = -acc;
        acc += dash;
        return { ...c, frac, dash, offset, opacity: OPACITIES[i % OPACITIES.length] };
    });


    const hovered = hover !== null ? slices[hover] : null;
    const centerLabel = hovered ? hovered.category : 'Total out';
    const centerValue = masked
        ? '₦ ••••••'
        : hovered
            ? `${Math.round(hovered.frac * 100)}%`
            : formatKobo(totalOut);


    return (
        <div className="donut-wrap">
            <svg width={size} height={size} style={{ flexShrink: 0 }}>
                <g transform={`rotate(-90 ${cx} ${cy})`}>
                    {slices.map((s, i) => (
                        <circle key={s.category}
                                cx={cx} cy={cy} r={r}
                                fill="none"
                                stroke="var(--ink)"
                                strokeOpacity={s.opacity}
                                strokeWidth={hover === i ? stroke + hoverGrow : stroke}
                                strokeDasharray={`${s.dash} ${C - s.dash}`}
                                strokeDashoffset={s.offset}
                                className="donut-slice"
                                onMouseEnter={() => setHover(i)}
                                onMouseLeave={() => setHover(null)} />
                    ))}
                </g>
                <text x={cx} y={cy - 6} textAnchor="middle" className="donut-center-label">{centerLabel}</text>
                <text x={cx} y={cy + 16} textAnchor="middle" className="donut-center-value">{centerValue}</text>
            </svg>
            <div className="donut-legend">
                {slices.map((s, i) => (
                    <div key={s.category} className="donut-legend-item"
                         onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
                        <i style={{ background: 'var(--ink)', opacity: s.opacity }} />
                        <span>{s.category}</span>
                        <span className="amt">
                            {masked ? '₦ ••••' : formatKobo(s.totalKobo)} · {Math.round(s.frac * 100)}%
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default DonutChart;