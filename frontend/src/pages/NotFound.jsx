import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { FiAlertTriangle } from 'react-icons/fi';

export default function NotFound() {

    useEffect(() => {
        document.title = "Page Not Found - MyBank";
    }, []);


    return (
        <div className="app-container" style={{ alignItems: 'center', justifyContent: 'center' }}>
            <div className="card text-center" style={{ maxWidth: '400px', width: '100%' }}>
                <FiAlertTriangle style={{ fontSize: '3rem', color: 'var(--danger)', marginBottom: '1rem' }} />
                <h2 className="card-title">Page Not Found</h2>
                <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
                    The page you are looking for doesn't exist or has been moved.
                </p>

                <Link viewTransition to="/" className="btn btn-primary">
                    Back to home
                </Link>
            </div>
        </div>
    );
}