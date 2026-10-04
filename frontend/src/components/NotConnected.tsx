/** A section whose data the backend does not provide yet, see available() in services/http.ts. */
export function NotConnected({ title }: { title: string }) {
  return (
    <>
      <h1>{title}</h1>
      <p className="page-message">
        Cette rubrique n&apos;est pas encore reliée au serveur. Elle est disponible avec les données de démonstration.
      </p>
    </>
  );
}
