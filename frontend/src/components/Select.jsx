import { useEffect, useRef, useState } from 'react';








export default function Select({ value, onChange, options, placeholder = 'Select…', className = '', disabled = false, ariaLabel }) {

    const [open, setOpen] = useState(false);
    const ref = useRef(null);

    useEffect(() => {

        const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
        const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
        document.addEventListener('mousedown', close);
        document.addEventListener('keydown', esc);
        return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc); };
    }, []);


    const selected = options.find((o) => o.value === value);


    return (
        <div className={`select-wrap ${disabled ? 'disabled' : ''} ${className}`} ref={ref}>
            <button type="button"
                    className={`select-trigger ${selected ? '' : 'is-placeholder'}`}
                    disabled={disabled} aria-haspopup="listbox" aria-expanded={open}
                    aria-label={ariaLabel}
                    onClick={() => setOpen((o) => !o)}>
                <span>{selected ? selected.label : placeholder}</span>
                <svg className={`select-chev ${open ? 'open' : ''}`} width="10" height="6" viewBox="0 0 10 6" fill="none">
                    <path d="M1 1l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
            </button>
            <ul className={`select-menu ${open ? 'open' : ''}`} role="listbox" aria-hidden={!open}>
                {options.map((o) => (
                    <li key={o.value || '__placeholder'}>
                        <button type="button" role="option" aria-selected={o.value === value}
                                className={`select-option ${o.value === value ? 'is-selected' : ''} ${o.value === '' ? 'is-placeholder' : ''}`}
                                onClick={() => { onChange(o.value); setOpen(false); }}>
                            {o.label}
                        </button>
                    </li>
                ))}
            </ul>
        </div>
    );
}