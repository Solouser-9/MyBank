import { useState, useEffect, useMemo } from 'react';
import { FiEye, FiEyeOff, FiActivity } from 'react-icons/fi';
import api from '../api';
import { formatKobo } from '../utils/format';
import { Spinner, ErrorBanner, EmptyState } from '../components/ui';
import { useBalanceHidden, useWindowBlur, usePrintScreenGuard } from '../hooks/usePrivacy';
import { useScrollFade } from '../hooks/useScrollFade';
import { compactNaira, isoLocal, daysInclusive } from '../components/charts/chartHelpers';
import BalanceChart from '../components/charts/BalanceChart';
import FlowBars from '../components/charts/FlowBars';
import WaterfallChart from '../components/charts/WaterfallChart';
import DonutChart from '../components/charts/DonutChart';

function TrendBadge({ current, avg, upColor, downColor }) {

    if (!avg || avg <= 0) return null;
    const pct = Math.round(((current - avg) / avg) * 100);
    const up = pct >= 0;
    return (
        <p className="trend-badge" style={{ color: up ? upColor : downColor }}>
            {up ? '↑' : '↓'} {Math.abs(pct)}% vs 3-mo avg
        </p>
    );
}

const PERIODS = [
    { label: '1M', value: '1M', months: 1 },
    { label: '3M', value: '3M', months: 3 },
    { label: '6M', value: '6M', months: 6 },
    { label: '1Y', value: '1Y', months: 12 },
    { label: 'Custom', value: 'custom' },
];

export default function Insights() {

    const [period, setPeriod] = useState('6M');
    const [customFrom, setCustomFrom] = useState('');
    const [customTo, setCustomTo] = useState('');
    const [appliedCustom, setAppliedCustom] = useState(null);
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // LAYER 3: Privacy shield: combine the manual amount toggle with automatic focus masking.
    const [balanceHidden, toggleBalanceHidden] = useBalanceHidden();
    const windowBlurred = useWindowBlur();
    usePrintScreenGuard();
    const masked = balanceHidden || windowBlurred;


    const fadeRef = useScrollFade([data]);

    useEffect(() => {

        document.title = "Insights - MyBank";
    }, []);

    // LAYER 3: Date range calculation: convert the selected period or custom dates into one API window.
    const range = useMemo(() => {

        if (period === 'custom') return appliedCustom;
        const p = PERIODS.find(x => x.value === period);
        const now = new Date();
        const first = new Date(now.getFullYear(), now.getMonth() - (p.months - 1), 1);
        return { from: isoLocal(first), to: isoLocal(now) };
    }, [period, appliedCustom]);


    useEffect(() => {
        if (!range) return;
        let cancelled = false;
        setLoading(true);
        setError('');
        api.request(`/me/analytics?from=${range.from}&to=${range.to}`)
            .then(d => { if (!cancelled) setData(d); })
            .catch(err => { if (!cancelled) setError(err.message); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [range]);

    // LAYER 3: Historical data trim: start charts at the user's first activity month.
    const visibleMonths = useMemo(() => {

        if (!data?.months) return [];
        return data.firstActivityYm
            ? data.months.filter(m => m.ym >= data.firstActivityYm)
            : data.months;
    }, [data]);

    const trajectory = data && visibleMonths.length > 0 ? {
        start: data.openingBalanceKobo,
        end: visibleMonths[visibleMonths.length - 1].endBalanceKobo,
    } : null;


    const applyCustom = (e) => {
        e.preventDefault();
        if (!customFrom || !customTo) return;
        if (customFrom > customTo) {
            setError("'From' must be on or before 'To'");
            return;
        }
        setError('');
        setAppliedCustom({ from: customFrom, to: customTo });
    };


    const isEmpty = data
        && data.totals.inKobo === 0 && data.totals.outKobo === 0
        && data.openingBalanceKobo === 0 && data.currentBalanceKobo === 0;

    const hasSpending = data && data.totals.outKobo > 0;
    const cmp = data?.comparison || null;


    return (
        <div className="insights-page" data-user={localStorage.getItem('wm-user') || ''} ref={fadeRef}>
            <div className="flex-between mb-4">
                <h1 className="page-title">Insights</h1>
                <button className="btn-icon" onClick={toggleBalanceHidden}
                        aria-label={balanceHidden ? 'Show amounts' : 'Hide amounts'}>
                    {balanceHidden ? <FiEye /> : <FiEyeOff />}
                </button>
            </div>


            <div className="mb-4">
                <div className="period-pills">
                    {PERIODS.map(p => (
                        <button key={p.value}
                                className={`period-pill ${period === p.value ? 'active' : ''}`}
                                onClick={() => setPeriod(p.value)}>
                            {p.label}
                        </button>
                    ))}
                </div>
                {period === 'custom' && (
                    <form onSubmit={applyCustom} className="custom-range">
                        <input type="date" className="input" value={customFrom}
                               max={isoLocal(new Date())} aria-label="From date"
                               onChange={e => setCustomFrom(e.target.value)} />
                        <span className="text-muted text-sm">→</span>
                        <input type="date" className="input" value={customTo}
                               max={isoLocal(new Date())} aria-label="To date"
                               onChange={e => setCustomTo(e.target.value)} />
                        <button type="submit" className="btn btn-outline btn-sm">Apply</button>
                    </form>
                )}
            </div>

            {error && <ErrorBanner message={error} onDismiss={() => setError('')} />}

            {loading && !data ? (
                <Spinner />
            ) : data && (
                <>

                    <div className="dashboard-stats mt-2 mb-4" data-fade>
                        <div className="card stat-card">
                            <p className="text-muted text-sm">Money In</p>
                            <h4 className="amount-credit">{masked ? '₦ ••••••' : formatKobo(data.totals.inKobo)}</h4>
                            {cmp && (
                                <TrendBadge current={cmp.thisMonthInKobo} avg={cmp.avgInKobo}
                                            upColor="var(--credit)" downColor="var(--debit)" />
                            )}
                        </div>
                        <div className="card stat-card">
                            <p className="text-muted text-sm">Money Out</p>
                            <h4 className="amount-debit">{masked ? '₦ ••••••' : formatKobo(data.totals.outKobo)}</h4>
                            <p className="text-xs text-muted mt-1">
                                ≈ {masked ? '₦ ••' : compactNaira(data.totals.outKobo / daysInclusive(data.from, data.to))}/day
                            </p>
                            {cmp && (
                                <TrendBadge current={cmp.thisMonthOutKobo} avg={cmp.avgOutKobo}
                                            upColor="var(--debit)" downColor="var(--credit)" />
                            )}
                        </div>
                        <div className="card stat-card">
                            <p className="text-muted text-sm">Net Flow</p>
                            <h4 className={data.totals.inKobo - data.totals.outKobo >= 0 ? 'amount-credit' : 'amount-debit'}>
                                {masked ? '₦ ••••••' : formatKobo(data.totals.inKobo - data.totals.outKobo)}
                            </h4>
                            <p className="text-xs text-muted mt-1">
                                {data.totals.inKobo > 0
                                    ? (data.totals.outKobo > data.totals.inKobo
                                        ? `Spent ${(data.totals.outKobo / data.totals.inKobo).toFixed(1)}× income`
                                        : `Savings rate: ${Math.round(((data.totals.inKobo - data.totals.outKobo) / data.totals.inKobo) * 100)}%`)
                                    : 'No income this period'}
                            </p>
                            {trajectory && (
                                <p className="text-xs text-muted mt-1">
                                    Balance {masked ? '₦ ••••••' : formatKobo(trajectory.start)}
                                    {' → '}
                                    {masked ? '₦ ••••••' : formatKobo(trajectory.end)}
                                </p>
                            )}
                        </div>
                        <div className="card stat-card">
                            <p className="text-muted text-sm">Fees Paid</p>
                            <h4>{masked ? '₦ ••••••' : formatKobo(data.totals.feesKobo)}</h4>
                            {data.totals.outKobo > 0 && (
                                <p className="text-xs text-muted mt-1">
                                    {((data.totals.feesKobo / data.totals.outKobo) * 100).toFixed(1)}% of spending
                                </p>
                            )}
                        </div>
                    </div>


                    {isEmpty ? (
                        <EmptyState icon={FiActivity} message="No money movement in this period yet" />
                    ) : visibleMonths.length > 0 && (
                        <>
                            <div className="card mb-4 chart-card" data-fade>
                                <h3 className="chart-title">Balance over time</h3>
                                <BalanceChart months={visibleMonths} masked={masked} />
                            </div>
                            <div className="card mb-4 chart-card" data-fade>
                                <h3 className="chart-title">Money in vs out</h3>
                                <FlowBars months={visibleMonths} masked={masked} />
                            </div>
                            <div className="card mb-4 chart-card" data-fade>
                                <h3 className="chart-title">Cash flow bridge</h3>
                                <p className="text-xs text-muted" style={{ marginBottom: '0.75rem' }}>
                                    How your opening balance became your closing balance, month by month.
                                </p>
                                <WaterfallChart opening={data.openingBalanceKobo} months={visibleMonths} masked={masked} />
                            </div>
                        </>
                    )}


                    {hasSpending && data.categories?.length > 0 && (
                        <div className="card mb-4 chart-card" data-fade>
                            <h3 className="chart-title">Spending mix</h3>
                            <p className="text-xs text-muted" style={{ marginBottom: '0.75rem' }}>
                                Where your money went this period: bills, MyBank users, and other banks.
                            </p>
                            <DonutChart categories={data.categories} totalOut={data.totals.outKobo} masked={masked} />
                        </div>
                    )}


                    {hasSpending && data.topRecipients?.length > 0 && (
                        <div className="card mb-4 chart-card" data-fade>
                            <h3 className="chart-title">Top recipients</h3>
                            {data.topRecipients.map((r, i) => {
                                const max = data.topRecipients[0].totalKobo || 1;
                                return (
                                    <div key={`${r.name}-${i}`} className="recipient-row">
                                        <span className="recipient-rank">{i + 1}</span>
                                        <div className="recipient-main">
                                            <p className="recipient-name" title={r.name}>{r.name}</p>
                                            <div className="recipient-bar"
                                                 style={{ width: `${Math.max((r.totalKobo / max) * 100, 3)}%` }} />
                                        </div>
                                        <span className="recipient-amt">
                                            {masked ? '₦ ••••' : formatKobo(r.totalKobo)}
                                        </span>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </>
            )}
        </div>
    );
}