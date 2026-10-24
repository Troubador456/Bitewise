const backendProtocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
const backendHost = window.location.hostname || 'localhost';
window.BITEWISE_API_BASE_URL = `${backendProtocol}//${backendHost}:8080`;
