import { useState, useEffect, useRef } from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import { FiBell, FiHome, FiSend, FiFileText, FiUsers, FiSettings, FiLogOut, FiMenu, FiX, FiSun, FiMoon, FiTrendingUp } from 'react-icons/fi';
import api from '../api';
import { useToast } from './ui';
import { getTheme, applyTheme, toggleTheme } from '../utils/theme';
import { navigateWithFade } from '../utils/motion';

export default function Layout() {

    const navigate = useNavigate();
    const location = useLocation();
    const { toast } = useToast();

    const [menuOpen, setMenuOpen] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [showBellDropdown, setShowBellDropdown] = useState(false);
    const [recentNotifs, setRecentNotifs] = useState([]);
    const dropdownRef = useRef(null);
    const [theme, setTheme] = useState(getTheme());


    const fetchUnread = async () => {
        try {
            const data = await api.request('/notifications?unreadOnly=true');
            setUnreadCount(data.length);
            setRecentNotifs(data.slice(0, 5));
        } catch (err) {
            console.error("Failed to fetch notifications", err);
        }
    };


    useEffect(() => {
        fetchUnread();
    }, [location.pathname]);


    useEffect(() => {
        setMenuOpen(false);
    }, [location.pathname]);

    useEffect(() => {
        document.documentElement.setAttribute('data-drawer', menuOpen ? 'open' : 'closed');
        if (!menuOpen) return;

        const close = (e) => {
            if (e.target.closest && e.target.closest('.mobile-only')) return;
            setMenuOpen(false);
        };
        const esc = (e) => { if (e.key === 'Escape') setMenuOpen(false); };

        const mc = document.querySelector('.main-content');
        mc?.addEventListener('click', close);
        document.addEventListener('keydown', esc);
        return () => {
            mc?.removeEventListener('click', close);
            document.removeEventListener('keydown', esc);
        };
    }, [menuOpen]);

    useEffect(() => {
        // LAYER 3: Notification synchronization: refresh the bell when another page marks notifications read.
        const handleNotifRead = () => fetchUnread();
        window.addEventListener('notifications-read', handleNotifRead);
        return () => window.removeEventListener('notifications-read', handleNotifRead);
    }, []);

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setShowBellDropdown(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);



    const flipTheme = (e) => {
        const next = toggleTheme(e);
        setTheme(next);
    };


    const handleLogout = async () => {
        try {
            const rt = localStorage.getItem('refreshToken');
            if (rt) {
                await api.request('/auth/logout', { method: 'POST', body: { refreshToken: rt } });
            }
        } catch (e) {
            console.error(e);
        } finally {
            // LAYER 3: Secure logout: remove authentication tokens without deleting saved preferences.
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            navigate('/login', { viewTransition: true });
        }
    };


    const markAllRead = async () => {
        try {
            await api.request('/notifications/read', { method: 'POST' });
            setUnreadCount(0);
            setRecentNotifs([]);
            setShowBellDropdown(false);
            window.dispatchEvent(new Event('notifications-read'));
            toast('success', 'All marked as read');
        } catch (err) {
            toast('error', err.message);
        }
    };


    const navLinks = [
        { path: '/dashboard', icon: FiHome, label: 'Dashboard' },
        { path: '/send', icon: FiSend, label: 'Send Money' },
        { path: '/bills', icon: FiFileText, label: 'Pay Bills' },
        { path: '/history', icon: FiFileText, label: 'History' },
        { path: '/insights', icon: FiTrendingUp, label: 'Insights' },
        { path: '/statements', icon: FiFileText, label: 'Statements' },
        { path: '/beneficiaries', icon: FiUsers, label: 'Beneficiaries', fadeNavigate: true },
        { path: '/settings', icon: FiSettings, label: 'Settings' }
    ];

    const activeLink = navLinks.find(l => location.pathname.startsWith(l.path));
    const activeLabel = activeLink ? activeLink.label : 'MyBank';

    useEffect(() => {
        document.title = activeLabel === 'MyBank' ? 'MyBank' : `${activeLabel} - MyBank`;
    }, [activeLabel]);


    return (
        <div className="layout">

            <aside className={`sidebar ${menuOpen ? 'open' : ''}`}>
                <div className="sidebar-header">
                    <h2>MyBank</h2>
                    <button className="btn-icon mobile-only" onClick={() => setMenuOpen(false)} aria-label="Close menu">
                        <FiX />
                    </button>
                </div>
                <nav className="sidebar-nav">
                    {navLinks.map(link => {
                        const Icon = link.icon;
                        const active = location.pathname.startsWith(link.path);
                        return link.fadeNavigate ? (
                            <button key={link.path}
                                    className={`nav-item ${active ? 'active' : ''}`}
                                    onClick={(e) => {
                                        e.preventDefault();
                                        setMenuOpen(false);
                                        navigateWithFade(navigate, link.path);
                                    }}>
                                <Icon className="nav-icon" />
                                <span>{link.label}</span>
                            </button>
                        ) : (
                            <Link key={link.path} to={link.path} viewTransition className={`nav-item ${active ? 'active' : ''}`} onClick={() => setMenuOpen(false)}>
                                <Icon className="nav-icon" />
                                <span>{link.label}</span>
                            </Link>
                        );
                    })}
                </nav>
                <div className="sidebar-footer">
                    <button className="nav-item text-danger" onClick={handleLogout}>
                        <FiLogOut className="nav-icon" />
                        <span>Logout</span>
                    </button>
                </div>
            </aside>


            <main className="main-content">
                <header className="topbar">
                    <button className="btn-icon mobile-only" onClick={() => setMenuOpen(o => !o)} aria-label="Open menu">
                        <FiMenu />
                    </button>
                    <h3 className="topbar-title">{activeLabel}</h3>
                    <div className="topbar-spacer"></div>
                    <button className="btn-icon" onClick={flipTheme} aria-label="Toggle theme">
                        {theme === 'dark' ? <FiSun /> : <FiMoon />}
                    </button>

                    <div className="bell-container" ref={dropdownRef}>
                        <button className="btn-icon" onClick={() => setShowBellDropdown(!showBellDropdown)} aria-label="Notifications">
                            <FiBell />
                            {unreadCount > 0 && <span className="badge-count">{unreadCount}</span>}
                        </button>
                        <div className={`bell-dropdown card ${showBellDropdown ? 'open' : ''}`}
                             aria-hidden={!showBellDropdown}>
                                <div className="bell-dropdown-header">
                                    <h4>Notifications</h4>
                                    {unreadCount > 0 && (
                                        <button className="btn-link text-sm" onClick={markAllRead}>Mark all read</button>
                                    )}
                                </div>
                                <div className="bell-dropdown-body">
                                    {recentNotifs.length === 0 ? (
                                        <p className="text-muted text-sm text-center py-2">No new notifications</p>
                                    ) : (
                                        recentNotifs.map(n => (
                                            <div key={n.id} className="bell-item">
                                                <p className="text-sm">{n.message}</p>
                                            </div>
                                        ))
                                    )}
                                </div>
                                <div className="bell-dropdown-footer">
                                    <Link viewTransition to="/notifications" className="btn-link text-sm text-center block" onClick={() => setShowBellDropdown(false)}>
                                        View all
                                    </Link>
                                </div>
                            </div>
                    </div>
                </header>

                <div className="page-container">
                    {/* LAYER 3: Child route rendering: keyed Outlet content remounts to replay the page transition. */}
                    <div key={location.pathname} className="page-enter">
                        <Outlet />
                    </div>
                </div>
            </main>
        </div>
    );
}