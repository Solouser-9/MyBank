import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { request } from '../api';
import { Spinner, ErrorBanner, FieldError, PasswordInput, useToast } from '../components/ui';
import { pinProblems } from '../utils/validate';

export default function SetPin() {

    const navigate = useNavigate();
    const { toast } = useToast();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [pin, setPin] = useState('');
    const [touched, setTouched] = useState({});
    const markTouched = e => setTouched(prev => ({ ...prev, [e.target.name]: true }));

    useEffect(() => {

        document.title = "Set PIN - MyBank";
    }, []);


    const pinProblemsNow = pinProblems(pin);




    useEffect(() => {
        if (error === 'Please fix the highlighted fields' && pinProblemsNow.length === 0) {
            setError('');
        }
    }, [error, pin]);


    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        if (pinProblemsNow.length > 0) {
            setError('Please fix the highlighted fields');
            setTouched({ pin: true });
            return;
        }

        setLoading(true);
        try {
            await request('/auth/set-pin', {
                method: 'POST',
                body: { pin }
            });
            toast('success', 'Transaction PIN set');
            navigate('/dashboard', { viewTransition: true });
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };


    return (
        <div className="auth-layout">
            <div className="card auth-card">
                <h2 className="card-title text-center">Set Your Transaction PIN</h2>
                <ErrorBanner message={error} onDismiss={() => setError('')} />


                <form onSubmit={handleSubmit} noValidate>
                    <div className="form-group">
                        <label htmlFor="pin">Transaction PIN (4-6 digits)</label>
                        <PasswordInput
                            id="pin" name="pin"
                            inputMode="numeric"
                            autoComplete="new-password"
                            maxLength={6}
                            className="input text-center"
                            style={{ letterSpacing: '0.5rem', fontSize: '1.25rem' }}
                            value={pin}
                            onChange={e => setPin(e.target.value)}
                            onBlur={markTouched}
                        />
                        <FieldError problems={pinProblemsNow} show={touched.pin} />
                    </div>
                    <button type="submit" className="btn btn-primary mt-1 w-full" disabled={loading || pin.length < 4}>
                        {loading ? <Spinner /> : 'Save PIN'}
                    </button>
                </form>

                <p className="text-center mt-1 text-sm">
                    This PIN is required every time money moves.
                </p>
            </div>
        </div>
    );
}