import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { FiFileText, FiLock } from 'react-icons/fi';
import api, { uuid } from '../api';
import { Spinner, ErrorBanner, MoneyInput, FieldError, PasswordInput, useToast } from '../components/ui';
import Select from '../components/Select';
import { otpProblems } from '../utils/validate';
import { useScrollFade } from '../hooks/useScrollFade';

export default function Bills() {

    const navigate = useNavigate();
    const { toast } = useToast();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [touched, setTouched] = useState({});
    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));
    const reveal = names => setTouched(prev => ({ ...prev, ...Object.fromEntries(names.map(n => [n, true])) }));



    const fadeRef = useScrollFade([]);


    const [form, setForm] = useState({
        biller: '',
        customerRef: '',
        amountKobo: null,
        otpCode: '',
        pin: ''
    });







    const idemKeyRef = useRef(null);
    useEffect(() => {

        idemKeyRef.current = uuid();
    }, [form.biller, form.customerRef, form.amountKobo]);


    const billerProblems = form.biller ? [] : ['Choose a biller'];
    const refProblems = form.customerRef.trim() ? [] : ['Enter the customer reference (phone or meter number)'];
    const amountProblems = (!form.amountKobo || form.amountKobo <= 0) ? ['Enter a valid amount'] : [];
    const otpProblemsNow = !form.otpCode.trim()
        ? ['Request and enter the 6-digit code']
        : otpProblems(form.otpCode);
    const pinProblemsNow = !form.pin.trim() ? ['Enter your transaction PIN'] : [];




    useEffect(() => {
        if (error === 'Please fix the highlighted fields'
            && billerProblems.length === 0 && refProblems.length === 0
            && amountProblems.length === 0 && otpProblemsNow.length === 0
            && pinProblemsNow.length === 0) {
            setError('');
        }
    }, [error, form.biller, form.customerRef, form.amountKobo, form.otpCode, form.pin]);


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


    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const problems = [...billerProblems, ...refProblems, ...amountProblems, ...otpProblemsNow, ...pinProblemsNow];
        if (problems.length > 0) {
            setError('Please fix the highlighted fields');
            reveal(['biller', 'customerRef', 'amount', 'otpCode', 'pin']);
            return;
        }
        setLoading(true);
        try {
            const res = await api.send('/bills/pay', form, idemKeyRef.current);
            toast('success', res.message || 'Bill payment submitted');
            navigate('/dashboard', { viewTransition: true });
        } catch (err) {
            setError(err.message);
            setLoading(false);
        }
    };


    return (
        <div className="bills-page max-w-md" ref={fadeRef}>
            <h1 className="page-title mb-4">Pay Bills</h1>

            <div className="card" data-fade>
                {error && <ErrorBanner message={error} />}


                <form onSubmit={handleSubmit} noValidate className="space-y-4">
                    <div className="form-group">
                        <label htmlFor="biller">Biller</label>
                        <Select value={form.biller}
                                onChange={(v) => {
                                    setForm({...form, biller: v});
                                    setTouched(prev => ({ ...prev, biller: true }));
                                }}
                                placeholder="Select Biller"
                                ariaLabel="Biller"
                                options={[{ value: '', label: 'Select Biller' },
                                    { value: 'airtime', label: 'Airtime' },
                                    { value: 'data', label: 'Data' },
                                    { value: 'electricity', label: 'Electricity' },
                                    { value: 'tv', label: 'TV' },
                                    { value: 'cable', label: 'Cable' }]} />
                        <FieldError problems={billerProblems} show={touched.biller} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="customerRef">Customer Reference (Phone / Meter No)</label>
                        <input type="text" id="customerRef" name="customerRef" className="input"
                               value={form.customerRef}
                               onChange={e => setForm({...form, customerRef: e.target.value})}
                               onBlur={markTouched} />
                        <FieldError problems={refProblems} show={touched.customerRef} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="amount">Amount (₦)</label>
                        <MoneyInput id="amount" valueKobo={form.amountKobo}
                                    onChangeKobo={val => setForm({...form, amountKobo: val})} />
                        <FieldError problems={amountProblems} show={touched.amount} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="otpCode">OTP Code</label>
                        <div className="flex-align">
                            <input type="text" id="otpCode" name="otpCode" inputMode="numeric" maxLength="6"
                                   autoComplete="one-time-code" className="input" value={form.otpCode}
                                   onChange={e => setForm({...form, otpCode: e.target.value})}
                                   onBlur={markTouched} />
                            <button type="button" className="btn btn-outline" onClick={requestOtp} disabled={loading}>Request</button>
                        </div>
                        <FieldError problems={otpProblemsNow} show={touched.otpCode} />
                    </div>

                    <div className="form-group">
                        <label htmlFor="pin">Transaction PIN</label>
                        <PasswordInput id="pin" name="pin" inputMode="numeric" maxLength={6}
                                       icon={<FiLock className="input-icon" />}
                                       value={form.pin}
                                       onChange={e => setForm({...form, pin: e.target.value})}
                                       onBlur={markTouched} />
                        <FieldError problems={pinProblemsNow} show={touched.pin} />
                    </div>

                    <button type="submit" className="btn btn-primary w-full mt-4" disabled={loading}>
                        {loading ? <Spinner /> : <><FiFileText /> Pay Bill</>}
                    </button>
                </form>
            </div>
        </div>
    );
}