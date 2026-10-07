import { useState, useEffect } from 'react';
import { FiBell, FiCheck, FiEye, FiEyeOff } from 'react-icons/fi';
import api from '../api';
import { formatDate } from '../utils/format';
import { Spinner, ErrorBanner, EmptyState, Modal, useToast } from '../components/ui';
import { useScrollFade } from '../hooks/useScrollFade';
import { useBalanceHidden, useWindowBlur, usePrintScreenGuard } from '../hooks/usePrivacy';


const dayLabel = (isoString) => {

    const d = new Date(isoString);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    if (d.toDateString() === today.toDateString()) return 'Today';
    if (d.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return d.toLocaleDateString('en-NG', { day: '2-digit', month: 'short', year: 'numeric' });
};

export default function Notifications() {

    const [notifications, setNotifications] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');


    const [selected, setSelected] = useState(null);
    const { toast } = useToast();





    const [balanceHidden, toggleBalanceHidden] = useBalanceHidden();
    const windowBlurred = useWindowBlur();
    usePrintScreenGuard();
    const privacyHidden = balanceHidden || windowBlurred;




    const fadeRef = useScrollFade([notifications]);


    const fetchNotifications = () => {
        setLoading(true);
        api.request('/notifications')
            .then(setNotifications)
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    };

    useEffect(() => {

        fetchNotifications();
    }, []);


    const markAllRead = async () => {
        try {
            await api.request('/notifications/read', { method: 'POST' });
            setNotifications(notifications.map(n => ({ ...n, read: true })));
            window.dispatchEvent(new Event('notifications-read'));
            toast('success', 'All marked as read');
        } catch (err) {
            toast('error', err.message);
        }
    };






    const openNotification = (n) => {
        setSelected(n);
        if (!n.read) {
            api.request('/notifications/read-one', { method: 'POST', body: { id: n.id } })
                .then(() => {
                    setNotifications(prev => prev.map(x => (x.id === n.id ? { ...x, read: true } : x)));
                    setSelected(prev => (prev && prev.id === n.id ? { ...prev, read: true } : prev));
                    window.dispatchEvent(new Event('notifications-read'));
                })
                .catch(err => console.error('Mark-read failed:', err));
        }
    };


    if (loading && notifications.length === 0) return <Spinner />;
    if (error) return <ErrorBanner message={error} />;





    let lastDay = null;


    return (
        <div className="notifications-page" data-user={localStorage.getItem('wm-user') || ''} ref={fadeRef}>
            <div className="flex-between mb-4">
                <h1 className="page-title">Notifications</h1>
                <div className="flex-align">
                    <button className="btn-icon" onClick={toggleBalanceHidden}
                            aria-label={balanceHidden ? 'Show messages' : 'Hide messages'}>
                        {balanceHidden ? <FiEye /> : <FiEyeOff />}
                    </button>
                    {notifications.length > 0 && notifications.some(n => !n.read) && (
                        <button className="btn btn-outline btn-sm" onClick={markAllRead}>
                            <FiCheck /> Mark all read
                        </button>
                    )}
                </div>
            </div>


            {notifications.length === 0 ? (
                <EmptyState icon={FiBell} message="You have no notifications" />
            ) : (
                <div className="card p-0" data-fade>
                    <div className={`notification-list ${privacyHidden ? 'privacy-blur' : ''}`}>
                        {notifications.map(n => {
                            const isNewDay = new Date(n.createdAt).toDateString() !== lastDay;
                            lastDay = new Date(n.createdAt).toDateString();
                            return (
                                <div key={n.id}>
                                    {isNewDay && (
                                        <div className="notif-day-header">
                                            <span>{dayLabel(n.createdAt)}</span>
                                        </div>
                                    )}
                                    <div className={`notification-item clickable ${!n.read ? 'unread' : ''}`}
                                         onClick={() => openNotification(n)}
                                         onKeyDown={e => {
                                             if (e.key === 'Enter' || e.key === ' ') {
                                                 e.preventDefault();
                                                 openNotification(n);
                                             }
                                         }}
                                         tabIndex={0}
                                         role="button"
                                         aria-label={`View notification: ${n.message}`}>
                                        <div className="notif-content">
                                            <p className="notif-message">{n.message}</p>
                                            <p className="text-xs text-muted mt-1">{formatDate(n.createdAt)}</p>
                                        </div>
                                        {!n.read && <div className="unread-dot"></div>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}


            <Modal open={!!selected} title="Notification" onClose={() => setSelected(null)}>
                {selected && (
                    <>
                        <p className="notif-message-full">{selected.message}</p>
                        <div className="detail-list">
                            <div className="detail-row">
                                <span>Received</span>
                                <strong>{formatDate(selected.createdAt)}</strong>
                            </div>
                            <div className="detail-row">
                                <span>Status</span>
                                <strong>{selected.read ? 'Read' : 'Unread'}</strong>
                            </div>
                        </div>
                    </>
                )}
            </Modal>
        </div>
    );
}