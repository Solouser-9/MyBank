import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { FiCopy, FiArrowRight, FiActivity, FiEye, FiEyeOff } from 'react-icons/fi';
import api from '../api';
import { formatKobo, maskAcct, formatDate } from '../utils/format';
import { Spinner, ErrorBanner, useToast } from '../components/ui';
import { useScrollFade } from '../hooks/useScrollFade';
import { usePrintScreenGuard } from '../hooks/usePrivacy';

export default function Dashboard() {

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { toast } = useToast();
    const navigate = useNavigate();


    const fadeRef = useScrollFade([data]);


    const [balanceHidden, setBalanceHidden] = useState(
        () => localStorage.getItem('balanceHidden') === 'true'
    );
    const toggleBalance = () => {
        setBalanceHidden(h => {
            const next = !h;
            localStorage.setItem('balanceHidden', String(next));
            return next;
        });
    };

    // LAYER 3: Window privacy: mask balances when the browser loses focus or visibility.
    const [windowBlurred, setWindowBlurred] = useState(false);
    useEffect(() => {

        const onBlur = () => setWindowBlurred(true);
        const onFocus = () => setWindowBlurred(false);
        const onVis = () => setWindowBlurred(document.hidden);
        window.addEventListener('blur', onBlur);
        window.addEventListener('focus', onFocus);
        document.addEventListener('visibilitychange', onVis);
        return () => {
            window.removeEventListener('blur', onBlur);
            window.removeEventListener('focus', onFocus);
            document.removeEventListener('visibilitychange', onVis);
        };
    }, []);

    usePrintScreenGuard();

    useEffect(() => {


        api.request('/me/dashboard')
            .then(d => {
                localStorage.setItem('wm-user', d.user.first_name);
                setData(d);
            })
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    // LAYER 3: PIN onboarding guard: redirect users who have not configured a transaction PIN.
    useEffect(() => {

        if (data?.user && data.user.hasPin === false) {
            navigate('/set-pin');
        }
    }, [data, navigate]);


    const copyVa = async () => {

        if (!data?.virtualAccount?.account_number) return;
        try {
            // LAYER 3: Clipboard fallback: try the modern clipboard API before the legacy copy path.
            await navigator.clipboard.writeText(data.virtualAccount.account_number);
            toast('success', 'Copied to clipboard');
        } catch (err) {
            const el = document.createElement('textarea');
            el.value = data.virtualAccount.account_number;
            el.style.position = 'fixed';
            el.style.opacity = '0';
            document.body.appendChild(el);
            el.select();
            const ok = document.execCommand('copy');
            document.body.removeChild(el);
            if (ok) {
                toast('success', 'Copied to clipboard');
            } else {
                toast('error', 'Copy failed; please copy manually');
            }
        }
    };


    if (loading) return <Spinner />;
    if (error) return <ErrorBanner message={error} />;
    if (!data) return null;

    const privacyHidden = balanceHidden || windowBlurred;


    return (
        <div className="dashboard" data-user={data.user.first_name} ref={fadeRef}>

            <header className="page-header">
                <div>
                    <h1 className="page-title">Hello, {data.user.first_name}</h1>
                    <span className={`badge ${data.user.kyc_status === 'VERIFIED' ? 'badge-success' : 'badge-pending'}`}>
            KYC: {data.user.kyc_status}
          </span>
                </div>
            </header>


            <div className="dashboard-grid" data-fade>
                <div className="card balance-card">
                    <div className="flex-between">
                        <p className="text-muted">Available Balance</p>
                        <button className="btn-icon" onClick={toggleBalance}
                                aria-label={balanceHidden ? 'Show balance' : 'Hide balance'}>
                            {balanceHidden ? <FiEyeOff /> : <FiEye />}
                        </button>
                    </div>
                    <h2 className={`balance-amount ${privacyHidden ? 'amount-hidden' : ''}`}>
                        {privacyHidden ? '₦ ••••••' : formatKobo(data.balanceKobo)}
                    </h2>
                </div>

                <div className="card va-card">
                    <p className="text-muted">Account Details</p>
                    {data.virtualAccount ? (
                        <div className="va-details">
                            <h3 className="va-number">{maskAcct(data.virtualAccount.account_number)}</h3>
                            <p className="text-sm text-muted">{data.virtualAccount.bank_name}</p>
                            <button className="btn btn-outline btn-sm mt-2" onClick={copyVa}>
                                <FiCopy /> Copy Number
                            </button>
                        </div>
                    ) : (
                        <div className="va-pending mt-2">
                            <p className="text-sm text-muted">Your account number is coming...</p>
                        </div>
                    )}
                </div>
            </div>


            <div className="dashboard-stats mt-4" data-fade>
                <div className="card stat-card">
                    <p className="text-muted text-sm">Money In (This Month)</p>
                    <h4 className={`amount-credit ${privacyHidden ? 'amount-hidden' : ''}`}>
                        {privacyHidden ? '₦ ••••••' : formatKobo(data.monthInKobo)}
                    </h4>
                </div>
                <div className="card stat-card">
                    <p className="text-muted text-sm">Money Out (This Month)</p>
                    <h4 className={`amount-debit ${privacyHidden ? 'amount-hidden' : ''}`}>
                        {privacyHidden ? '₦ ••••••' : formatKobo(data.monthOutKobo)}
                    </h4>
                </div>
            </div>


            <div className="mt-2">
                <Link viewTransition to="/insights" className="btn-link text-sm flex-align">
                    View spending insights <FiArrowRight />
                </Link>
            </div>


            <div className="card mt-4" data-fade>
                <div className="flex-between mb-4">
                    <h3>Recent Transactions</h3>
                    <Link viewTransition to="/history" className="btn-link text-sm flex-align">
                        View all <FiArrowRight />
                    </Link>
                </div>
                <div className="transaction-list">
                    {data.recentTransactions && data.recentTransactions.length > 0 ? (
                        data.recentTransactions.map(tx => (
                            <div key={tx.reference} className="transaction-item">
                                <div className="tx-info">
                                    <p className="tx-counterparty">{tx.counterparty || 'Transaction'}</p>
                                    <p className="text-xs text-muted">{formatDate(tx.created_at)}</p>
                                </div>
                                <div className="tx-amount-status">
                                    <p className={`${tx.type === 'credit' ? 'amount-credit' : 'amount-debit'} ${privacyHidden ? 'amount-hidden' : ''}`}>
                                        {privacyHidden ? '₦ ••••••'
                                            : `${tx.type === 'credit' ? '+' : '-'}${formatKobo(tx.amount_kobo)}`}
                                    </p>
                                    <span className={`badge badge-${tx.status}`}>{tx.status}</span>
                                </div>
                            </div>
                        ))
                    ) : (
                        <div className="empty-state-inline">
                            <FiActivity className="empty-icon-small" />
                            <p className="text-muted text-sm">No recent transactions</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}