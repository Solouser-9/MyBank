import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { FiLock, FiShield } from 'react-icons/fi';
import api from '../api';
import { Spinner, ErrorBanner, FieldError, PasswordInput, useToast } from '../components/ui';
import PasswordStrength from '../components/PasswordStrength';
import { passwordProblems, pinProblems } from '../utils/validate';
import { useScrollFade } from '../hooks/useScrollFade';

export default function Settings() {

    const navigate = useNavigate();
    const { toast } = useToast();



    const fadeRef = useScrollFade([]);


    const [pwdForm, setPwdForm] = useState({ oldPassword: '', newPassword: '' });
    const [pwdLoading, setPwdLoading] = useState(false);
    const [pwdError, setPwdError] = useState('');
    const [pwdTouched, setPwdTouched] = useState({});
    const markPwdTouched = e => setPwdTouched(prev => ({ ...prev, [e.target.name]: true }));

    const [pinForm, setPinForm] = useState({ pin: '' });
    const [pinLoading, setPinLoading] = useState(false);
    const [pinError, setPinError] = useState('');
    const [pinTouched, setPinTouched] = useState({});
    const markPinTouched = e => setPinTouched(prev => ({ ...prev, [e.target.name]: true }));



    const oldPwdProblems = pwdForm.oldPassword ? [] : ['Enter your current password'];
    const newPwdProblems = passwordProblems(pwdForm.newPassword);
    const pinProblemsNow = pinProblems(pinForm.pin);





    useEffect(() => {
        if (pwdError === 'Please fix the highlighted fields'
            && oldPwdProblems.length === 0 && newPwdProblems.length === 0) {
            setPwdError('');
        }
    }, [pwdError, pwdForm.oldPassword, pwdForm.newPassword]);


    useEffect(() => {
        if (pinError === 'Please fix the highlighted fields' && pinProblemsNow.length === 0) {
            setPinError('');
        }
    }, [pinError, pinForm.pin]);


    const handlePasswordChange = async (e) => {
        e.preventDefault();
        setPwdError('');
        const problems = [...oldPwdProblems, ...newPwdProblems];
        if (problems.length > 0) {
            setPwdError('Please fix the highlighted fields');
            setPwdTouched({ oldPassword: true, newPassword: true });
            return;
        }

        setPwdLoading(true);
        try {
            await api.request('/auth/change-password', { method: 'POST', body: pwdForm });
            toast('success', 'Password changed. Please log in again.');


            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            navigate('/login', { viewTransition: true });
        } catch (err) {
            setPwdError(err.message);
            setPwdLoading(false);
        }
    };


    const handlePinChange = async (e) => {
        e.preventDefault();
        setPinError('');
        if (pinProblemsNow.length > 0) {
            setPinError('Please fix the highlighted fields');
            setPinTouched({ pin: true });
            return;
        }

        setPinLoading(true);
        try {
            await api.request('/auth/set-pin', { method: 'POST', body: pinForm });
            toast('success', 'Transaction PIN updated successfully');
            setPinForm({ pin: '' });
            setPinTouched({});
        } catch (err) {
            setPinError(err.message);
        } finally {
            setPinLoading(false);
        }
    };


    return (
        <div className="settings-page max-w-md" ref={fadeRef}>
            <h1 className="page-title mb-4">Settings</h1>


            <div className="card mb-4" data-fade>
                <div className="flex-align mb-4">
                    <FiShield className="text-primary" style={{ fontSize: '1.5rem' }} />
                    <h3>Change Password</h3>
                </div>
                <p className="text-sm text-muted mb-4">Changing your password will log you out of all devices.</p>

                {pwdError && <ErrorBanner message={pwdError} />}

                <form onSubmit={handlePasswordChange} noValidate className="space-y-4">
                    <div className="form-group">
                        <label htmlFor="oldPassword">Current Password</label>
                        <PasswordInput id="oldPassword" name="oldPassword" autoComplete="current-password"
                                       value={pwdForm.oldPassword}
                                       onChange={e => setPwdForm({...pwdForm, oldPassword: e.target.value})}
                                       onBlur={markPwdTouched} />
                        <FieldError problems={oldPwdProblems} show={pwdTouched.oldPassword} />
                    </div>
                    <div className="form-group">
                        <label htmlFor="newPassword">New Password</label>
                        <PasswordInput id="newPassword" name="newPassword" autoComplete="new-password" maxLength={128}
                                       value={pwdForm.newPassword}
                                       onChange={e => setPwdForm({...pwdForm, newPassword: e.target.value})}
                                       onBlur={markPwdTouched} />
                        <PasswordStrength password={pwdForm.newPassword} />
                        <FieldError problems={newPwdProblems} show={pwdTouched.newPassword} />
                    </div>
                    <button type="submit" className="btn btn-primary w-full mt-2" disabled={pwdLoading}>
                        {pwdLoading ? <Spinner /> : 'Update Password'}
                    </button>
                </form>
            </div>


            <div className="card" data-fade>
                <div className="flex-align mb-4">
                    <FiLock className="text-primary" style={{ fontSize: '1.5rem' }} />
                    <h3>Change Transaction PIN</h3>
                </div>

                {pinError && <ErrorBanner message={pinError} />}

                <form onSubmit={handlePinChange} noValidate className="space-y-4">
                    <div className="form-group">
                        <label htmlFor="newPin">New PIN (4-6 digits)</label>
                        <PasswordInput id="newPin" name="pin" inputMode="numeric" maxLength={6}
                                       value={pinForm.pin}
                                       onChange={e => setPinForm({...pinForm, pin: e.target.value})}
                                       onBlur={markPinTouched} />
                        <FieldError problems={pinProblemsNow} show={pinTouched.pin} />
                    </div>
                    <button type="submit" className="btn btn-primary w-full mt-2" disabled={pinLoading}>
                        {pinLoading ? <Spinner /> : 'Update PIN'}
                    </button>
                </form>
            </div>
        </div>
    );
}