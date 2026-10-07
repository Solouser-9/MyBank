import React from 'react';



const RULES = [
    { label: 'At least 8 characters', test: (p) => p.length >= 8 },
    { label: 'An uppercase letter',   test: (p) => /[A-Z]/.test(p) },
    { label: 'A lowercase letter',    test: (p) => /[a-z]/.test(p) },
    { label: 'A number',              test: (p) => /[0-9]/.test(p) },
];

export default function PasswordStrength({ password }) {

    return (
        <ul style={{ listStyle: 'none', padding: 0, margin: '8px 0 0', fontSize: '0.85rem' }}>
            {RULES.map((r) => {
                const ok = r.test(password || '');
                return (
                    <li key={r.label} style={{ color: ok ? '#2e9e5b' : '#9a9a9a' }}>
                        {ok ? '✓' : '○'} {r.label}
                    </li>
                );
            })}
        </ul>
    );
}