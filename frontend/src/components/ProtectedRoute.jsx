import { Navigate, Outlet } from 'react-router-dom';

export default function ProtectedRoute() {
    // LAYER 3: Route guard: require an access token before rendering protected child routes.
    const token = localStorage.getItem('accessToken');


    if (!token) {
        return <Navigate to="/login" replace />;
    }


    return <Outlet />;
}