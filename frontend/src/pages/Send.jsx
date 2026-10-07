import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { FiArrowLeft, FiChevronRight, FiCheck, FiX, FiLock } from 'react-icons/fi';
import api, { uuid } from '../api';
import { Spinner, ErrorBanner, Modal, MoneyInput, FieldError, PasswordInput, useToast } from '../components/ui';
import Select from '../components/Select';
import { otpProblems } from '../utils/validate';
import { withPageFade } from '../utils/motion';
import { useScrollFade } from '../hooks/useScrollFade';

export default function Send() {

    const navigate = useNavigate();
    const { toast } = useToast();
    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(false);
    const [resolving, setResolving] = useState(false);
    const [resolveError, setResolveError] = useState('');
    const [error, setError] = useState('');
    const [touched, setTouched] = useState({});
    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));
    const reveal = names => setTouched(prev => ({ ...prev, ...Object.fromEntries(names.map(n => [n, true])) }));


    const fadeRef = useScrollFade([]);


    const [banks, setBanks] = useState([]);
    const [beneficiaries, setBeneficiaries] = useState([]);
    const [destType, setDestType] = useState('external');


    const [form, setForm] = useState({
        receiverAccount: '',
        receiverId: null,
        receiverName: '',
        bankCode: '',
        accountNumber: '',
        narration: '',
        amountKobo: null,
        otpCode: '',
        pin: ''
    });

    const [showConfirmModal, setShowConfirmModal] = useState(false);
    const [beneficiaryId, setBeneficiaryId] = useState('');


    const idemKeyRef = useRef(null);

    useEffect(() => {

        api.request('/banks').then(setBanks).catch(console.error);
        api.request('/beneficiaries').then(setBeneficiaries).catch(console.error);
    }, []);


    const internalBens = beneficiaries.filter(b => b.bankCode === 'MYBANK');
    const externalBens = beneficiaries.filter(b => b.bankCode !== 'MYBANK');

    // LAYER 3: External recipient lookup: resolve the account name when the account and bank are known.
    useEffect(() => {
        if (destType !== 'external') return;
        const acct = form.accountNumber || '';
        if (acct.length === 10 && form.bankCode) {
            let cancelled = false;
            setResolving(true);
            api.request(`/banks/resolve?account_number=${form.accountNumber}&bank_code=${form.bankCode}`)
                .then(res => {
                    if (cancelled) return;
                    setForm(f => ({ ...f, receiverName: res.accountName || '' }));
                })
                .catch(() => { if (!cancelled) setForm(f => ({ ...f, receiverName: '' })); })
                .finally(() => { if (!cancelled) setResolving(false); });
            return () => { cancelled = true; };
        }
        setForm(f => (f.receiverName === '' ? f : { ...f, receiverName: '' }));
    }, [form.accountNumber, form.bankCode, destType]);

    // LAYER 3: Internal recipient lookup: ignore stale results when account input changes.
    useEffect(() => {
        if (destType !== 'internal') return;
        const acct = form.receiverAccount || '';
        if (acct.length !== 10) {
            setResolveError('');
            return;
        }
        let cancelled = false;
        setResolving(true);
        setResolveError('');
        api.request(`/beneficiaries/resolve?account_number=${acct}`)
            .then(res => {
                if (cancelled) return;
                setForm(f => ({
                    ...f,
                    receiverId: String(res.receiverId),
                    receiverName: res.accountName || '',
                }));
            })
            .catch(err => {
                if (cancelled) return;
                setResolveError(err.message || 'Account not found');
                setForm(f => ({ ...f, receiverId: null, receiverName: '' }));
            })
            .finally(() => { if (!cancelled) setResolving(false); });
        return () => { cancelled = true; };
    }, [form.receiverAccount, destType]);

    const bankProblemsNow = destType === 'external' && !form.bankCode ? ['Choose a bank'] : [];
    const acctProblemsNow = destType === 'external'
        ? (!form.accountNumber.trim() ? ['Enter the account number'] : [])
        : (!form.receiverAccount.trim() ? ["Enter the receiver's MyBank account number"] : []);


    const handleDestSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const problems = [...bankProblemsNow, ...acctProblemsNow];
        if (problems.length > 0) {
            setError(destType === 'internal'
                ? "Receiver's MyBank account number is required"
                : 'Bank and Account Number are required');
            reveal(destType === 'external' ? ['bankCode', 'accountNumber'] : ['receiverAccount']);
            return;
        }
        if (resolving) {
            return setError(destType === 'external'
                ? 'Verifying account name…'
                : 'Still verifying the account…');
        }
        if (!form.receiverName) {
            return setError(destType === 'internal'
                ? 'Account could not be verified; check the number'
                : 'Account name could not be verified; check the account number');
        }
        withPageFade(() => {
            setShowConfirmModal(false);
            setStep(2);
        });
    };


    const confirmName = () => {
        withPageFade(() => {
            setShowConfirmModal(false);
            setStep(2);
        });
    };

    const amountProblemsNow = (!form.amountKobo || form.amountKobo <= 0) ? ['Enter a valid amount'] : [];

    // LAYER 3: New idempotency key: create one key for this Step 2-to-3 payment attempt.

    const handleAmountSubmit = (e) => {
        e.preventDefault();
        setError('');
        if (amountProblemsNow.length > 0) {
            setError('Please fix the highlighted fields');
            reveal(['amount']);
            return;
        }
        withPageFade(() => {
            setError('');
            idemKeyRef.current = uuid();
            setStep(3);
        });
    };


    const requestOtp = async () => {
        setLoading(true);
        setError('');
        try {
            const res = await api.request('/otp/request', { method: 'POST', body: { purpose: 'transfer' } });
            toast('success', res.message || 'Verification code sent');
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const otpProblemsNow = !form.otpCode.trim()
        ? ['Request and enter the 6-digit code']
        : otpProblems(form.otpCode);
    const pinProblemsNow = !form.pin.trim() ? ['Enter your transaction PIN'] : [];


    const handleFinalSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const problems = [...otpProblemsNow, ...pinProblemsNow];
        if (problems.length > 0) {
            setError('Please fix the highlighted fields');
            reveal(['otpCode', 'pin']);
            return;
        }
        setLoading(true);
        try {
            const endpoint = destType === 'internal' ? '/transfers/internal' : '/transfers/external';
            const payload = destType === 'internal'
                ? { receiverId: parseInt(form.receiverId, 10), amountKobo: form.amountKobo, pin: form.pin, otpCode: form.otpCode,
                    ...(form.narration ? { narration: form.narration } : {}) }
                : { accountNumber: form.accountNumber, bankCode: form.bankCode, amountKobo: form.amountKobo, pin: form.pin, otpCode: form.otpCode,
                    ...(form.narration ? { narration: form.narration } : {}) };

            // LAYER 3: Reuse the same key on retries to prevent duplicate transfers.
            const res = await api.send(endpoint, payload, idemKeyRef.current);
            toast('success', res.message || 'Transfer successful');
            navigate('/dashboard', { viewTransition: true });
        } catch (err) {
            setError(err.message);
            setLoading(false);
        }
    };

    return (
        <div className="send-page max-w-md" data-user={localStorage.getItem('wm-user') || ''} ref={fadeRef}>
            <div className="flex-align mb-4">
                {step > 1 && (
                    <button className="btn-icon"
                            onClick={() => withPageFade(() => { setStep(step - 1); setError(''); })}
                            disabled={loading}>
                        <FiArrowLeft />
                    </button>
                )}
                <h1 className="page-title">Send Money</h1>
            </div>


            <div className="card" data-fade>
                {error && <ErrorBanner message={error} />}


                {step === 1 && (
                    <form onSubmit={handleDestSubmit} noValidate className="space-y-4">
                        <div className="form-group">
                            <label>Transfer Type</label>
                            <div className="flex-align mt-1">
                                <label className="flex-align mr-4">
                                    <input type="radio" name="destType" checked={destType === 'external'}
                                           onChange={() => withPageFade(() => { setDestType('external'); setBeneficiaryId(''); setError(''); })} />
                                    To Other Bank
                                </label>
                                <label className="flex-align">
                                    <input type="radio" name="destType" checked={destType === 'internal'}
                                           onChange={() => withPageFade(() => {
                                               setDestType('internal'); setBeneficiaryId(''); setError(''); setTouched({});
                                               setForm(f => ({ ...f, accountNumber: '', bankCode: '', receiverName: '' }));
                                           })} />
                                    To MyBank User
                                </label>
                            </div>
                        </div>

                        {destType === 'external' ? (
                            <>
                                {externalBens.length > 0 && (
                                    <div className="form-group">
                                        <label>SAVED BENEFICIARIES: OTHER BANKS</label>
                                        <Select value={beneficiaryId}
                                                onChange={(v) => {
                                                    setBeneficiaryId(v);
                                                    if (!v) return;
                                                    const b = externalBens.find(x => x.id.toString() === v);
                                                    if (b) setForm(f => ({ ...f, bankCode: b.bankCode, accountNumber: b.accountNumber }));
                                                }}
                                                placeholder="Select a beneficiary..."
                                                ariaLabel="Saved Beneficiary"
                                                options={[{ value: '', label: 'Select a beneficiary...' },
                                                    ...externalBens.map(b => ({ value: b.id.toString(), label: `${b.name} (${b.accountNumber})` }))]} />
                                    </div>
                                )}
                                <div className="form-group">
                                    <label>BANK</label>
                                    <Select value={form.bankCode}
                                            onChange={(v) => {
                                                setForm(f => ({ ...f, bankCode: v, receiverName: '' }));
                                                setTouched(prev => ({ ...prev, bankCode: true }));
                                            }}
                                            placeholder="Select Bank"
                                            ariaLabel="Bank"
                                            options={[{ value: '', label: 'Select Bank' },
                                                ...banks.map(b => ({ value: b.code, label: b.name }))]} />
                                    <FieldError problems={bankProblemsNow} show={touched.bankCode} />
                                </div>
                                <div className="form-group">
                                    <label>ACCOUNT NUMBER</label>
                                    <input type="text" name="accountNumber" inputMode="numeric" maxLength="10" className="input"
                                           value={form.accountNumber}
                                           onChange={e => setForm(f => ({ ...f, accountNumber: e.target.value.replace(/\D/g, '') }))}
                                           onBlur={markTouched} />
                                    <FieldError problems={acctProblemsNow} show={touched.accountNumber} />
                                </div>
                                <div className="form-group">
                                    <label>ACCOUNT NAME (AUTO-VERIFIED, LOCKED)</label>
                                    <div className={`resolved-name ${resolving ? 'resolving' : ''}`}>
                                        {resolving ? 'Verifying…'
                                            : form.receiverName ? form.receiverName
                                                : 'Enter a 10-digit account number to verify'}
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                {internalBens.length > 0 && (
                                    <div className="form-group">
                                        <label>SAVED MYBANK BENEFICIARIES</label>
                                        <Select value={beneficiaryId}
                                                onChange={(v) => {
                                                    setBeneficiaryId(v);
                                                    const b = internalBens.find(x => x.accountNumber === v);
                                                    if (b) setForm(f => ({ ...f, receiverAccount: b.accountNumber }));
                                                }}
                                                placeholder="Select a beneficiary..."
                                                ariaLabel="MyBank Beneficiary"
                                                options={[{ value: '', label: 'Select a beneficiary...' },
                                                    ...internalBens.map(b => ({ value: b.accountNumber, label: `${b.name} (${b.accountNumber})` }))]} />
                                    </div>
                                )}
                                <div className="form-group">
                                    <label>RECEIVER'S MYBANK ACCOUNT NUMBER</label>
                                    <input type="text" name="receiverAccount" inputMode="numeric" maxLength="10" className="input"
                                           value={form.receiverAccount}
                                           onChange={e => setForm(f => ({ ...f, receiverAccount: e.target.value.replace(/\D/g, '') }))}
                                           onBlur={markTouched} />
                                    <FieldError problems={acctProblemsNow} show={touched.receiverAccount} />
                                </div>
                                <div className="form-group">
                                    <label>RECEIVER NAME (AUTO-VERIFIED, LOCKED)</label>
                                    <div className={`resolved-name ${resolving ? 'resolving' : ''} ${resolveError ? 'resolve-error' : ''}`}>
                                        {resolving ? 'Verifying…'
                                            : resolveError ? resolveError
                                                : form.receiverName || 'Enter the 10-digit MyBank account number'}
                                    </div>
                                </div>
                            </>
                        )}
                        <button type="submit" className="btn btn-primary w-full mt-4" disabled={loading || resolving}>
                            {loading ? <Spinner /> : resolving ? 'Verifying…' : <>Next <FiChevronRight /></>}
                        </button>
                    </form>
                )}


                {step === 2 && (
                    <form onSubmit={handleAmountSubmit} noValidate className="space-y-4">
                        <div className="form-group">
                            <label>AMOUNT (₦)</label>
                            <MoneyInput id="amount" valueKobo={form.amountKobo} onChangeKobo={val => setForm(f => ({ ...f, amountKobo: val }))} />
                            <FieldError problems={amountProblemsNow} show={touched.amount} />
                        </div>
                        <div className="form-group">
                            <label>NARRATION (OPTIONAL)</label>
                            <input type="text" className="input" maxLength="100"
                                   placeholder="Reason for sending (e.g. Rent, Gift)"
                                   value={form.narration}
                                   onChange={e => setForm(f => ({ ...f, narration: e.target.value }))} />
                        </div>
                        <button type="submit" className="btn btn-primary w-full mt-4">
                            Next <FiChevronRight />
                        </button>
                    </form>
                )}


                {step === 3 && (
                    <form onSubmit={handleFinalSubmit} noValidate className="space-y-4">
                        <div className="form-group">
                            <label>OTP CODE</label>
                            <div className="flex-align">
                                <input type="text" name="otpCode" inputMode="numeric" maxLength="6" autoComplete="one-time-code"
                                       className="input" value={form.otpCode}
                                       onChange={e => setForm(f => ({ ...f, otpCode: e.target.value }))}
                                       onBlur={markTouched} />
                                <button type="button" className="btn btn-outline" onClick={requestOtp} disabled={loading}>Request</button>
                            </div>
                            <FieldError problems={otpProblemsNow} show={touched.otpCode} />
                        </div>
                        <div className="form-group">
                            <label>TRANSACTION PIN</label>
                            <PasswordInput id="pin" name="pin" inputMode="numeric" maxLength="6"
                                           icon={<FiLock className="input-icon" />}
                                           value={form.pin}
                                           onChange={e => setForm(f => ({ ...f, pin: e.target.value }))}
                                           onBlur={markTouched} />
                            <FieldError problems={pinProblemsNow} show={touched.pin} />
                        </div>
                        <button type="submit" className="btn btn-primary w-full mt-4" disabled={loading}>
                            {loading ? <Spinner /> : 'Send Money'}
                        </button>
                    </form>
                )}
            </div>


            <Modal open={showConfirmModal} title="Confirm Recipient" onClose={() => setShowConfirmModal(false)}>
                <p className="mb-4">
                    {destType === 'internal'
                        ? <>You're sending to <strong>{form.receiverName}</strong> (MyBank account {form.receiverAccount}). Correct?</>
                        : <>You're sending to <strong>{form.receiverName}</strong>, {banks.find(b => b.code === form.bankCode)?.name}. Correct?</>}
                </p>
                <div className="flex-align" style={{ justifyContent: 'flex-end' }}>
                    <button className="btn btn-outline" onClick={() => setShowConfirmModal(false)}><FiX /> Cancel</button>
                    <button className="btn btn-primary" onClick={confirmName}><FiCheck /> Correct</button>
                </div>
            </Modal>
        </div>
    );
}