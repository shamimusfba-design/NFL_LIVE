import React from 'react';
import { NflLogo } from './NflLogo';

/** Start-up screen: the factory logo inside two circling arrows (recycle / keep-moving), in a loop. */
export const LoadingScreen: React.FC<{ label?: string }> = ({ label = 'Loading...' }) => (
  <div
    style={{
      minHeight: '100vh',
      display: 'grid',
      placeItems: 'center',
      background: 'radial-gradient(900px 500px at 50% 30%, #0C2A63 0%, #061B43 55%, #040F26 100%)'
    }}
  >
    <style>{`
      @keyframes nflRing { to { transform: rotate(360deg); } }
      @keyframes nflBreath { 0%,100% { transform: scale(1); } 50% { transform: scale(1.07); } }
      @keyframes nflFade { 0%,100% { opacity: .45; } 50% { opacity: 1; } }
    `}</style>
    <div style={{ textAlign: 'center' }}>
      <div style={{ position: 'relative', width: 168, height: 168, margin: '0 auto' }}>
        <svg
          viewBox="0 0 168 168"
          width="168"
          height="168"
          style={{ position: 'absolute', inset: 0, animation: 'nflRing 1.8s linear infinite' }}
          aria-hidden="true"
        >
          <g fill="none" stroke="#1677FF" strokeWidth="7" strokeLinecap="round">
            <path d="M84 12 A72 72 0 0 1 156 84" />
            <path d="M84 12 A72 72 0 0 1 156 84" transform="rotate(180 84 84)" stroke="#10B981" />
          </g>
          <polygon points="156,84 146,70 166,70" fill="#1677FF" transform="rotate(8 156 84)" />
          <polygon points="156,84 146,70 166,70" fill="#10B981" transform="rotate(188 84 84) rotate(8 156 84)" />
        </svg>
        <div
          style={{
            position: 'absolute', left: 30, top: 30, width: 108, height: 108, borderRadius: '50%', background: '#fff',
            display: 'grid', placeItems: 'center', boxShadow: '0 8px 26px rgba(0,0,0,.5)', animation: 'nflBreath 1.8s ease-in-out infinite', overflow: 'hidden'
          }}
        >
          <NflLogo size={90} />
        </div>
      </div>
      <div style={{ marginTop: 22, fontSize: 26, fontWeight: 900, letterSpacing: 1.2, color: '#fff' }}>
        NFL <span style={{ color: '#1677FF' }}>LIVE</span>
      </div>
      <div style={{ marginTop: 8, fontSize: 12, letterSpacing: 2, color: '#94A3B8', animation: 'nflFade 1.4s ease-in-out infinite' }}>
        {label}
      </div>
    </div>
  </div>
);
