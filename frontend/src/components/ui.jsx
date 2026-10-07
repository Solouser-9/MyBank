import { useWindowBlur } from '../hooks/usePrivacy';
import { useState, useEffect, createContext, useContext } from 'react';
import { FiAlertCircle, FiLoader, FiX, FiCheck, FiEye, FiEyeOff } from 'react-icons/fi';
import { parseNairaToKobo } from '../utils/format';

export function Spinner() {

    return <FiLoader className="spinner text-primary" style={{ fontSize: '1.5rem' }} />;
}

export function ErrorBanner({ message, onDismiss }) {

    if (!message) return null;
    return (
        <div className="card mb-4" style={{ background: '#ffebe6', borderColor: 'var(--danger)', padding: '1rem' }}>
            <div className="flex-between">
                <div className="flex-align text-danger">
                    <FiAlertCircle />
                    <span className="text-sm font-medium">{message}</span>
                </div>
                {onDismiss && (
                    <button className="btn-icon" onClick={onDismiss} style={{ padding: '0.25rem' }}>
                        <FiX />
                    </button>
                )}
            </div>
        </div>
    );
}

export function EmptyState({ icon: Icon, message }) {

    return (
        <div className="empty-state card">
            {Icon && <Icon className="empty-icon" />}
            <p>{message}</p>
        </div>
    );
}

export function FieldError({ problems, show = true }) {

    if (!show || !problems || problems.length === 0) return null;
    return (
        <ul className="field-errors" role="alert">
            {problems.map(p => <li key={p}>{p}</li>)}
        </ul>
    );
}

export function PasswordInput({ id, name, value, onChange, onBlur, maxLength, inputMode, autoComplete, className = 'input', style, icon }) {

    const [show, setShow] = useState(false);
    return (
        <div className="eye-wrap">
            {icon}
            <input
                type={show ? 'text' : 'password'}
                id={id}
                name={name}
                value={value}
                onChange={onChange}
                onBlur={onBlur}
                maxLength={maxLength}
                inputMode={inputMode}
                autoComplete={autoComplete}
                className={icon ? `${className} with-icon` : className}
                style={style}
            />
            <button type="button" className="input-eye"
                    onClick={() => setShow(s => !s)}
                    onMouseDown={e => e.preventDefault()}
                    aria-label={show ? 'Hide value' : 'Show value'}
                    tabIndex={-1}>
                {show ? <FiEyeOff /> : <FiEye />}
            </button>
        </div>
    );
}

// LAYER 3: Global toast state: expose the addToast action through React Context.
const ToastContext = createContext(null);

export function ToastProvider({ children }) {

    const [toasts, setToasts] = useState([]);


    const addToast = (type, message) => {
        const id = Date.now();
        setToasts(prev => [...prev, { id, type, message }]);
        setTimeout(() => {
            setToasts(prev => prev.filter(t => t.id !== id));
        }, 3000);
    };


    return (
        <ToastContext.Provider value={{ toast: addToast }}>
            {children}
            <div className="toast-container">
                {toasts.map(t => (
                    <div key={t.id} className={`toast toast-${t.type}`}>
                        {t.type === 'success' ? <FiCheck /> : <FiAlertCircle />}
                        {t.message}
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
}

export function useToast() {

    return useContext(ToastContext);
}

export function Modal({ open, title, onClose, children }) {

    const [visible, setVisible] = useState(open);
    const [shown, setShown] = useState(false);
    const blurred = useWindowBlur();

    // LAYER 3: Modal animation lifecycle: defer fade-in and delay unmount for the exit transition.
    useEffect(() => {
        if (open) {
            setVisible(true);
            const raf = requestAnimationFrame(() => setShown(true));
            return () => cancelAnimationFrame(raf);
        }
        if (!visible) return;
        setShown(false);
        const reduce = typeof window.matchMedia === 'function'
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        const ms = reduce ? 0 : 300;
        const t = setTimeout(() => setVisible(false), ms);
        return () => clearTimeout(t);
    }, [open, visible]);

    if (!visible) return null;

    return (
        <div className={`modal-overlay ${shown ? 'open' : 'closing'}`}
             aria-hidden={!shown}>
            <div className={`modal-content ${shown ? 'open' : 'closing'} ${blurred ? 'privacy-blur' : ''}`}>
                <div className="modal-header">
                    <h3 className="modal-title">{title}</h3>
                    <button className="btn-icon" onClick={onClose}><FiX /></button>
                </div>
                <div className="modal-body">
                    {children}
                </div>
            </div>
        </div>
    );
}

export function MoneyInput({ valueKobo, onChangeKobo, id, required }) {

    const [display, setDisplay] = useState(valueKobo ? (valueKobo / 100).toString() : '');

    // LAYER 3: Safe money input: parse Naira to integer Kobo and normalize the display on blur.
    const handleBlur = () => {
        const kobo = parseNairaToKobo(display);
        if (kobo !== null) {
            setDisplay((kobo / 100).toFixed(2));
            onChangeKobo(kobo);
        } else {
            setDisplay('');
            onChangeKobo(null);
        }
    };

    return (
        <div className="input-group">
            <span className="input-icon" style={{ top: '0.75rem', left: '0.75rem', color: 'var(--text)' }}>₦</span>
            <input
                type="text"
                id={id}
                inputMode="numeric"
                className="input with-icon"
                value={display}
                onChange={e => setDisplay(e.target.value)}
                onBlur={handleBlur}
                placeholder="0.00"
                required={required}
            />
        </div>
    );
}