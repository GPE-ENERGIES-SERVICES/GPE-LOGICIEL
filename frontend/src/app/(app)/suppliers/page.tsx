"use client";

import { useAuth } from "@/components/AuthProvider";
import { SuppliersPanel } from "@/components/settings/SuppliersPanel";

/** The suppliers and their price files, used when estimating: for administrators. */
export default function SuppliersPage() {
  const { user } = useAuth();

  if (user.role !== "ADMIN") {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  return (
    <div>
      <h1>Tarifs fournisseurs</h1>
      <section className="card">
        <SuppliersPanel />
      </section>
    </div>
  );
}
