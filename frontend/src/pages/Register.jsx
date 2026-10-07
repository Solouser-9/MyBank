import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { request } from '../api';
import { Spinner, ErrorBanner, FieldError, PasswordInput, useToast } from '../components/ui';
import PasswordStrength from '../components/PasswordStrength';
import { passwordProblems, nameProblems, emailProblems, phoneProblems } from '../utils/validate';

export default function Register() {

    const navigate = useNavigate();
    const { toast } = useToast();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [touched, setTouched] = useState({});
    const [form, setForm] = useState({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        password: ''
    });

    useEffect(() => {

        document.title = "Register - MyBank";
    }, []);



    const fieldProblems = {
        firstName: nameProblems(form.firstName, 'First name'),
        lastName: nameProblems(form.lastName, 'Last name'),
        email: emailProblems(form.email),
        phone: phoneProblems(form.phone),
        password: passwordProblems(form.password),
    };




    useEffect(() => {
        if (error === 'Please fix the highlighted fields'
            && fieldProblems.firstName.length === 0 && fieldProblems.lastName.length === 0
            && fieldProblems.email.length === 0 && fieldProblems.phone.length === 0
            && fieldProblems.password.length === 0) {
            setError('');
        }
    }, [error, form]);


    const markTouched = (e) => {
        const { name } = e.target;
        setTouched(prev => ({ ...prev, [name]: true }));
    };


    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');


        const hasProblems = Object.values(fieldProblems).some(p => p.length > 0);
        setTouched({ firstName: true, lastName: true, email: true, phone: true, password: true });
        if (hasProblems) {
            setError('Please fix the highlighted fields');
            return;
        }

        setLoading(true);
        try {
            const data = await request('/auth/register', {
                method: 'POST',
                body: form,
                auth: false
            });
            sessionStorage.setItem('pendingUserId', data.userId);
            sessionStorage.setItem('isNewUser', 'true');
            sessionStorage.setItem('otpHint', data.message || 'A verification code has been sent');


            toast('success', data.message || 'Registration successful.');
            navigate('/verify', { viewTransition: true });
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };


    const handleChange = (e) => {
        setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
    };


    return (
        <div className="auth-layout">
            <div className="card auth-card">
                <Link viewTransition to="/" className="auth-back">← Back to home</Link>
                <Link viewTransition to="/" className="auth-brand" aria-label="MyBank, back to home">MyBank</Link>
                <h2 className="card-title text-center">Create an Account</h2>
                <ErrorBanner message={error} onDismiss={() => setError('')} />


                <form onSubmit={handleSubmit} noValidate>
                    <div className="form-group">
                        <label htmlFor="firstName">First Name</label>
                        <input id="firstName" name="firstName" type="text" className="input" maxLength={50}
                               value={form.firstName} onChange={handleChange} onBlur={markTouched} />
                        <FieldError problems={fieldProblems.firstName} show={touched.firstName} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="lastName">Last Name</label>
                        <input id="lastName" name="lastName" type="text" className="input" maxLength={50}
                               value={form.lastName} onChange={handleChange} onBlur={markTouched} />
                        <FieldError problems={fieldProblems.lastName} show={touched.lastName} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="email">Email</label>
                        <input id="email" name="email" type="email" autoComplete="email" className="input"
                               value={form.email} onChange={handleChange} onBlur={markTouched} />
                        <FieldError problems={fieldProblems.email} show={touched.email} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="phone">Phone</label>
                        <input id="phone" name="phone" type="tel" autoComplete="tel" placeholder="+2348012345678"
                               className="input" maxLength={16}
                               value={form.phone} onChange={handleChange} onBlur={markTouched} />
                        <FieldError problems={fieldProblems.phone} show={touched.phone} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="password">Password</label>
                        <PasswordInput id="password" name="password" autoComplete="new-password" maxLength={128}
                                       value={form.password}
                                       onChange={e => setForm(prev => ({ ...prev, password: e.target.value }))}
                                       onBlur={markTouched} />
                        <PasswordStrength password={form.password} />
                        <FieldError problems={fieldProblems.password} show={touched.password} />
                    </div>

                    <button type="submit" className="btn btn-primary mt-1 w-full" disabled={loading}>
                        {loading ? <Spinner /> : 'Register'}
                    </button>
                </form>


                <p className="text-center mt-1 text-sm">
                    Already have an account? <Link viewTransition to="/login">Log in</Link>
                </p>
            </div>
        </div>
    );
}