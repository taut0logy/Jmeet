/** @type {import('next').NextConfig} */
// jmeet's Spring Boot `meet-web` process, not the old Node api (port 4000).
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8080';

const nextConfig = {
  // Proxies the browser's same-origin /api/* calls to the backend, so the
  // session cookie stays first-party (no CORS, no SameSite=None).
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${API_ORIGIN}/api/:path*` }];
  },
};

export default nextConfig;
