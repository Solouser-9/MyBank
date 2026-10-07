import { useState, useEffect } from 'react';
import { FiDownload, FiFileText } from 'react-icons/fi';
import api from '../api';
import { Spinner, ErrorBanner, FieldError, useToast } from '../components/ui';
import { useScrollFade } from '../hooks/useScrollFade';

export default function Statements() {

    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [touched, setTouched] = useState({});
    const { toast } = useToast();



    const fadeRef = useScrollFade([]);


    const fromProblems = from ? [] : ['Choose a start date'];
    const toProblems = from && to && from > to
        ? ['End date must be after start date']
        : (to ? [] : ['Choose an end date']);




    useEffect(() => {
        if (error === 'Please fix the highlighted fields'
            && fromProblems.length === 0 && toProblems.length === 0) {
            setError('');
        }
    }, [error, from, to]);


    const handleDownload = async (e) => {
        e.preventDefault();
        setError('');

        const problems = [...fromProblems, ...toProblems];
        if (problems.length > 0) {
            setError('Please fix the highlighted fields');
            setTouched({ from: true, to: true });
            return;
        }

        setLoading(true);
        try {
            const params = new URLSearchParams({ from, to });
            const blob = await api.request(`/statements?${params.toString()}`, { blob: true });

            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `statement_${from}_to_${to}.csv`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);

            toast('success', 'Statement downloaded successfully');
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };


    return (
        <div className="statements-page max-w-md mx-auto" ref={fadeRef}>
            <h1 className="page-title mb-4">Account Statements</h1>

            <div className="card" data-fade>
                <div className="text-center mb-6">
                    <FiFileText className="text-4xl text-primary mx-auto mb-2" style={{ fontSize: '2.5rem' }} />
                    <p className="text-muted">Download your transaction history as a CSV file.</p>
                </div>

                {error && <ErrorBanner message={error} />}


                <form onSubmit={handleDownload} noValidate className="space-y-4 max-w-md mx-auto">
                    <div className="form-group">
                        <label htmlFor="from">From Date</label>
                        <input
                            type="date"
                            id="from" name="from"
                            className="input"
                            value={from}
                            onChange={(e) => setFrom(e.target.value)}
                        />
                        <FieldError problems={fromProblems} show={touched.from} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="to">To Date</label>
                        <input
                            type="date"
                            id="to" name="to"
                            className="input"
                            value={to}
                            onChange={(e) => setTo(e.target.value)}
                        />
                        <FieldError problems={toProblems} show={touched.to} />
                    </div>

                    <button type="submit" className="btn btn-primary w-full mt-4" disabled={loading}>
                        {loading ? <Spinner /> : <><FiDownload /> Download CSV</>}
                    </button>
                </form>
            </div>
        </div>
    );
}