import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { request } from '../api';
import { Spinner, ErrorBanner, FieldError, useToast } from '../components/ui';
import { otpProblems } from '../utils/validate';

export default function Verify() {

    const navigate = useNavigate();
    const { toast } = useToast();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [code, setCode] = useState('');

    const pendingUserId = sessionStorage.getItem('pendingUserId');



    const [otpHint] = useState(
        sessionStorage.getItem('otpHint') || ''
    );


    const [touched, setTouched] = useState({});
    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));

    useEffect(() => {

        document.title = "Verify - MyBank";
    }, []);


    const codeProblemsNow = otpProblems(code);




    useEffect(() => {
        if (error === 'Please fix the highlighted fields' && codeProblemsNow.length === 0) {
            setError('');
        }
    }, [error, code]);


    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        if (codeProblemsNow.length > 0) {
            setError('Please fix the highlighted fields');
            setTouched({ code: true });
            return;
        }

        setLoading(true);
        try {
            await request('/auth/verify-otp', {
                method: 'POST',
                body: { userId: Number(pendingUserId), code },
                auth: false
            });
            sessionStorage.removeItem('otpHint');
            toast('success', 'Account verified. Please log in.');
            navigate('/login', { viewTransition: true });
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };


    return (
        <div className="auth-layout">
            <div className="card auth-card">
                <Link viewTransition to="/" className="auth-back">← Back to home</Link>
                <Link viewTransition to="/" className="auth-brand" aria-label="MyBank, back to home">MyBank</Link>
                <h2 className="card-title text-center">Verify Your Account</h2>
                <ErrorBanner message={error} onDismiss={() => setError('')} />


                <form onSubmit={handleSubmit} noValidate>
                    <div className="form-group">
                        <label htmlFor="code">OTP Code</label>
                        <input
                            id="code" name="code"
                            type="text"
                            inputMode="numeric"
                            autoComplete="one-time-code"
                            maxLength={6}
                            className="input text-center"
                            style={{ letterSpacing: '0.5rem', fontSize: '1.25rem' }}
                            value={code}
                            onChange={e => setCode(e.target.value)}
                            onBlur={markTouched}
                        />
                        <FieldError problems={codeProblemsNow} show={touched.code} />
                    </div>
                    <button type="submit" className="btn btn-primary mt-1 w-full" disabled={loading}>
                        {loading ? <Spinner /> : 'Verify'}
                    </button>
                </form>

                <p className="text-center mt-1 text-sm">
                    {otpHint
                        ? otpHint
                        : 'Your verification code was sent when you registered; check your email (or the server console in test mode).'}
                </p>
            </div>
        </div>
    );
}