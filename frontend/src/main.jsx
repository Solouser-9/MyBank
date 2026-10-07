import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';
import ErrorBoundary from './components/ErrorBoundary';
import { applyTheme, getTheme } from './utils/theme';

// LAYER 3: Theme initialization: apply the saved appearance before mounting React to prevent a theme flash.
applyTheme(getTheme());


ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <ErrorBoundary>
            <App />
        </ErrorBoundary>
    </React.StrictMode>
);