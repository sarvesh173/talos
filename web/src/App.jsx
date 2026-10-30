import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Send, Terminal, Bot, MessageSquare, Users, Menu, X, Sparkles } from 'lucide-react';
import OnboardingWizard from './components/OnboardingWizard.jsx';

const DEFAULT_SERVER = typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.hostname}:8000` : 'http://localhost:8000';
const ONBOARDED_KEY = 'talos.onboarded';
const SPLASH_SEEN_KEY = 'talos.splashSeen';

/* ═══ M3 Expressive Purple Dot Splash (Plays only on fresh first load) ═══ */
function M3SplashScreen({ onFinished }) {
  const [phase, setPhase] = useState('dots');

  useEffect(() => {
    const t1 = setTimeout(() => setPhase('fading'), 1500);
    const t2 = setTimeout(() => { setPhase('done'); onFinished(); }, 1900);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onFinished]);

  if (phase === 'done') return null;

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 m3-purple-atmosphere ${phase === 'fading' ? 'm3-splash-exit' : ''}`}
    >
      <div className="flex items-center gap-3 relative z-10">
        <div className="w-12 h-12 rounded-[20px] flex items-center justify-center m3-scallop-badge">
          <Bot className="w-6 h-6 text-[#D0BCFF]" />
        </div>
        <div>
          <span className="text-2xl font-bold tracking-wider text-[#E6E0E9]">TALOS</span>
          <span className="block text-[10px] tracking-widest uppercase text-[#D0BCFF]/70 font-mono">Expressive Mesh</span>
        </div>
      </div>
      <div className="m3-dot-loader relative z-10"><span /><span /><span /></div>
      <p className="text-xs tracking-wide text-[#CAC4D0] relative z-10">Initializing neural chamber…</p>
    </div>
  );
}

/* ═══ M3 Expressive Typing Indicator ═══ */
function M3TypingIndicator() {
  return (
    <div className="flex items-center gap-2 px-4 py-2">
      <div className="flex gap-1.5">
        <div className="m3-typing-dot" />
        <div className="m3-typing-dot" />
        <div className="m3-typing-dot" />
      </div>
      <span className="text-xs text-[#D0BCFF]">Synthesizing thought…</span>
    </div>
  );
}

export default function App() {
  const [rooms, setRooms] = useState([]);
  const [activeRoomId, setActiveRoomId] = useState('the-council');
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER);
  const [apiToken, setApiToken] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem('talos.apiToken') || ''
  );
  const [onboarded, setOnboarded] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem(ONBOARDED_KEY) === '1'
  );

  /* Fix: Do NOT replay splash animation repeatedly on reload */
  const [splashDone, setSplashDone] = useState(
    () => typeof sessionStorage !== 'undefined' && sessionStorage.getItem(SPLASH_SEEN_KEY) === '1'
  );

  const wsRef = useRef(null);
  const messagesEndRef = useRef(null);

  const handleSplashDone = useCallback(() => {
    try {
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem(SPLASH_SEEN_KEY, '1');
      }
    } catch (e) {
      console.error(e);
    }
    setSplashDone(true);
  }, []);

  const authHeaders = () => (apiToken ? { Authorization: `Bearer ${apiToken}` } : {});

  const handleOnboardingComplete = ({ token, serverUrl: url }) => {
    if (token) setApiToken(token);
    if (url) setServerUrl(url);
    try {
      localStorage.setItem(ONBOARDED_KEY, '1');
      if (token) localStorage.setItem('talos.apiToken', token);
    } catch (e) {
      console.error('Could not persist onboarding state:', e);
    }
    setOnboarded(true);
  };

  useEffect(() => {
    if (!onboarded) return;
    fetch(`${serverUrl}/api/rooms`, { headers: authHeaders() })
      .then(res => res.json())
      .then(data => setRooms(data))
      .catch(err => console.error("Error fetching rooms:", err));
  }, [serverUrl, onboarded]);

  useEffect(() => {
    if (!onboarded) return;
    fetch(`${serverUrl}/api/rooms/${activeRoomId}/messages`, { headers: authHeaders() })
      .then(res => res.json())
      .then(data => setMessages(data))
      .catch(err => console.error("Error fetching messages:", err));

    const wsBase = serverUrl.replace(/^http/, 'ws') + `/ws/${activeRoomId}/web-client`;
    const wsUrl = apiToken ? `${wsBase}?token=${encodeURIComponent(apiToken)}` : wsBase;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = (err) => console.error("WebSocket error:", err);

    ws.onmessage = (event) => {
      try {
        const busEvent = JSON.parse(event.data);
        if (busEvent.event_type === 'message_new') {
          setMessages(prev => [...prev, busEvent.payload]);
          setIsTyping(false);
        } else if (busEvent.event_type === 'typing_indicator') {
          setIsTyping(busEvent.payload?.is_typing || false);
        }
      } catch (e) {
        console.error(e);
      }
    };

    return () => ws.close();
  }, [activeRoomId, serverUrl, apiToken, onboarded]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e) => {
    e.preventDefault();
    if (!input.trim() || !wsRef.current) return;
    const payload = {
      type: 'message',
      room_id: activeRoomId,
      sender_name: 'Sarvesh',
      content: input
    };
    wsRef.current.send(JSON.stringify(payload));
    setInput('');
  };

  const activeRoom = rooms.find(r => r.id === activeRoomId);

  if (!onboarded) {
    return (
      <OnboardingWizard
        serverUrl={serverUrl}
        onComplete={handleOnboardingComplete}
      />
    );
  }

  return (
    <>
      {/* ═══ M3 Splash: Only displays on first session cold start ═══ */}
      {!splashDone && <M3SplashScreen onFinished={handleSplashDone} />}

      <div
        className={`flex h-[100dvh] w-screen font-sans m3-purple-atmosphere overflow-hidden ${splashDone ? 'm3-chamber-enter' : 'opacity-0'}`}
        style={{ color: 'var(--md-sys-color-on-surface)' }}
      >
        {/* ═══ Sidebar — Responsive M3 Navigation Rail / Drawer ═══ */}
        <aside
          className={`
            fixed inset-y-0 left-0 z-40 w-72 md:static md:translate-x-0 transition-transform duration-300 ease-[cubic-bezier(0.2,0,0,1)]
            flex flex-col m3-card-glass border-r border-[rgba(208,188,255,0.14)]
            ${mobileDrawerOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}
          `}
        >
          {/* Expressive Header pill */}
          <div className="m-3 p-3 rounded-[24px] m3-expressive-border m3-card-glass flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-[14px] flex items-center justify-center m3-scallop-badge">
                <Bot className="w-5 h-5 text-[#D0BCFF]" />
              </div>
              <div>
                <span className="font-bold tracking-wider text-base text-[#E6E0E9]">TALOS</span>
                <span className="block text-[9px] tracking-widest uppercase text-[#D0BCFF]/80 font-mono">M3 Expressive</span>
              </div>
            </div>
            <button
              onClick={() => setMobileDrawerOpen(false)}
              className="md:hidden p-1.5 rounded-full hover:bg-[rgba(208,188,255,0.12)] text-[#CAC4D0]"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          <div className="px-5 py-2 text-[11px] font-semibold uppercase tracking-wider text-[#D0BCFF]/70 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-[#D0BCFF]" />
            Active Chambers
          </div>

          {/* Chamber List with M3 Fluid Pill Selection */}
          <div className="flex-1 overflow-y-auto px-2 space-y-1.5">
            {rooms.map(room => {
              const isSelected = activeRoomId === room.id;
              return (
                <button
                  key={room.id}
                  onClick={() => {
                    setActiveRoomId(room.id);
                    setMobileDrawerOpen(false);
                  }}
                  className={`
                    w-full text-left p-3 rounded-[20px] m3-state-layer m3-spring-press flex items-center gap-3 transition-all duration-200
                    ${isSelected ? 'm3-expressive-border bg-[rgba(103,80,164,0.35)] text-[#EADDFF]' : 'hover:bg-[rgba(208,188,255,0.06)] text-[#CAC4D0]'}
                  `}
                >
                  <div className={`w-8 h-8 rounded-[12px] flex items-center justify-center shrink-0 ${isSelected ? 'bg-[#4F378B]' : 'bg-[rgba(33,31,38,0.6)]'}`}>
                    {room.mode === 'dm'
                      ? <MessageSquare className="w-4 h-4 text-[#EFB8C8]" />
                      : <Users className="w-4 h-4 text-[#D0BCFF]" />}
                  </div>
                  <div className="overflow-hidden flex-1">
                    <div className="font-medium text-sm truncate">{room.name}</div>
                    <div className="text-xs truncate text-[#CAC4D0]/60">{room.description}</div>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Mesh status footer */}
          <div className="p-3 text-xs flex items-center gap-2 border-t border-[rgba(208,188,255,0.12)] text-[#CAC4D0]/80">
            <span
              className="w-2.5 h-2.5 rounded-full shrink-0 animate-pulse"
              style={{ background: connected ? '#10B981' : '#F2B8B5' }}
            />
            <span className="truncate font-mono text-[11px]">Mesh: {typeof window !== 'undefined' ? window.location.hostname : 'connected'}</span>
          </div>
        </aside>

        {/* Backdrop for mobile drawer */}
        {mobileDrawerOpen && (
          <div
            onClick={() => setMobileDrawerOpen(false)}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-30 md:hidden"
          />
        )}

        {/* ═══ Main Chat Area — Purple Gradient Chamber ═══ */}
        <main className="flex-1 flex flex-col h-full min-w-0 relative z-10">

          {/* Floating Top Header */}
          <header className="mx-3 mt-3 px-4 py-2.5 rounded-[24px] m3-expressive-border m3-card-glass flex items-center justify-between shadow-lg">
            <div className="flex items-center gap-3">
              <button
                onClick={() => setMobileDrawerOpen(true)}
                className="md:hidden p-2 rounded-full hover:bg-[rgba(208,188,255,0.12)] text-[#D0BCFF]"
                aria-label="Toggle chambers"
              >
                <Menu className="w-5 h-5" />
              </button>
              <div>
                <span className="text-base font-semibold text-[#E6E0E9]">
                  {activeRoom?.name || 'TALOS'}
                </span>
                <span className="hidden sm:inline-block ml-2 text-xs text-[#D0BCFF]/60 font-mono">
                  {connected ? '● Linked' : '○ Standby'}
                </span>
              </div>
            </div>

            <span className="text-[11px] font-mono px-3 py-1 rounded-full bg-[rgba(79,55,139,0.3)] text-[#D0BCFF] border border-[rgba(208,188,255,0.2)]">
              WireGuard Encrypted
            </span>
          </header>

          {/* Message Stream */}
          <div className="flex-1 overflow-y-auto px-4 md:px-8 py-4 space-y-4">
            {messages.map((msg) => {
              const isHuman = msg.sender_role === 'human';
              return (
                <div key={msg.id} className={`flex gap-3 ${isHuman ? 'justify-end' : 'justify-start'}`}>
                  {!isHuman && (
                    <div className="w-8 h-8 rounded-[12px] flex items-center justify-center text-xs font-bold shrink-0 bg-[#2B1D3A] border border-[rgba(208,188,255,0.3)] text-[#D0BCFF] shadow-sm">
                      {msg.sender_name?.charAt(0) || 'A'}
                    </div>
                  )}
                  <div className={`max-w-xl ${isHuman ? 'items-end' : 'items-start'} flex flex-col`}>
                    {!isHuman && (
                      <span className="text-xs font-medium mb-1 text-[#D0BCFF]/80">{msg.sender_name}</span>
                    )}
                    {msg.thought && (
                      <div className="mb-2 p-3 rounded-[16px] text-xs bg-[rgba(43,29,58,0.7)] border border-[rgba(208,188,255,0.2)] text-[#EADDFF]">
                        <div className="font-semibold mb-1 flex items-center gap-1.5 text-[#D0BCFF]">
                          <Terminal className="w-3.5 h-3.5" /> Reasoning
                        </div>
                        <p className="whitespace-pre-wrap">{msg.thought}</p>
                      </div>
                    )}
                    <div
                      className="p-3.5 text-sm leading-relaxed shadow-md"
                      style={{
                        borderRadius: isHuman
                          ? '22px 22px 6px 22px'
                          : '22px 22px 22px 6px',
                        background: isHuman
                          ? 'linear-gradient(135deg, #7C4DFF 0%, #6750A4 100%)'
                          : 'rgba(33, 31, 38, 0.85)',
                        color: isHuman ? '#FFFFFF' : '#E6E0E9',
                        border: isHuman ? 'none' : '1px solid rgba(208, 188, 255, 0.16)'
                      }}
                    >
                      <p className="whitespace-pre-wrap">{msg.content}</p>
                    </div>
                  </div>
                </div>
              );
            })}
            {isTyping && <M3TypingIndicator />}
            <div ref={messagesEndRef} />
          </div>

          {/* ═══ M3 Expressive Floating Command Dock ═══ */}
          <form onSubmit={handleSend} className="p-3 md:p-4">
            <div className="flex items-center gap-2 max-w-4xl mx-auto px-4 py-2 rounded-[28px] m3-expressive-border m3-card-glass shadow-xl">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={`Message ${activeRoom?.name || 'chamber'}…`}
                className="flex-1 bg-transparent border-none outline-none text-sm text-[#E6E0E9] placeholder-[#CAC4D0]/50"
              />
              <button
                type="submit"
                disabled={!input.trim()}
                className="w-10 h-10 rounded-full flex items-center justify-center m3-spring-press shrink-0 disabled:opacity-30 bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] shadow-md"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </form>
        </main>
      </div>
    </>
  );
}
