import { useState, useEffect } from 'react';
import { FiSearch, FiFileText, FiEye, FiEyeOff, FiCopy } from 'react-icons/fi';
import api from '../api';
import { formatKobo, formatDate } from '../utils/format';
import { Spinner, ErrorBanner, EmptyState, Modal, useToast } from '../components/ui';
import Select from '../components/Select';
import { useBalanceHidden, useWindowBlur, usePrintScreenGuard } from '../hooks/usePrivacy';
import { useScrollFade } from '../hooks/useScrollFade';

export default function History() {

    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [offset, setOffset] = useState(0);
    const [hasMore, setHasMore] = useState(true);


    const [selected, setSelected] = useState(null);
    const { toast } = useToast();


    const [filters, setFilters] = useState({
        search: '',
        from: '',
        to: '',
        type: '',
        status: ''
    });




    const fadeRef = useScrollFade([transactions]);




    const [balanceHidden, toggleBalanceHidden] = useBalanceHidden();
    const windowBlurred = useWindowBlur();
    usePrintScreenGuard();
    const privacyHidden = balanceHidden || windowBlurred;


    const fetchHistory = (reset = false) => {
        const currentOffset = reset ? 0 : offset;
        if (reset) {
            setLoading(true);
            setTransactions([]);
        }

        const params = new URLSearchParams({ limit: 20, offset: currentOffset });
        if (filters.search) params.append('search', filters.search);
        if (filters.from) params.append('from', filters.from);
        if (filters.to) params.append('to', filters.to);
        if (filters.type) params.append('type', filters.type);
        if (filters.status) params.append('status', filters.status);

        api.request(`/transactions?${params.toString()}`)
            .then(data => {
                if (reset) {
                    setTransactions(data);
                } else {
                    setTransactions(prev => [...prev, ...data]);
                }
                setHasMore(data.length === 20);
                setOffset(currentOffset + 20);
            })
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    };

    useEffect(() => {

        fetchHistory(true);

    }, [filters]);


    const handleFilterChange = (e) => {
        const { name, value } = e.target;
        setFilters(prev => ({ ...prev, [name]: value }));
    };




    const handleSelectChange = (name) => (value) => {
        setFilters(prev => ({ ...prev, [name]: value }));
    };




    const copyRef = async () => {
        if (!selected) return;
        try {
            await navigator.clipboard.writeText(selected.reference);
            toast('success', 'Reference copied');
        } catch (err) {
            const el = document.createElement('textarea');
            el.value = selected.reference;
            el.style.position = 'fixed';
            el.style.opacity = '0';
            document.body.appendChild(el);
            el.select();
            const ok = document.execCommand('copy');
            document.body.removeChild(el);
            if (ok) toast('success', 'Reference copied');
            else toast('error', 'Copy failed; please copy manually');
        }
    };


    return (
        <div className="history-page" data-user={localStorage.getItem('wm-user') || ''} ref={fadeRef}>
            <div className="flex-between mb-4">
                <h1 className="page-title">Transaction History</h1>
                <button className="btn-icon" onClick={toggleBalanceHidden}
                        aria-label={balanceHidden ? 'Show amounts' : 'Hide amounts'}>
                    {balanceHidden ? <FiEye /> : <FiEyeOff />}
                </button>
            </div>


            <div className="card mb-4 filter-card">
                <div className="filter-grid">
                    <div className="input-group">
                        <FiSearch className="input-icon" />
                        <input type="text" name="search" className="input with-icon" placeholder="Search counterparty..." value={filters.search} onChange={handleFilterChange} />
                    </div>
                    <div className="input-group">
                        <input type="date" name="from" className="input" value={filters.from} onChange={handleFilterChange} aria-label="From Date" />
                    </div>
                    <div className="input-group">
                        <input type="date" name="to" className="input" value={filters.to} onChange={handleFilterChange} aria-label="To Date" />
                    </div>
                    <div className="input-group">
                        <Select value={filters.type}
                                onChange={handleSelectChange('type')}
                                placeholder="All Types"
                                ariaLabel="Type"
                                options={[
                                    { value: '', label: 'All Types' },
                                    { value: 'credit', label: 'Credit' },
                                    { value: 'debit', label: 'Debit' },
                                ]} />
                    </div>
                    <div className="input-group">
                        <Select value={filters.status}
                                onChange={handleSelectChange('status')}
                                placeholder="All Statuses"
                                ariaLabel="Status"
                                options={[
                                    { value: '', label: 'All Statuses' },
                                    { value: 'successful', label: 'Successful' },
                                    { value: 'pending', label: 'Pending' },
                                    { value: 'failed', label: 'Failed' },
                                    { value: 'reversed', label: 'Reversed' },
                                ]} />
                    </div>
                </div>
            </div>


            {error && <ErrorBanner message={error} />}


            {loading && transactions.length === 0 ? (
                <Spinner />
            ) : transactions.length === 0 ? (
                <EmptyState icon={FiFileText} message="No transactions found matching your filters" />
            ) : (
                <div className="card p-0 overflow-x-auto" data-fade>
                    <table className="table w-full">
                        <thead>
                        <tr>
                            <th>Date</th>
                            <th>Details</th>
                            <th>Reference</th>
                            <th className="text-right">Amount</th>
                            <th className="text-right">Balance After</th>
                        </tr>
                        </thead>
                        <tbody>
                        {transactions.map((tx, i) => {



                            const prev = i > 0 ? transactions[i - 1] : null;
                            const isNewDay = !prev ||
                                new Date(prev.created_at).toDateString() !== new Date(tx.created_at).toDateString();
                            return (
                                <tr key={tx.reference}
                                    className={`clickable ${i > 0 && isNewDay ? 'day-start' : ''}`}
                                    onClick={() => setSelected(tx)}
                                    onKeyDown={e => {
                                        if (e.key === 'Enter' || e.key === ' ') {
                                            e.preventDefault();
                                            setSelected(tx);
                                        }
                                    }}
                                    tabIndex={0}
                                    aria-label={`View details for ${tx.counterparty || 'transaction'}`}>
                                    <td className="text-sm whitespace-nowrap" data-label="Date">{formatDate(tx.created_at)}</td>
                                    <td data-label="Details">
                                        <p className="font-medium">{tx.counterparty || 'Transaction'}</p>
                                        <span className={`badge badge-${tx.status} mt-1`}>{tx.status}</span>
                                    </td>
                                    <td className="text-xs text-muted font-mono" data-label="Reference" title={tx.reference}>{tx.reference}</td>
                                    <td data-label="Amount">
                                        <p className={tx.type === 'credit' ? 'amount-credit' : 'amount-debit'}>
                                            {privacyHidden ? '₦ ••••••' : `${tx.type === 'credit' ? '+' : '-'}${formatKobo(tx.amount_kobo)}`}
                                        </p>
                                        {tx.fee_kobo > 0 && <p className="text-xs text-muted">Fee: {privacyHidden ? '₦ ••••' : formatKobo(tx.fee_kobo)}</p>}
                                    </td>
                                    <td data-label="Balance After">{privacyHidden ? '₦ ••••••' : formatKobo(tx.balance_after_kobo)}</td>
                                </tr>
                            );
                        })}
                        </tbody>
                    </table>
                </div>
            )}


            {hasMore && !loading && (
                <div className="text-center mt-4">
                    <button className="btn btn-outline" onClick={() => fetchHistory(false)}>Load More</button>
                </div>
            )}
            {loading && transactions.length > 0 && (
                <div className="text-center mt-4"><Spinner /></div>
            )}


            <Modal open={!!selected} title="Transaction Details" onClose={() => setSelected(null)}>
                {selected && (
                    <>
                        <div className="detail-summary text-center">
                            <p className={`detail-amount ${selected.type === 'credit' ? 'amount-credit' : 'amount-debit'}`}>
                                {selected.type === 'credit' ? '+' : '-'}{formatKobo(selected.amount_kobo)}
                            </p>
                            <span className={`badge badge-${selected.status}`}>{selected.status}</span>
                        </div>
                        <div className="detail-list">
                            <div className="detail-row">
                                <span>Direction</span>
                                <strong>{selected.type === 'credit' ? 'Money in' : 'Money out'}</strong>
                            </div>
                            <div className="detail-row">
                                <span>Date</span>
                                <strong>{formatDate(selected.created_at)}</strong>
                            </div>
                            <div className="detail-row">
                                <span>Counterparty</span>
                                <strong>{selected.counterparty || '-'}</strong>
                            </div>
                            {selected.fee_kobo > 0 && (
                                <div className="detail-row">
                                    <span>Fee</span>
                                    <strong>{formatKobo(selected.fee_kobo)}</strong>
                                </div>
                            )}
                            <div className="detail-row">
                                <span>Balance after</span>
                                <strong>{formatKobo(selected.balance_after_kobo)}</strong>
                            </div>
                            <div className="detail-row">
                                <span>Reference</span>
                                <span className="detail-ref">
                                    <strong className="font-mono">{selected.reference}</strong>
                                    <button className="btn-link" onClick={copyRef} aria-label="Copy reference">
                                        <FiCopy />
                                    </button>
                                </span>
                            </div>
                        </div>
                    </>
                )}
            </Modal>
        </div>
    );
}