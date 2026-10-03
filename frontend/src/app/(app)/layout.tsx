import type { ReactNode } from "react";

import { AppShell } from "@/components/AppShell";
import { AuthProvider } from "@/components/AuthProvider";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AppShell>{children}</AppShell>
    </AuthProvider>
  );
}
