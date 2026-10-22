import React, { useState, useEffect, useRef } from 'react';
import { Send, Terminal, Code, ChevronDown, ChevronUp, Bot, User, Radio, Settings, MessageSquare, Users } from 'lucide-react';
import OnboardingWizard from './components/OnboardingWizard.jsx';

const DEFAULT_SERVER = 'http://100.75.107.79:8000';
const ONBOARDED_KEY = 'talos.onboarded';

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
  const wsRef = useRef(null);
  const messagesEndRef = useRef(null);

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
    <div className="flex h-screen bg-[#080c14] text-slate-100 font-sans">
      {/* Sidebar / Chambers */}
      <div className="w-72 border-r border-slate-800 flex flex-col bg-[#0b0f19]">
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-indigo-500 animate-pulse"></div>
            <span className="font-bold tracking-wider text-lg text-white">TALOS</span>
          </div>
          <span className="text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded font-mono">v0.1</span>
        </div>

        <div className="p-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Chambers</div>
        <div className="flex-1 overflow-y-auto px-2 space-y-1">
          {rooms.map(room => (
            <button
              key={room.id}
              onClick={() => setActiveRoomId(room.id)}
              className={`w-full text-left p-3 rounded-xl transition flex items-center gap-3 ${
                activeRoomId === room.id
                  ? 'bg-indigo-600/20 text-indigo-400 border border-indigo-500/30'
                  : 'hover:bg-slate-800/60 text-slate-300'
              }`}
            >
              <div className="w-5 h-5 flex items-center justify-center shrink-0">
                {room.mode === 'dm' ? <MessageSquare className="w-4 h-4 text-pink-400" /> : <Users className="w-4 h-4 text-indigo-400" />}
              </div>
              <div className="overflow-hidden">
                <div className="font-medium text-sm truncate">{room.name}</div>
                <div className="text-xs text-slate-500 truncate">{room.description}</div>
              </div>
            </button>
          ))}
        </div>

        <div className="p-3 border-t border-slate-800 text-xs text-slate-500 flex items-center gap-2">
          <Radio className={`w-3.5 h-3.5 ${connected ? 'text-emerald-500' : 'text-red-500'}`} />
          <span className="truncate">Tailscale: 100.75.107.79</span>
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col bg-[#080c14]">
        {/* Top Header */}
        <div className="h-14 border-b border-slate-800 px-6 flex items-center justify-between bg-[#080c14]/80 backdrop-blur">
          <div className="flex items-center gap-3">
            <span className="text-lg font-semibold">{activeRoom?.name || 'TALOS'}</span>
            <span className={`w-2 h-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-red-500'}`}></span>
          </div>
          <div className="text-xs text-slate-400 flex items-center gap-2">
            <span className="font-mono bg-slate-900 border border-slate-800 px-2.5 py-1 rounded-md">
              WireGuard Encrypted
            </span>
          </div>
        </div>

        {/* Message Stream */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.map((msg) => {
            const isHuman = msg.sender_role === 'human';
            return (
              <div key={msg.id} className={`flex gap-3 ${isHuman ? 'justify-end' : 'justify-start'}`}>
                {!isHuman && (
                  <div className="w-8 h-8 rounded-full bg-pink-500/20 border border-pink-500/40 flex items-center justify-center text-pink-400 font-bold text-xs shrink-0">
                    {msg.sender_name?.charAt(0) || 'A'}
                  </div>
                )}
                <div className={`max-w-xl ${isHuman ? 'items-end' : 'items-start'} flex flex-col`}>
                  {!isHuman && (
                    <span className="text-xs text-slate-400 font-medium mb-1">{msg.sender_name}</span>
                  )}
                  {msg.thought && (
                    <div className="mb-2 p-3 rounded-lg bg-purple-950/20 border border-purple-800/30 text-xs text-purple-300">
                      <div className="font-semibold text-purple-400 mb-1 flex items-center gap-1.5">
                        <Terminal className="w-3.5 h-3.5" /> Reasoning
                      </div>
                      <p className="whitespace-pre-wrap">{msg.thought}</p>
                    </div>
                  )}
                  <div className={`p-3.5 rounded-2xl text-sm leading-relaxed ${
                    isHuman
                      ? 'bg-indigo-600 text-white rounded-br-none'
                      : 'bg-[#0f172a] text-slate-100 border border-slate-800 rounded-bl-none'
                  }`}>
                    <p className="whitespace-pre-wrap">{msg.content}</p>
                  </div>
                </div>
              </div>
            );
          })}
          {isTyping && (
            <div className="text-xs text-indigo-400 animate-pulse flex items-center gap-2">
              <span>Thinking and formulating response...</span>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Bottom Input */}
        <form onSubmit={handleSend} className="p-4 border-t border-slate-800 bg-[#080c14]">
          <div className="flex items-center gap-2 max-w-4xl mx-auto bg-[#0f172a] border border-slate-800 rounded-2xl px-4 py-2 focus-within:border-indigo-500 transition">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={`Message ${activeRoom?.name || 'chamber'}...`}
              className="flex-1 bg-transparent border-none outline-none text-sm text-white placeholder-slate-500"
            />
            <button
              type="submit"
              disabled={!input.trim()}
              className="w-8 h-8 rounded-full bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:hover:bg-indigo-600 flex items-center justify-center text-white transition shrink-0"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
