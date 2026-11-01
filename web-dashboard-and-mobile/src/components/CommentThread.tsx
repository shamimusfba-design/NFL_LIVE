import React, { useMemo, useState } from 'react';
import { AlertComment } from '../types';

interface Props {
  targetId: string;
  comments: AlertComment[];
  onAdd: (targetId: string, text: string, parentId: string | null) => void;
}

const ago = (ms: number): string => {
  const m = Math.max(0, Math.round((Date.now() - ms) / 60000));
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return new Date(ms + 6 * 3600 * 1000).toISOString().slice(0, 16).replace('T', ' ');
};

/** Comments and replies under one notification. Anyone approved can write. */
export const CommentThread: React.FC<Props> = ({ targetId, comments, onAdd }) => {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');

  const mine = useMemo(() => comments.filter(c => c.targetId === targetId).sort((a, b) => a.createdMillis - b.createdMillis), [comments, targetId]);
  const top = mine.filter(c => !c.parentId);
  const repliesOf = (id: string) => mine.filter(c => c.parentId === id);

  const send = (value: string, parent: string | null, clear: () => void) => {
    const t = value.trim();
    if (!t) return;
    onAdd(targetId, t, parent);
    clear();
    setReplyTo(null);
  };

  const row = (c: AlertComment, isReply: boolean) => (
    <div key={c.id} className={`rounded-lg border border-slate-200 bg-white p-2.5 ${isReply ? 'ml-5' : ''}`}>
      <div className="flex items-center gap-2 text-[11px]">
        <b className="text-slate-900">{c.authorName}</b>
        <span className="rounded bg-slate-100 px-1.5 py-0.5 font-bold text-slate-600">{c.authorRole}</span>
        <span className="ml-auto text-slate-400">{ago(c.createdMillis)}</span>
      </div>
      <div className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{c.text}</div>
      {!isReply && (
        <button type="button" onClick={() => setReplyTo(replyTo === c.id ? null : c.id)} className="mt-1 text-[11px] font-bold text-blue-700 hover:underline">
          Reply
        </button>
      )}
    </div>
  );

  return (
    <div className="mt-2">
      <button type="button" onClick={() => setOpen(v => !v)} className="text-xs font-bold text-blue-700 hover:underline">
        {open ? 'Hide comments' : `Comments (${mine.length})`}
      </button>
      {open && (
        <div className="mt-2 space-y-2 rounded-xl bg-slate-50 p-2.5">
          {top.length === 0 && <div className="text-xs text-slate-500">No comments yet.</div>}
          {top.map(c => (
            <div key={c.id} className="space-y-1.5">
              {row(c, false)}
              {repliesOf(c.id).map(r => row(r, true))}
              {replyTo === c.id && (
                <div className="ml-5 flex gap-2">
                  <input
                    autoFocus
                    value={replyText}
                    onChange={e => setReplyText(e.target.value)}
                    placeholder="Write a reply"
                    maxLength={1000}
                    className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white p-2 text-sm text-slate-900"
                  />
                  <button type="button" onClick={() => send(replyText, c.id, () => setReplyText(''))} className="rounded-lg bg-[#1976D2] px-3 text-xs font-black text-white">Send</button>
                </div>
              )}
            </div>
          ))}
          <div className="flex gap-2">
            <input
              value={text}
              onChange={e => setText(e.target.value)}
              placeholder="Write a comment"
              maxLength={1000}
              className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white p-2 text-sm text-slate-900"
            />
            <button type="button" onClick={() => send(text, null, () => setText(''))} className="rounded-lg bg-[#1976D2] px-3 text-xs font-black text-white">Comment</button>
          </div>
        </div>
      )}
    </div>
  );
};
