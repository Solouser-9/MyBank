import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { request } from '../api';
import { Spinner, ErrorBanner, FieldError, PasswordInput, useToast } from '../components/ui';
import { otpProblems, emailProblems } from '../utils/validate';

export default function Login() {

    const navigate = useNavigate();
    const { toast } = useToast();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');


    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    const [step, setStep] = useState(1);
    const [userId, setUserId] = useState(null);
    const [code, setCode] = useState('');


    const [otpHint, setOtpHint] = useState('');


    const [touched, setTouched] = useState({});
    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));
    const reveal = names => setTouched(Object.fromEntries(names.map(n => [n, true])));

    useEffect(() => {

        document.title = "Log in - MyBank";
    }, []);



    const emailProblemsNow = emailProblems(email);
    const passwordProblemsNow = password ? [] : ['Enter your password'];


    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        const problems = [...emailProblemsNow, ...passwordProblemsNow];
        if (problems.length > 0) {
            setError('Please fix the highlighted fields');
            reveal(['email', 'password']);
            return;
        }

        setLoading(true);
        try {
            const data = await request('/auth/login', {
                method: 'POST',
                body: { email, password },
                auth: false
            });
            if (data.requiresOtp) {
                setUserId(data.userId);
                setOtpHint(data.message || 'A verification code has been sent');
                setStep(2);
                toast('success', data.message || 'A verification code has been sent');
            }
        } catch (err) {

            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const codeProblemsNow = otpProblems(code);





    useEffect(() => {
        if (error !== 'Please fix the highlighted fields') return;
        const clean = step === 1
            ? emailProblemsNow.length === 0 && passwordProblemsNow.length === 0
            : codeProblemsNow.length === 0;
        if (clean) setError('');
    }, [error, step, email, password, code]);


    const handleVerify = async (e) => {
        e.preventDefault();
        setError('');
        if (codeProblemsNow.length > 0) {
            setError('Please fix the highlighted fields');
            reveal(['code']);
            return;
        }

        setLoading(true);
        try {
            const data = await request('/auth/verify-login-otp', {
                method: 'POST',
                body: { userId, code },
                auth: false
            });
            localStorage.setItem('accessToken', data.accessToken);
            localStorage.setItem('refreshToken', data.refreshToken);

            if (sessionStorage.getItem('isNewUser') === 'true') {
                sessionStorage.removeItem('isNewUser');
                navigate('/set-pin', { viewTransition: true });
            } else {
                navigate('/dashboard', { viewTransition: true });
            }
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
                <h2 className="card-title text-center">Welcome Back</h2>
                <ErrorBanner message={error} onDismiss={() => setError('')} />


                {step === 1 ? (
                    <form onSubmit={handleLogin} noValidate>
                        <div className="form-group">
                            <label htmlFor="email">Email</label>
                            <input id="email" name="email" type="email" autoComplete="email" className="input"
                                   value={email} onChange={e => setEmail(e.target.value)} onBlur={markTouched} />
                            <FieldError problems={emailProblemsNow} show={touched.email} />
                        </div>
                        <div className="form-group">
                            <label htmlFor="password">Password</label>
                            <PasswordInput id="password" name="password" autoComplete="current-password"
                                           value={password}
                                           onChange={e => setPassword(e.target.value)}
                                           onBlur={markTouched} />
                            <FieldError problems={passwordProblemsNow} show={touched.password} />
                        </div>
                        <button type="submit" className="btn btn-primary mt-1 w-full" disabled={loading}>
                            {loading ? <Spinner /> : 'Log in'}
                        </button>
                    </form>
                ) : (

                    <form onSubmit={handleVerify} noValidate>
                        <p className="text-center mb-1 text-sm" style={{ color: 'var(--muted)' }}>
                            Enter the 6-digit code: {otpHint}
                        </p>
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
                            {loading ? <Spinner /> : 'Verify & Log in'}
                        </button>
                    </form>
                )}

                {step === 1 && (
                    <p className="text-center mt-1 text-sm">
                        Don't have an account? <Link viewTransition to="/register">Register</Link>
                    </p>
                )}
            </div>
        </div>
    );
}