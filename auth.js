(function () {
    const API_BASE_URL = (window.BITEWISE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
    const ACCESS_TOKEN_KEY = 'bitewise.accessToken';
    const USER_KEY = 'bitewise.user';

    function getAccessToken() {
        return sessionStorage.getItem(ACCESS_TOKEN_KEY);
    }

    function saveSession(authResponse) {
        sessionStorage.setItem(ACCESS_TOKEN_KEY, authResponse.accessToken);
        sessionStorage.setItem(USER_KEY, JSON.stringify(authResponse.user));
    }

    function clearSession() {
        sessionStorage.removeItem(ACCESS_TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
    }

    async function responseError(response) {
        const body = await response.json().catch(() => null);
        return body?.detail || body?.message || `Request failed (HTTP ${response.status}).`;
    }

    async function refreshAccessToken() {
        const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                Accept: 'application/json',
                'X-Bitewise-Refresh': '1'
            }
        });
        if (!response.ok) {
            clearSession();
            return false;
        }
        saveSession(await response.json());
        return true;
    }

    async function request(path, options = {}, retryAfterRefresh = true) {
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');
        if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }
        const token = getAccessToken();
        if (token) {
            headers.set('Authorization', `Bearer ${token}`);
        }
        const response = await fetch(`${API_BASE_URL}${path}`, {
            ...options,
            headers,
            credentials: 'include'
        });

        if (response.status === 401 && retryAfterRefresh
                && !path.startsWith('/api/auth/')) {
            if (await refreshAccessToken()) {
                return request(path, options, false);
            }
        }
        return response;
    }

    async function requestJson(path, options) {
        const response = await request(path, options);
        if (!response.ok) {
            throw new Error(await responseError(response));
        }
        if (response.status === 204) {
            return null;
        }
        return response.json();
    }

    async function login(email, password) {
        const authResponse = await requestJson('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        saveSession(authResponse);
        return authResponse.user;
    }

    async function register(email, displayName, password) {
        return requestJson('/api/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, displayName, password })
        });
    }

    async function requireAuth() {
        if (!getAccessToken() && !(await refreshAccessToken())) {
            window.location.replace('signin.html');
            return null;
        }
        try {
            return await requestJson('/api/auth/me');
        } catch (error) {
            clearSession();
            window.location.replace('signin.html');
            return null;
        }
    }

    async function logout() {
        try {
            if (getAccessToken()) {
                await requestJson('/api/auth/logout', { method: 'POST' });
            }
        } finally {
            clearSession();
            window.location.replace('signin.html');
        }
    }

    window.BitewiseAuth = {
        apiBaseUrl: API_BASE_URL,
        login,
        logout,
        register,
        request,
        requestJson,
        requireAuth
    };
}());
