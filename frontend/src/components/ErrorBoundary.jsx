import { Component } from 'react';







export default class ErrorBoundary extends Component {

    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }


    static getDerivedStateFromError() {
        return { hasError: true };
    }


    componentDidCatch(error, errorInfo) {

        console.error('Unhandled render error:', error, errorInfo);
    }


    render() {
        if (!this.state.hasError) return this.props.children;

        return (
            <div className="error-page">
                <div className="card error-card">
                    <div className="error-icon">⚠</div>
                    <h1 className="error-title">Something went wrong</h1>
                    <p className="text-sm text-muted">
                        An unexpected error occurred. Reloading usually fixes it;
                        your session and saved data are safe.
                    </p>
                    <button className="btn btn-primary error-btn" onClick={() => window.location.reload()}>
                        Reload Page
                    </button>
                </div>
            </div>
        );
    }
}