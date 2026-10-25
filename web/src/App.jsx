import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Send, Terminal, Bot, MessageSquare, Users } from 'lucide-react';
import OnboardingWizard from './components/OnboardingWizard.jsx';

const DEFAULT_SERVER = 'http://100.75.107.79:8000';
const ONBOARDED_KEY = 'talos.onboarded';

/* ═══ M3 Dot-Pulse Splash → Chamber Cross-Fade ═══ */
function M3SplashScreen({ onFinished }) {
  const [phase, setPhase] = useState('dots');

  useEffect(() => {
    const t1 = setTimeout(() => setPhase('fading'), 1800);
    const t2 = setTimeout(() => { setPhase('done'); onFinished(); }, 2300);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onFinished]);

  if (phase === 'done') return null;

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 ${phase === 'fading' ? 'm3-splash-exit' : ''}`}
      style={{ background: 'var(--md-sys-color-surface)' }}
    >
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-2xl flex items-center justify-center metallic-border"
          style={{ background: 'var(--metallic-socket)' }}>
          <Bot className="w-5 h-5" style={{ color: 'var(--md-sys-color-primary)' }} />
        </div>
        <span className="text-2xl font-bold tracking-wider"
          style={{ color: 'var(--md-sys-color-on-surface)' }}>TALOS</span>
      </div>
      <div className="m3-dot-loader"><span /><span /><span /></div>
      <p className="text-xs tracking-wide"
        style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Initializing mesh…</p>
    </div>
  );
}

/* ═══ M3 Typing Indicator ═══ */
function M3TypingIndicator() {
  return (
    <div className="flex items-center gap-2 px-4 py-2">
      <div className="flex gap-1">
        <div className="m3-typing-dot" />
        <div className="m3-typing-dot" />
        <div className="m3-typing-dot" />
      </div>
      <span className="text-xs" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Thinking…</span>
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
  const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER);
  const [apiToken, setApiToken] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem('talos.apiToken') || ''
  );
  const [onboarded, setOnboarded] = useState(
    () => typeof localStorage !== 'undefined' && localStorage.getItem(ONBOARDED_KEY) === '1'
  );
  const [splashDone, setSplashDone] = useState(false);
  const wsRef = useRef(null);
  const messagesEndRef = useRef(null);
  const handleSplashDone = useCallback(() => setSplashDone(true), []);

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
      {/* ═══ M3 Splash → Metallic Chamber cross-fade ═══ */}
      {!splashDone && <M3SplashScreen onFinished={handleSplashDone} />}

      <div
        className={`flex h-screen font-sans ${splashDone ? 'm3-chamber-enter' : 'opacity-0'}`}
        style={{ background: 'var(--md-sys-color-surface)', color: 'var(--md-sys-color-on-surface)' }}
      >
        {/* ═══ Sidebar — Metallic Chamber Rail ═══ */}
        <div className="w-72 flex flex-col"
          style={{ background: 'var(--md-sys-color-surface-container-low)', borderRight: '1px solid var(--md-sys-color-outline)' }}>

          {/* Metallic header pill */}
          <div className="m-3 p-3 rounded-[22px] metallic-border metallic-glass flex items-center justify-between"
            style={{ background: 'var(--md-sys-color-surface-container-high)' }}>
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                style={{ background: 'var(--metallic-socket)', border: '1px solid color-mix(in srgb, var(--md-sys-color-primary) 50%, transparent)' }}>
                <Bot className="w-4 h-4" style={{ color: 'var(--md-sys-color-primary)' }} />
              </div>
              <span className="font-bold tracking-wider text-base" style={{ color: 'var(--md-sys-color-on-surface)' }}>TALOS</span>
            </div>
            <span className="text-[10px] px-2 py-0.5 rounded-full font-mono"
              style={{ background: 'var(--md-sys-color-surface-container)', color: 'var(--md-sys-color-on-surface-variant)' }}>v0.1</span>
          </div>

          <div className="px-5 py-2 text-[11px] font-semibold uppercase tracking-wider"
            style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>Active Chambers</div>

          <div className="flex-1 overflow-y-auto px-2 space-y-1">
            {rooms.map(room => (
              <button key={room.id} onClick={() => setActiveRoomId(room.id)}
                className={`w-full text-left p-3 rounded-2xl m3-state-layer m3-spring-press flex items-center gap-3 ${activeRoomId === room.id ? 'metallic-border' : ''}`}
                style={{
                  background: activeRoomId === room.id ? 'var(--md-sys-color-surface-container-high)' : 'transparent',
                  color: activeRoomId === room.id ? 'var(--md-sys-color-primary)' : 'var(--md-sys-color-on-surface-variant)',
                  transition: 'all var(--md-sys-motion-duration-medium2) var(--md-sys-motion-easing-standard)'
                }}>
                <div className="w-5 h-5 flex items-center justify-center shrink-0">
                  {room.mode === 'dm'
                    ? <MessageSquare className="w-4 h-4" style={{ color: 'var(--md-sys-color-tertiary)' }} />
                    : <Users className="w-4 h-4" style={{ color: 'var(--md-sys-color-primary)' }} />}
                </div>
                <div className="overflow-hidden">
                  <div className="font-medium text-sm truncate">{room.name}</div>
                  <div className="text-xs truncate" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>{room.description}</div>
                </div>
              </button>
            ))}
          </div>

          <div className="p-3 text-xs flex items-center gap-2"
            style={{ borderTop: '1px solid var(--md-sys-color-outline)', color: 'var(--md-sys-color-on-surface-variant)' }}>
            <span className="w-2 h-2 rounded-full shrink-0"
              style={{ background: connected ? 'var(--md-sys-color-tertiary)' : 'var(--md-sys-color-error)' }} />
            <span className="truncate">Tailscale: 100.75.107.79</span>
          </div>
        </div>

        {/* ═══ Main Chat — Metallic Chamber ═══ */}
        <div className="flex-1 flex flex-col" style={{ background: 'var(--md-sys-color-surface)' }}>

          {/* Metallic floating top header */}
          <div className="mx-3 mt-3 px-5 py-3 rounded-[22px] metallic-border metallic-glass flex items-center justify-between"
            style={{ background: 'var(--md-sys-color-surface-container-high)' }}>
            <div className="flex items-center gap-3">
              <span className="text-base font-semibold" style={{ color: 'var(--md-sys-color-on-surface)' }}>
                {activeRoom?.name || 'TALOS'}
              </span>
              <span className="w-2 h-2 rounded-full"
                style={{ background: connected ? 'var(--md-sys-color-tertiary)' : 'var(--md-sys-color-error)' }} />
            </div>
            <span className="text-[11px] font-mono px-3 py-1 rounded-full"
              style={{ background: 'var(--md-sys-color-surface-container)', color: 'var(--md-sys-color-on-surface-variant)', border: '1px solid var(--md-sys-color-outline)' }}>
              WireGuard Encrypted
            </span>
          </div>

          {/* Message stream */}
          <div className="flex-1 overflow-y-auto p-6 space-y-4">
            {messages.map((msg) => {
              const isHuman = msg.sender_role === 'human';
              return (
                <div key={msg.id} className={`flex gap-3 ${isHuman ? 'justify-end' : 'justify-start'}`}>
                  {!isHuman && (
                    <div className="w-8 h-8 rounded-xl flex items-center justify-center text-xs font-bold shrink-0"
                      style={{
                        background: 'var(--metallic-socket)',
                        border: '1px solid color-mix(in srgb, var(--md-sys-color-tertiary) 40%, transparent)',
                        color: 'var(--md-sys-color-tertiary)'
                      }}>
                      {msg.sender_name?.charAt(0) || 'A'}
                    </div>
                  )}
                  <div className={`max-w-xl ${isHuman ? 'items-end' : 'items-start'} flex flex-col`}>
                    {!isHuman && (
                      <span className="text-xs font-medium mb-1" style={{ color: 'var(--md-sys-color-on-surface-variant)' }}>{msg.sender_name}</span>
                    )}
                    {msg.thought && (
                      <div className="mb-2 p-3 rounded-xl text-xs"
                        style={{
                          background: 'color-mix(in srgb, var(--md-sys-color-primary) 8%, var(--md-sys-color-surface-container))',
                          border: '1px solid color-mix(in srgb, var(--md-sys-color-primary) 20%, transparent)',
                          color: 'color-mix(in srgb, var(--md-sys-color-primary) 70%, var(--md-sys-color-on-surface))'
                        }}>
                        <div className="font-semibold mb-1 flex items-center gap-1.5">
                          <Terminal className="w-3.5 h-3.5" /> Reasoning
                        </div>
                        <p className="whitespace-pre-wrap">{msg.thought}</p>
                      </div>
                    )}
                    <div className="p-3.5 text-sm leading-relaxed"
                      style={{
                        borderRadius: isHuman
                          ? 'var(--md-sys-shape-corner-extra-large) var(--md-sys-shape-corner-extra-large) var(--md-sys-shape-corner-extra-small) var(--md-sys-shape-corner-extra-large)'
                          : 'var(--md-sys-shape-corner-extra-large) var(--md-sys-shape-corner-extra-large) var(--md-sys-shape-corner-extra-large) var(--md-sys-shape-corner-extra-small)',
                        background: isHuman ? 'var(--md-sys-color-primary)' : 'var(--md-sys-color-surface-container-high)',
                        color: isHuman ? 'var(--md-sys-color-on-primary)' : 'var(--md-sys-color-on-surface)',
                        border: isHuman ? 'none' : '1px solid var(--md-sys-color-outline)'
                      }}>
                      <p className="whitespace-pre-wrap">{msg.content}</p>
                    </div>
                  </div>
                </div>
              );
            })}
            {isTyping && <M3TypingIndicator />}
            <div ref={messagesEndRef} />
          </div>

          {/* ═══ Metallic floating command dock ═══ */}
          <form onSubmit={handleSend} className="p-3">
            <div className="flex items-center gap-2 max-w-4xl mx-auto px-4 py-2.5 rounded-[26px] metallic-border"
              style={{ background: 'var(--md-sys-color-surface-container-high)', transition: 'border-color var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard)' }}>
              <input type="text" value={input} onChange={(e) => setInput(e.target.value)}
                placeholder={`Message ${activeRoom?.name || 'chamber'}…`}
                className="flex-1 bg-transparent border-none outline-none text-sm"
                style={{ color: 'var(--md-sys-color-on-surface)', caretColor: 'var(--md-sys-color-primary)' }} />
              <button type="submit" disabled={!input.trim()}
                className="w-9 h-9 rounded-full flex items-center justify-center m3-spring-press shrink-0 disabled:opacity-30"
                style={{
                  background: input.trim() ? 'var(--md-sys-color-primary)' : 'var(--md-sys-color-surface-container)',
                  color: input.trim() ? 'var(--md-sys-color-on-primary)' : 'var(--md-sys-color-on-surface-variant)',
                  transition: 'all var(--md-sys-motion-duration-short3) var(--md-sys-motion-easing-standard)'
                }}>
                <Send className="w-4 h-4" />
              </button>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}
