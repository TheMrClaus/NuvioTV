import Link from "next/link";
import SourceCloudTvHandoffCard from "@/components/forms/SourceCloudTvHandoffCard";
import { redeemSourceCloudAdvancedSession } from "@/lib/actions/sourcecloud";

interface Props {
  params: Promise<{ token: string }>;
}

export const metadata = {
  title: "Source Cloud TV handoff",
};

export default async function SourceCloudTvHandoffPage({ params }: Props) {
  const { token } = await params;
  const session = await redeemSourceCloudAdvancedSession(token);

  if (!session) {
    return (
      <main className="mx-auto flex min-h-screen max-w-2xl items-center justify-center p-6">
        <section className="w-full rounded-2xl border border-slate-700/50 bg-slate-800/40 p-6">
          <h1 className="text-2xl font-semibold">This TV handoff link is unavailable</h1>
          <p className="mt-2 text-sm text-slate-400">
            It may have expired, been replaced by a newer QR, or belong to a different account. Generate a new QR from the TV and try again.
          </p>
          <div className="mt-4">
            <Link href="/profiles" className="text-primary hover:underline">
              Go to profiles
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl items-center justify-center p-6">
      <SourceCloudTvHandoffCard
        directConfigureUrl={session.directConfigureUrl}
        configurePassword={session.configurePassword}
        sourceCloudSettingsPath={session.sourceCloudSettingsPath}
      />
    </main>
  );
}
