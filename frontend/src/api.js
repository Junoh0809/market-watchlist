export function createApiClient(baseUrl, fetchImpl = fetch) {
  return {
    async request(path, { token, body, headers, ...options } = {}) {
      const response = await fetchImpl(`${baseUrl}${path}`, {
        ...options,
        headers: {
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...headers,
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      if (response.status === 401 && token) {
        const error = new Error('세션이 만료되었습니다. 다시 로그인해주세요.');
        error.unauthorized = true;
        throw error;
      }
      if (!response.ok) {
        let message = `요청에 실패했습니다. (HTTP ${response.status})`;
        const raw = await response.text();
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            message = parsed.error?.message || parsed.message || message;
          } catch {
            // Keep a short, safe fallback for non-JSON server errors.
          }
        }
        throw new Error(message);
      }
      if (response.status === 204) return null;
      const raw = await response.text();
      return raw ? JSON.parse(raw) : null;
    },
  };
}
