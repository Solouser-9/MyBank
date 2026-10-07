import { useState, useEffect } from 'react';
import { Link, Navigate } from 'react-router-dom';
import {
    FiArrowRight, FiEyeOff, FiFileText, FiHash, FiKey, FiLink,
    FiPieChart, FiSend, FiShield, FiSmartphone, FiSun, FiMoon, FiUsers
} from 'react-icons/fi';
import { getTheme, toggleTheme } from '../utils/theme';
import { useScrollFade } from '../hooks/useScrollFade';









const FEATURES = [
    { icon: FiSend, title: 'Transfers, two ways',
        body: 'Instant MyBank-to-MyBank transfers, or payouts to other banks; every payout stays pending until the provider confirms, and rejected payments reverse automatically. (Bank network simulated in demo mode.)' },
    { icon: FiSmartphone, title: 'Bills without the queue',
        body: 'Airtime, data, electricity, TV and cable; every payment behind the OTP + transaction-PIN ceremony, and a rejected payment returns your money automatically. (Bill fulfillment simulated in demo mode.)' },
    { icon: FiUsers, title: 'Your own account number',
        body: 'Complete KYC and receive a personal account number. Deposits arrive through HMAC-signed webhooks, verified over the raw bytes and deduplicated, so no deposit can ever pay twice. (Live bank rails await provider activation.)' },
    { icon: FiPieChart, title: 'Insights, not guesswork',
        body: 'Balance trends, a month-by-month cash-flow bridge and a spending mix, computed from your actual receipts, never estimates.' },
    { icon: FiFileText, title: 'Statements on demand',
        body: 'Pick any date range and download a CSV statement with opening and closing balances derived from your actual transaction history.' },
    { icon: FiEyeOff, title: 'Privacy built in',
        body: 'Hide every balance with one tap, auto-blur when the window loses focus, and a traceability watermark on sensitive pages.' },
];


const PROOFS = [
    { icon: FiHash, title: 'Integer kobo, always',
        body: 'There is no floating point anywhere in the money path. \u20A61.00 is stored as 100 kobo; rounding errors are structurally impossible.' },
    { icon: FiLink, title: 'Double-entry ledger',
        body: 'Every credit is paired with a debit somewhere in the books. A watchdog reconciles every wallet against the ledger every five minutes.' },
    { icon: FiKey, title: 'One-time, single-use',
        body: 'Verification codes are hashed, purpose-tagged and atomically claimed. Sessions rotate on use; a replayed one is treated as theft.' },
    { icon: FiShield, title: 'Signed inbound money',
        body: 'Funding webhooks are verified with HMAC signatures over the raw bytes and deduplicated in an event inbox. Forged deposits cannot create balance.' },
];


const STEPS = [
    { title: 'Create your account',
        body: 'First name, email, phone (international format) and a strong password. Takes about a minute.' },
    { title: 'Verify with a code',
        body: 'A 6-digit one-time code confirms it\u2019s you, by email or printed to the server console in demo mode.' },
    { title: 'Set your transaction PIN',
        body: 'Choose 4\u20136 digits. This PIN is required every single time money moves.' },
    { title: 'Complete KYC',
        body: 'One-tap verification unlocks money movement and issues your personal account number.' },
    { title: 'Fund & explore',
        body: 'Top up your wallet; in demo mode, deposits arrive as signed webhook events. Then send, pay bills, and watch your Insights fill in.' },
];

export default function Landing() {

    const [theme, setTheme] = useState(getTheme());

    useEffect(() => {

        document.title = 'MyBank - Banking, down to the last kobo';
    }, []);




    const fadeRef = useScrollFade([]);



    if (localStorage.getItem('accessToken')) {
        return <Navigate to="/dashboard" replace />;
    }



    const flipTheme = (e) => setTheme(toggleTheme(e));


    return (
        <div className="landing" ref={fadeRef}>

            <nav className="landing-nav">
                <span className="landing-logo">MyBank</span>
                <div className="landing-nav-right">
                    <a href="#features" className="landing-navlink">Features</a>
                    <a href="#security" className="landing-navlink">Security</a>
                    <a href="#how" className="landing-navlink">How it works</a>
                    <button className="btn-icon" onClick={flipTheme} aria-label="Toggle theme">
                        {theme === 'dark' ? <FiSun /> : <FiMoon />}
                    </button>
                    <Link viewTransition to="/login" className="btn btn-outline btn-sm">Log in</Link>
                    <Link viewTransition to="/register" className="btn btn-primary btn-sm">Create account</Link>
                </div>
            </nav>


            <header className="landing-hero">
                <div>
                    <p className="landing-eyebrow">Digital banking · built from first principles</p>
                    <h1 className="landing-headline">Banking, down to <em>the last kobo.</em></h1>
                    <p className="landing-sub">
                        MyBank is a complete digital banking platform in your browser: instant
                        MyBank-to-MyBank transfers, bill payments, your own account number, insights
                        computed from your actual receipts, and a double-entry ledger that accounts
                        for every kobo.
                    </p>
                    <div className="landing-cta">
                        <Link viewTransition to="/register" className="btn btn-primary">
                            Create your account <FiArrowRight />
                        </Link>
                        <Link viewTransition to="/login" className="btn btn-outline">Log in</Link>
                    </div>
                    <p className="landing-demo-note">Demo mode · OTPs print to the server console · external bank network simulated</p>
                </div>


                <div className="landing-hero-card" aria-hidden="true">
                    <p className="landing-microlabel">Available balance</p>
                    <p className="landing-hero-balance">₦3,299.00</p>
                    <svg className="landing-spark" viewBox="0 0 300 70" preserveAspectRatio="none">
                        <defs>
                            <linearGradient id="landingSpark" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" className="grad-a" />
                                <stop offset="100%" className="grad-b" />
                            </linearGradient>
                        </defs>
                        <path d="M0,58 C40,54 70,50 100,44 C130,38 160,34 190,28 C220,22 260,16 300,12 L300,70 L0,70 Z"
                              fill="url(#landingSpark)" />
                        <path d="M0,58 C40,54 70,50 100,44 C130,38 160,34 190,28 C220,22 260,16 300,12"
                              className="chart-line" />
                    </svg>
                    <p className="landing-microlabel" style={{ marginTop: '1rem' }}>Recent activity</p>
                    <div className="landing-tx">
                        <span className="name">airtime: 0801•••9001</span>
                        <span className="amt" style={{ color: 'var(--debit)' }}>−₦100.00</span>
                    </div>
                    <div className="landing-tx">
                        <span className="name">User 46</span>
                        <span className="amt" style={{ color: 'var(--credit)' }}>+₦50.00</span>
                    </div>
                    <div className="landing-tx">
                        <span className="name">Mock User 1234•••7890</span>
                        <span className="amt" style={{ color: 'var(--debit)' }}>−₦550.00</span>
                    </div>
                    <p className="landing-watchman"><i></i>Watchman · books balanced · 0 alarms</p>
                </div>
            </header>


            <section className="landing-section" id="features" data-fade>
                <p className="landing-kicker">What you get</p>
                <h2 className="landing-section-title">A complete bank, in the browser.</h2>
                <p className="landing-section-sub">Every flow below is live in this build; only the external bank network is simulated in demo mode.</p>
                <div className="landing-grid">
                    {FEATURES.map(f => {
                        const Icon = f.icon;
                        return (
                            <div className="landing-feature" key={f.title}>
                                <Icon className="fi-icon" />
                                <h3>{f.title}</h3>
                                <p>{f.body}</p>
                            </div>
                        );
                    })}
                </div>
            </section>


            <section className="landing-band" id="security" data-fade>
                <div className="landing-section">
                    <p className="landing-kicker">Why the numbers can be trusted</p>
                    <h2 className="landing-section-title">Security as accounting, not decoration.</h2>
                    <div className="landing-proofs">
                        {PROOFS.map(p => {
                            const Icon = p.icon;
                            return (
                                <div className="landing-proof" key={p.title}>
                                    <Icon className="fi-icon" />
                                    <div>
                                        <h4>{p.title}</h4>
                                        <p>{p.body}</p>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            </section>


            <section className="landing-section" id="how" data-fade>
                <p className="landing-kicker">Getting started</p>
                <h2 className="landing-section-title">Five steps to your first transfer.</h2>
                <div className="landing-steps">
                    {STEPS.map((s, i) => (
                        <div className="landing-step" key={s.title}>
                            <p className="landing-step-num">0{i + 1}</p>
                            <h4>{s.title}</h4>
                            <p>{s.body}</p>
                        </div>
                    ))}
                </div>
            </section>


            <section className="landing-cta-band" data-fade>
                <h2>From signup to first transfer in minutes.</h2>
                <div className="landing-cta">
                    <Link viewTransition to="/register" className="btn btn-primary">
                        Create your account <FiArrowRight />
                    </Link>
                    <Link viewTransition to="/login" className="btn btn-outline">Log in</Link>
                </div>
            </section>


            <footer className="landing-footer">
                <div className="landing-footer-inner">
                    <div>
                        <p className="brand">MyBank</p>
                        <p className="landing-microlabel" style={{ marginTop: '0.3rem' }}>
                            Banking, down to the last kobo
                        </p>
                    </div>
                    <p className="landing-footer-meta">
                        Built by Bakare Abdussalam · NIIT Lagos
                    </p>
                </div>
                <p className="landing-disclaimer">
                    An educational full-stack project, not a licensed financial institution.
                    Demo data and test keys only; external bank rails are simulated.
                </p>
            </footer>
        </div>
    );
}