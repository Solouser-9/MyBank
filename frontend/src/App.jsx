import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './components/ui';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import ScrollToTop from './components/ScrollToTop';
import Landing from './pages/Landing';
import Register from './pages/Register';
import Verify from './pages/Verify';
import Login from './pages/Login';
import SetPin from './pages/SetPin';
import Dashboard from './pages/Dashboard';
import History from './pages/History';
import Statements from './pages/Statements';
import Notifications from './pages/Notifications';
import Send from './pages/Send';
import Bills from './pages/Bills';
import Beneficiaries from './pages/Beneficiaries';
import Insights from './pages/Insights';
import Settings from './pages/Settings';
import NotFound from './pages/NotFound';

export default function App() {

    return (
        <ToastProvider>
            <Router>
                <ScrollToTop />
                <Routes>

                    <Route path="/" element={<Landing />} />
                    <Route path="/register" element={<Register />} />
                    <Route path="/verify" element={<Verify />} />
                    <Route path="/login" element={<Login />} />

                    {/* LAYER 3: Protected route tree: authentication gates PIN setup and all private pages. */}
                    <Route element={<ProtectedRoute />}>

                        <Route path="/set-pin" element={<SetPin />} />

                        <Route element={<Layout />}>
                            <Route path="/dashboard" element={<Dashboard />} />
                            <Route path="/history" element={<History />} />
                            <Route path="/statements" element={<Statements />} />
                            <Route path="/notifications" element={<Notifications />} />
                            <Route path="/send" element={<Send />} />
                            <Route path="/bills" element={<Bills />} />
                            <Route path="/beneficiaries" element={<Beneficiaries />} />
                            <Route path="/insights" element={<Insights />} />
                            <Route path="/settings" element={<Settings />} />
                        </Route>
                    </Route>


                    <Route path="*" element={<NotFound />} />
                </Routes>
            </Router>
        </ToastProvider>
    );
}