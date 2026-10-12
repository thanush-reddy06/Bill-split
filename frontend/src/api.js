export async function api(path, options = {}) {
 try {
  const headers = {...options.headers};
  if (options.method && options.method !== 'GET') {
   const tokenResponse = await fetch('/api/auth/csrf', {credentials: 'same-origin'});
   if (!tokenResponse.ok) throw new Error('Could not start a secure session. Please retry.');
   const csrf = await tokenResponse.json();
   headers[csrf.headerName] = csrf.token;
  }
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const response = await fetch('/api' + path, {...options, headers, credentials: 'same-origin'});
  if (!response.ok) {
   const body = await response.json().catch(() => ({}));
   if (response.status === 401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('session-expired'));
   const error = new Error(body.message || 'Request failed. Please try again.');
   error.status = response.status;
   throw error;
  }
  return response.status === 204 ? null : response.json();
 } catch (error) {
  if (error instanceof TypeError) throw new Error('Cannot reach the backend. Check that Spring Boot is running on port 8085.');
  throw error;
 }
}
