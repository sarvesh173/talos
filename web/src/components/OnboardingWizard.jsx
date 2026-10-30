import React, { useCallback, useEffect, useState } from 'react';
import {
  ArrowLeft,
  Bot,
  CheckCircle2,
  KeyRound,
  Loader2,
  Shield,
  ShieldCheck,
  Terminal,
  Users,
  Wifi,
  WifiOff,
} from 'lucide-react';

const WIZARD_BG = '#141218';
const WIZARD_SURFACE = '#211F26';
const WIZARD_SURFACE_ALT = '#2B2930';
const WIZARD_BORDER = 'rgba(208, 188, 255, 0.16)';
const WIZARD_TEXT = '#E6E0E9';
const WIZARD_MUTED = '#CAC4D0';
const WIZARD_DANGER = '#F2B8B5';
const INDIGO = '#D0BCFF';
const STATUS_GREEN = '#10B981';

const PASTEL_MINT = 'rgba(16, 185, 129, 0.22)';
const PASTEL_LAVENDER = 'rgba(208, 188, 255, 0.22)';
const PASTEL_SKY = 'rgba(56, 189, 248, 0.22)';
const PASTEL_PEACH = 'rgba(239, 184, 200, 0.22)';

const MESH_IP = typeof window !== 'undefined' ? window.location.hostname : 'mesh-node.local';
const PEER_LABEL = 'TALOS-Node';

const STEPS = 4;

/* ------------------------------------------------------------------ */
/* Small presentational helpers                                        */
/* ------------------------------------------------------------------ */

function PastelBadge({ tint, children }) {
  return (
    <div
      className="w-[52px] h-[52px] shrink-0 rounded-2xl flex items-center justify-center"
      style={{ background: tint }}
    >
      {children}
    </div>
  );
}

function StepHeading({ title, subtitle }) {
  return (
    <div className="pt-4">
      <h1 className="text-[27px] leading-[33px] font-extrabold" style={{ color: WIZARD_TEXT }}>
        {title}
      </h1>
      <p className="mt-2 text-sm leading-5" style={{ color: WIZARD_MUTED }}>
        {subtitle}
      </p>
    </div>
  );
}

function Card({ children, className = '' }) {
  return (
    <div
      className={`rounded-[26px] m3-expressive-border m3-card-glass p-4 ${className}`}
    >
      {children}
    </div>
  );
}

function ErrorNote({ children }) {
  return (
    <div className="mt-3.5 flex items-start gap-2">
      <span
        className="mt-[5px] w-[14px] h-[14px] rounded-full shrink-0"
        style={{ background: WIZARD_DANGER }}
        aria-hidden="true"
      />
      <p className="text-xs leading-[17px]" style={{ color: WIZARD_DANGER }}>
        {children}
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Step 1 — Overview                                                    */
/* ------------------------------------------------------------------ */

function OverviewCard({ index, title, body, badgeTint, icon: Icon }) {
  return (
    <div
      className="flex items-center gap-3.5 rounded-[20px] p-4 bg-[rgba(43,41,48,0.7)] border border-[rgba(208,188,255,0.14)]"
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2.5">
          <span
            className="w-6 h-6 shrink-0 rounded-full flex items-center justify-center text-xs font-bold bg-[#D0BCFF] text-[#381E72]"
          >
            {index}
          </span>
          <span className="text-sm font-semibold leading-[19px] text-[#E6E0E9]">
            {title}
          </span>
        </div>
        <p className="mt-1.5 text-xs leading-[17px] text-[#CAC4D0]">
          {body}
        </p>
      </div>

      <div
        className="w-[52px] h-[52px] shrink-0 rounded-[16px] flex items-center justify-center border border-[rgba(208,188,255,0.25)]"
        style={{ background: badgeTint }}
      >
        <Icon className="w-[26px] h-[26px] text-[#D0BCFF]" strokeWidth={1.9} />
      </div>
    </div>
  );
}

function OverviewStep() {
  return (
    <>
      <StepHeading
        title="Initialize Your TALOS Mesh"
        subtitle="A single plan for the entire agent suite"
      />

      <div className="mt-6">
        <Card className="space-y-3">
          <OverviewCard
            index={1}
            title="Connect over Tailscale WireGuard"
            body="Private overlay network. No public ports, no exposed surface."
            badgeTint={PASTEL_MINT}
            icon={ShieldCheck}
          />
          <OverviewCard
            index={2}
            title="Pair with Autonomous Agent Fleet"
            body="One handshake brings every always-on agent into the mesh."
            badgeTint={PASTEL_LAVENDER}
            icon={Users}
          />
          <OverviewCard
            index={3}
            title="Real-Time Collaboration & Tool Bus"
            body="Shared event bus, tool calls and presence in a single stream."
            badgeTint={PASTEL_SKY}
            icon={Terminal}
          />
        </Card>
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Step 2 — Tailscale mesh verification                                 */
/* ------------------------------------------------------------------ */

function MeshStatusRing({ active, checking, label }) {
  const ringColor = checking ? INDIGO : active ? STATUS_GREEN : WIZARD_DANGER;
  const Icon = active ? Wifi : checking ? Loader2 : WifiOff;

  return (
    <div className="relative w-[110px] h-[110px] flex items-center justify-center">
      <span
        className="absolute inset-0 rounded-full animate-pulse"
        style={{ background: ringColor, opacity: 0.1 }}
        aria-hidden="true"
      />
      <span
        className="absolute inset-[9px] rounded-full border-[5px]"
        style={{ borderColor: ringColor }}
        aria-hidden="true"
      />
      <span
        className="absolute inset-[9px] rounded-full border-[8px] border-dashed animate-spin"
        style={{
          borderColor: ringColor,
          opacity: 0.7,
          animationDuration: checking ? '1.4s' : '4s',
        }}
        aria-hidden="true"
      />
      <div className="relative flex flex-col items-center gap-1">
        <Icon
          className={`w-6 h-6 ${checking ? 'animate-spin' : ''}`}
          style={{ color: ringColor }}
          strokeWidth={2}
        />
        <span className="text-xs font-semibold" style={{ color: WIZARD_TEXT }}>
          {label}
        </span>
      </div>
    </div>
  );
}

function MeshRow({ label, value, last = false }) {
  return (
    <div>
      <div className="flex items-center py-[11px]">
        <span className="flex-1 text-[13px]" style={{ color: WIZARD_MUTED }}>
          {label}
        </span>
        <span
          className="text-[13px] font-medium"
          style={{ color: WIZARD_TEXT }}
        >
          {value}
        </span>
      </div>
      {!last && <div className="h-px w-full" style={{ background: WIZARD_BORDER }} />}
    </div>
  );
}

function MeshStep({ meshInfo, isChecking, errorMessage }) {
  const active = meshInfo?.active === true;

  return (
    <>
      <StepHeading
        title="Tailscale Mesh Verification"
        subtitle="Confirm the overlay link before minting an agent token."
      />

      <div className="mt-6">
        <Card className="p-[18px]">
          <div className="h-[120px] flex items-center justify-center">
            <MeshStatusRing
              active={active}
              checking={isChecking}
              label={isChecking ? 'Scanning' : active ? 'Linked' : 'Offline'}
            />
          </div>

          <div className="mt-[18px]">
            <MeshRow label="Node" value={meshInfo?.hostname || 'talos-node'} />
            <MeshRow label="Overlay IP" value={meshInfo?.self_ip || MESH_IP} />
            <MeshRow label="Peer" value={meshInfo?.peer_name || PEER_LABEL} />
            <MeshRow
              label="Peers online"
              value={meshInfo?.peers_count > 0 ? String(meshInfo.peers_count) : '1'}
            />
            <MeshRow label="Transport" value="WireGuard (UDP)" last />
          </div>
        </Card>
      </div>

      {errorMessage ? (
        <ErrorNote>{errorMessage}</ErrorNote>
      ) : !active && !isChecking ? (
        <ErrorNote>Mesh unreachable. Verify Tailscale is running on both ends.</ErrorNote>
      ) : null}
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Step 3 — Agent API token                                            */
/* ------------------------------------------------------------------ */

function TokenStep({ token, isPairing, errorMessage }) {
  return (
    <>
      <StepHeading
        title="Generate Agent API Token"
        subtitle="This key authorises the app to reach the agent bus."
      />

      <div className="mt-6">
        <Card className="p-[18px]">
          <div className="flex items-center gap-3.5">
            <div
              className="w-[46px] h-[46px] shrink-0 rounded-[14px] flex items-center justify-center"
              style={{ background: PASTEL_PEACH }}
            >
              <KeyRound className="w-[24px] h-[24px]" style={{ color: WIZARD_BG }} strokeWidth={1.9} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-semibold" style={{ color: WIZARD_TEXT }}>
                Device token
              </div>
              <div className="mt-0.5 text-xs" style={{ color: WIZARD_MUTED }}>
                {token ? 'Generated and stored locally' : 'Awaiting generation'}
              </div>
            </div>
          </div>

          <div
            className="mt-4 rounded-[14px] border p-3.5"
            style={{ background: '#0B0E15', borderColor: WIZARD_BORDER }}
          >
            {isPairing ? (
              <div>
                <div
                  className="h-[3px] w-full rounded-full overflow-hidden"
                  style={{ background: WIZARD_BORDER }}
                >
                  <div
                    className="h-full w-1/3 rounded-full animate-pulse"
                    style={{ background: INDIGO }}
                  />
                </div>
                <p className="mt-3 text-xs" style={{ color: WIZARD_MUTED }}>
                  Minting token on the mesh node...
                </p>
              </div>
            ) : token ? (
              <p
                className="text-[13px] leading-5 font-medium break-all"
                style={{ color: PASTEL_MINT }}
              >
                {token.chunk(8).join(' ')}
              </p>
            ) : (
              <p className="text-xs" style={{ color: WIZARD_MUTED }}>
                Token will appear here once generated.
              </p>
            )}
          </div>

          {token && (
            <div className="mt-3.5 flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 shrink-0" style={{ color: STATUS_GREEN }} />
              <p className="text-xs" style={{ color: WIZARD_MUTED }}>
                Key is bound to this device. Keep it private.
              </p>
            </div>
          )}
        </Card>
      </div>

      {errorMessage && <ErrorNote>{errorMessage}</ErrorNote>}
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Step 4 — Welcome                                                     */
/* ------------------------------------------------------------------ */

const CONNECTED_AGENTS = [
  { name: 'Hermes', role: 'Reasoning & synthesis', tint: PASTEL_PEACH },
  { name: 'OmniRush', role: 'Fast execution', tint: PASTEL_LAVENDER },
  { name: 'Council', role: 'Multi-agent debate', tint: PASTEL_SKY },
];

function ConnectedAgentRow({ name, role, tint }) {
  return (
    <div
      className="flex items-center gap-3.5 rounded-2xl p-3.5"
      style={{ background: WIZARD_SURFACE_ALT }}
    >
      <div
        className="w-[46px] h-[46px] shrink-0 rounded-full flex items-center justify-center"
        style={{ background: tint }}
      >
        <Bot className="w-[24px] h-[24px]" style={{ color: WIZARD_BG }} strokeWidth={1.9} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold" style={{ color: WIZARD_TEXT }}>
          {name}
        </div>
        <div className="mt-0.5 text-xs" style={{ color: WIZARD_MUTED }}>
          {role}
        </div>
      </div>
      <CheckCircle2 className="w-[18px] h-[18px] shrink-0" style={{ color: STATUS_GREEN }} />
    </div>
  );
}

function WelcomeStep({ meshInfo }) {
  const ip = meshInfo?.self_ip || MESH_IP;
  const agentCount = CONNECTED_AGENTS.length;

  return (
    <>
      <StepHeading
        title="Welcome to the Chamber"
        subtitle="Your mesh is live. These agents are already on the bus."
      />

      <div className="mt-6">
        <Card className="space-y-3">
          {CONNECTED_AGENTS.map((agent) => (
            <ConnectedAgentRow key={agent.name} {...agent} />
          ))}
        </Card>
      </div>

      <div className="mt-[18px] flex items-center gap-2">
        <span
          className="w-2 h-2 rounded-full shrink-0"
          style={{ background: STATUS_GREEN }}
          aria-hidden="true"
        />
        <p className="text-xs" style={{ color: WIZARD_MUTED }}>
          Mesh live at {ip} · {agentCount} agents connected
        </p>
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Wizard shell                                                        */
/* ------------------------------------------------------------------ */

export default function OnboardingWizard({ serverUrl, onComplete }) {
  const [step, setStep] = useState(0);
  const [meshInfo, setMeshInfo] = useState(null);
  const [isChecking, setIsChecking] = useState(false);
  const [token, setToken] = useState(null);
  const [isPairing, setIsPairing] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const baseUrl = (serverUrl || '').replace(/\/$/, '');

  const checkMesh = useCallback(async () => {
    setIsChecking(true);
    setErrorMessage(null);
    try {
      const res = await fetch(`${baseUrl}/api/mesh/status`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setMeshInfo({
        active: Boolean(data.active),
        hostname: data.hostname || '',
        self_ip: data.self_ip || '',
        peers_count: data.peers_count || 0,
        peer_name: data.peers?.[0]?.hostname || '',
      });
    } catch (err) {
      setMeshInfo(null);
      setErrorMessage('Mesh unreachable. Verify Tailscale is running on both ends.');
    } finally {
      setIsChecking(false);
    }
  }, [baseUrl]);

  const pairDevice = useCallback(async () => {
    setIsPairing(true);
    setErrorMessage(null);
    try {
      const res = await fetch(`${baseUrl}/api/auth/pair`, { method: 'POST' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      if (!data.token) throw new Error('empty token');
      setToken(data.token);
    } catch (err) {
      setToken(null);
      setErrorMessage('Could not mint a token. Verify the Tailscale mesh is reachable.');
    } finally {
      setIsPairing(false);
    }
  }, [baseUrl]);

  useEffect(() => {
    if (step === 1) checkMesh();
    if (step === 2 && !token && !isPairing) pairDevice();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step]);

  const handleBack = () => {
    if (step === 2) {
      setToken(null);
      setErrorMessage(null);
    }
    if (step > 0) setStep((s) => s - 1);
  };

  const handlePrimary = () => {
    switch (step) {
      case 0:
        setStep(1);
        break;
      case 1:
        if (meshInfo?.active === true) {
          setStep(2);
        } else {
          setErrorMessage('Mesh not verified. Confirm Tailscale is connected.');
        }
        break;
      case 2:
        if (token) {
          setStep(3);
        } else {
          setErrorMessage('Token is not ready yet.');
        }
        break;
      default:
        onComplete?.({ token, serverUrl: baseUrl, meshInfo });
    }
  };

  const busy = isChecking || isPairing;
  const primaryLabel =
    step === 0
      ? 'Get started'
      : step === 1
        ? 'Confirm Mesh & Continue'
        : step === 2
          ? 'Activate Token & Link'
          : 'Enter Chamber';
  const busyLabel = step === 1 ? 'Verifying Mesh...' : step === 2 ? 'Minting Token...' : primaryLabel;

  return (
    <div
      className="flex flex-col h-full min-h-screen m3-purple-atmosphere relative"
      style={{ color: WIZARD_TEXT }}
    >
      {/* Top bar: back + step counter + segmented progress rail */}
      <div className="w-full px-3 py-2 relative z-10">
        <div className="flex items-center">
          <div className="w-10 h-10 flex items-center justify-center shrink-0">
            {step > 0 && (
              <button
                onClick={handleBack}
                aria-label="Back"
                className="w-10 h-10 flex items-center justify-center rounded-full transition hover:bg-white/10"
                style={{ color: WIZARD_TEXT }}
              >
                <ArrowLeft className="w-5 h-5" />
              </button>
            )}
          </div>

          <div className="flex-1" />

          <span
            className="text-xs font-medium tracking-[0.6px] text-[#D0BCFF]/80"
          >
            Step {step + 1} of {STEPS}
          </span>

          <div className="flex-1" />

          <div className="w-10 h-10 shrink-0" />
        </div>

        <div className="flex gap-1.5 w-full mt-2.5 px-1.5">
          {Array.from({ length: STEPS }).map((_, i) => (
            <div
              key={i}
              className="flex-1 h-[4px] rounded-full transition-all duration-300"
              style={{
                background: i <= step ? '#D0BCFF' : 'rgba(208,188,255,0.18)',
                boxShadow: i <= step ? '0 0 10px rgba(208,188,255,0.5)' : 'none'
              }}
            />
          ))}
        </div>
      </div>

      {/* Step content */}
      <div className="flex-1 overflow-y-auto px-[22px] relative z-10">
        <div
          key={step}
          className="animate-[wizardSlideIn_220ms_ease-out] pb-7"
        >
          {step === 0 && <OverviewStep />}
          {step === 1 && (
            <MeshStep
              meshInfo={meshInfo}
              isChecking={isChecking}
              errorMessage={errorMessage}
            />
          )}
          {step === 2 && (
            <TokenStep
              token={token}
              isPairing={isPairing}
              errorMessage={errorMessage}
            />
          )}
          {step === 3 && <WelcomeStep meshInfo={meshInfo} />}
        </div>
      </div>

      {/* Bottom action */}
      <div className="w-full px-[22px] py-[18px] bg-transparent relative z-10">
        <button
          onClick={handlePrimary}
          disabled={busy}
          className="w-full h-14 rounded-[28px] font-bold text-[15px] flex items-center justify-center gap-2.5 transition m3-spring-press disabled:opacity-45 shadow-lg"
          style={{ background: 'linear-gradient(135deg, #7C4DFF 0%, #6750A4 100%)', color: '#FFFFFF', border: '1px solid rgba(208,188,255,0.3)' }}
        >
          {busy && <Loader2 className="w-[18px] h-[18px] animate-spin text-[#FFFFFF]" />}
          {busy ? busyLabel : primaryLabel}
        </button>
      </div>
    </div>
  );
}
