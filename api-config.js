const backendProtocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
const backendHost = window.location.hostname || 'localhost';
const isLocalDevelopment = backendHost === 'localhost' || backendHost === '127.0.0.1';
window.BITEWISE_API_BASE_URL = isLocalDevelopment
    ? `${backendProtocol}//${backendHost}:8080`
    : window.location.origin;
