const API_BASE = import.meta.env.VITE_API_URL || '';
// LAYER 3: UUID fallback: use crypto.randomUUID when available, with a fallback for non-secure LAN demos.

const uuid = () =>
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
            const r = (Math.random() * 16) | 0;
            return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
        });

let refreshing = null;


export const request = async (path, { method = 'GET', body, headers = {}, auth = true, blob = false } = {}) => {

    const doRequest = async () => {
        const reqHeaders = { ...headers };
        if (body && !reqHeaders['Content-Type']) {
            reqHeaders['Content-Type'] = 'application/json';
        }
        if (auth) {
            const token = localStorage.getItem('accessToken');
            if (token) reqHeaders['Authorization'] = `Bearer ${token}`;
        }

        const res = await fetch(`${API_BASE}/api${path}`, {
            method,
            headers: reqHeaders,
            body: body ? JSON.stringify(body) : undefined
        });


        if (res.status === 401 && auth) {
            throw new Error("401");
        }

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || "Something went wrong");
        }

        return blob ? await res.blob() : await res.json();
    };


    try {
        return await doRequest();
    } catch (err) {
        // LAYER 3: Silent token refresh: serialize concurrent refreshes, then retry the original request.
        if (err.message === "401") {
            if (!refreshing) {
                refreshing = (async () => {
                    try {
                        const rt = localStorage.getItem('refreshToken');
                        if (!rt) throw new Error("No refresh token");
                        const res = await fetch(`${API_BASE}/api/auth/refresh`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ refreshToken: rt })
                        });
                        if (!res.ok) throw new Error("Refresh failed");
                        const data = await res.json();
                        localStorage.setItem('accessToken', data.accessToken);
                        localStorage.setItem('refreshToken', data.refreshToken);
                    } catch (refreshErr) {
                        localStorage.removeItem('accessToken');
                        localStorage.removeItem('refreshToken');
                        window.location.href = '/login';
                        throw refreshErr;
                    } finally {
                        refreshing = null;
                    }
                })();
            }
            try {
                await refreshing;
            } catch (refreshErr) {
                throw new Error("Session expired; please log in again");
            }
            try {
                return await doRequest();
            } catch (retryErr) {
                if (retryErr.message === "401") {
                    throw new Error("Session expired; please log in again");
                }
                throw retryErr;
            }
        }
        throw err;
    }
};

// LAYER 3: Idempotency: attach the caller's stable key (or generate a new one) to money POST requests.

export const send = async (path, body, idempotencyKey = uuid()) => {
    return request(path, {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': idempotencyKey }
    });
};

export { uuid };
export default { request, send };