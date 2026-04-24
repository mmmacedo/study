import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Study App",
  description: "Monorepo de estudo — CI/CD, segurança e escalabilidade",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
