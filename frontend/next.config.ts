import type { NextConfig } from "next";

// The browser only talks to the frontend: API calls are proxied to the
// backend, so the session and CSRF cookies stay on a single origin.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
};

export default nextConfig;
