import { useState, useEffect } from 'react';
import { FiUsers, FiPlus, FiX, FiEye, FiEyeOff } from 'react-icons/fi';
import api from '../api';
import { Spinner, ErrorBanner, EmptyState, FieldError, useToast } from '../components/ui';
import Select from '../components/Select';
import { otpProblems } from '../utils/validate';
import { withPageFade } from '../utils/motion';
import { useScrollFade } from '../hooks/useScrollFade';
import { useBalanceHidden, useWindowBlur, usePrintScreenGuard } from '../hooks/usePrivacy';

export default function Beneficiaries() {

    const [beneficiaries, setBeneficiaries] = useState([]);
    const [banks, setBanks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showAdd, setShowAdd] = useState(false);
    const [touched, setTouched] = useState({});

    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));
    const { toast } = useToast();





    const [balanceHidden, toggleBalanceHidden] = useBalanceHidden();
    const windowBlurred = useWindowBlur();
    usePrintScreenGuard();
    const privacyHidden = balanceHidden || windowBlurred;






    const fadeRef = useScrollFade([]);


    const [form, setForm] = useState({ name: '', bankCode: '', accountNumber: '', otpCode: '' });
    const [submitting, setSubmitting] = useState(false);


    const fetchBeneficiaries = () => {
        setLoading(true);
        api.request('/beneficiaries')
            .then(setBeneficiaries)
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    };

    useEffect(() => {

        fetchBeneficiaries();
        api.request('/banks').then(setBanks).catch(console.error);
    }, []);


    const nameProblems = form.name.trim() ? [] : ['Enter a name for this beneficiary'];
    const bankProblems = form.bankCode ? [] : ['Choose a bank'];
    const acctProblems = !form.accountNumber.trim()
        ? ['Enter the account number']
        : (form.bankCode === 'MYBANK' && !/^\d{10}$/.test(form.accountNumber.trim())
            ? ['MyBank account numbers are 10 digits']
            : []);
    const otpProblemsNow = !form.otpCode.trim()
        ? ['Request and enter the 6-digit code']
        : otpProblems(form.otpCode);




    const resetForm = () => {
        setForm({ name: '', bankCode: '', accountNumber: '', otpCode: '' });
        setTouched({});
        setError('');
    };






    useEffect(() => {
        if (error === 'Please fix the highlighted fields'
            && nameProblems.length === 0 && bankProblems.length === 0
            && acctProblems.length === 0 && otpProblemsNow.length === 0) {
            setError('');
        }
    }, [error, form.name, form.bankCode, form.accountNumber, form.otpCode]);


    const requestOtp = async () => {
        setError('');
        try {


            const res = await api.request('/otp/request', { method: 'POST', body: { purpose: 'beneficiary' } });
            toast('success', res.message || 'Verification code sent');
        } catch (err) {
            setError(err.message);
        }
    };


    const handleAdd = async (e) => {
        e.preventDefault();
        setError('');
        const problems = [...nameProblems, ...bankProblems, ...acctProblems, ...otpProblemsNow];
        if (problems.length > 0) {
            setError('Please fix the highlighted fields');
            setTouched({ name: true, bankCode: true, accountNumber: true, otpCode: true });
            return;
        }
        setSubmitting(true);
        try {
            await api.request('/beneficiaries', { method: 'POST', body: form });
            toast('success', 'Beneficiary added');
            withPageFade(() => {
                setShowAdd(false);
                resetForm();
            });
            fetchBeneficiaries();
        } catch (err) {
            setError(err.message);
        } finally {
            setSubmitting(false);
        }
    };


    return (
        <div className="beneficiaries-page max-w-md" data-user={localStorage.getItem('wm-user') || ''} ref={fadeRef}>
            <div className="flex-between mb-4">
                <h1 className="page-title">Beneficiaries</h1>
                {!showAdd && (
                    <div className="flex-align">
                        <button className="btn-icon" onClick={toggleBalanceHidden}
                                aria-label={balanceHidden ? 'Show details' : 'Hide details'}>
                            {balanceHidden ? <FiEye /> : <FiEyeOff />}
                        </button>
                        <button className="btn btn-primary btn-sm"
                                onClick={() => withPageFade(() => { setError(''); setShowAdd(true); })}>
                            <FiPlus /> Add New
                        </button>
                    </div>
                )}
            </div>

            {error && <ErrorBanner message={error} />}


            <div data-fade>

                {showAdd ? (
                    <div className="card mb-4">
                        <div className="flex-between mb-4">
                            <h3>Add Beneficiary</h3>
                            <button className="btn-icon"
                                    onClick={() => withPageFade(() => { setShowAdd(false); resetForm(); })}>
                                <FiX />
                            </button>
                        </div>
                        <form onSubmit={handleAdd} noValidate className="space-y-4">
                            <div className="form-group">
                                <label htmlFor="name">Account Name</label>
                                <input type="text" id="name" name="name" className="input" maxLength="100"
                                       value={form.name} onChange={e => setForm({...form, name: e.target.value})}
                                       onBlur={markTouched} />
                                <p className="text-xs text-muted">Any name you like; it's your address book label. The real account name is verified when you send.</p>
                                <FieldError problems={nameProblems} show={touched.name} />
                            </div>
                            <div className="form-group">
                                <label htmlFor="bankCode">Bank</label>
                                <Select value={form.bankCode}
                                        onChange={(v) => {
                                            setForm({...form, bankCode: v});
                                            setTouched(prev => ({ ...prev, bankCode: true }));
                                        }}
                                        placeholder="Select Bank"
                                        ariaLabel="Bank"
                                        options={[{ value: '', label: 'Select Bank' },
                                            { value: 'MYBANK', label: 'MyBank (internal transfer)' },
                                            ...banks.map(b => ({ value: b.code, label: b.name }))]} />
                                <FieldError problems={bankProblems} show={touched.bankCode} />
                            </div>
                            <div className="form-group">
                                <label htmlFor="accountNumber">Account Number</label>
                                <input type="text" id="accountNumber" name="accountNumber" inputMode="numeric" maxLength="10"
                                       className="input" value={form.accountNumber}
                                       onChange={e => setForm({...form, accountNumber: e.target.value})}
                                       onBlur={markTouched} />
                                <FieldError problems={acctProblems} show={touched.accountNumber} />
                            </div>
                            <div className="form-group">
                                <label htmlFor="otpCode">OTP Code</label>
                                <div className="flex-align">
                                    <input type="text" id="otpCode" name="otpCode" inputMode="numeric" maxLength="6"
                                           autoComplete="one-time-code" className="input" value={form.otpCode}
                                           onChange={e => setForm({...form, otpCode: e.target.value})}
                                           onBlur={markTouched} />
                                    <button type="button" className="btn btn-outline" onClick={requestOtp} disabled={submitting}>Request</button>
                                </div>
                                <FieldError problems={otpProblemsNow} show={touched.otpCode} />
                            </div>
                            <button type="submit" className="btn btn-primary w-full mt-4" disabled={submitting}>
                                {submitting ? <Spinner /> : 'Save Beneficiary'}
                            </button>
                        </form>
                    </div>
                ) : (
                    <>
                        {loading ? (
                            <Spinner />
                        ) : beneficiaries.length === 0 ? (
                            <EmptyState icon={FiUsers} message="No saved beneficiaries" />
                        ) : (
                            <div className="card p-0">
                                <div className="notification-list">
                                    {beneficiaries.map(b => (
                                        <div key={b.id} className="notification-item">
                                            <div>
                                                <p className="font-medium">
                                                    {privacyHidden ? '•'.repeat(Math.min(b.name.length, 12)) : b.name}
                                                </p>
                                                <p className="text-sm text-muted">
                                                    {privacyHidden ? '•••• ••••••' : b.accountNumber}
                                                    {' - '}
                                                    {b.bankCode === 'MYBANK' ? 'MyBank' : b.bankCode}
                                                </p>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}