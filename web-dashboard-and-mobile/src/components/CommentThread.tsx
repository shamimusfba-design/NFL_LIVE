import React, { useMemo, useState, useEffect } from 'react';
import { AlertComment, UserAccount } from '../types';
import { resolveCommentDesignation } from '../data/designations';

interface Props {
  targetId: string;
  comments: AlertComment[];
  users?: UserAccount[];
  onAdd: (targetId: string, text: string, parentId: string | null) => void;
}

export const formatCommentAgo = (ms: number): string => {
  const m = Math.max(0, Math.round((Date.now() - ms) / 60000));
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return new Date(ms + 6 * 3600 * 1000).toISOString().slice(0, 16).replace('T', ' ');
};

/** Comments and replies under one notification. Anyone approved can write. */
export const CommentThread: React.FC<Props> = ({ targetId, comments, users = [], onAdd }) => {
  const mine = useMemo(
    () => comments.filter(c => c.targetId === targetId).sort((a, b) => a.createdMillis - b.createdMillis),
    [comments, targetId]
  );
  const [open, setOpen] = useState(() => mine.length > 0);
  const [text, setText] = useState('');
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');

  useEffect(() => {
    if (mine.length > 0) {
      setOpen(true);
    }
  }, [mine.length]);

  const top = mine.filter(c => !c.parentId);
  const repliesOf = (id: string) => mine.filter(c => c.parentId === id);
  const latestComment = mine.length > 0 ? mine[mine.length - 1] : null;

  const send = (value: string, parent: string | null, clear: () => void) => {
    const t = value.trim();
    if (!t) return;
    onAdd(targetId, t, parent);
    clear();
    setReplyTo(null);
    setOpen(true);
  };

  const row = (c: AlertComment, isReply: boolean) => {
    const desig = resolveCommentDesignation(c, users);
    return (
      <div key={c.id} className={`rounded-lg border border-slate-200 bg-white p-2.5 shadow-sm ${isReply ? 'ml-5 border-l-4 border-l-blue-500' : ''}`}>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <b className="text-slate-900 font-black">{c.authorName}</b>
          <span className="rounded-md bg-blue-100 border border-blue-200 px-2 py-0.5 text-[11px] font-extrabold text-[#002D62]">
            {desig}
          </span>
          <span className="ml-auto text-[11px] text-slate-400">{formatCommentAgo(c.createdMillis)}</span>
        </div>
        <div className="mt-1.5 whitespace-pre-wrap text-sm font-medium text-slate-800">{c.text}</div>
        {!isReply && (
          <button
            type="button"
            onClick={() => setReplyTo(replyTo === c.id ? null : c.id)}
            className="mt-1 text-[11px] font-bold text-blue-700 hover:underline"
          >
            Reply
          </button>
        )}
      </div>
    );
  };

  return (
    <div className="mt-2">
      {/* Latest comment preview shown at the top even when collapsed */}
      {!open && latestComment && (
        <div
          onClick={() => setOpen(true)}
          className="mb-1.5 cursor-pointer rounded-lg border border-blue-200 bg-blue-50/70 px-3 py-2 text-xs hover:bg-blue-50 transition"
        >
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-black text-slate-900">{latestComment.authorName}</span>
            <span className="rounded bg-blue-100 border border-blue-200 px-1.5 py-0.5 text-[10px] font-extrabold text-[#002D62]">
              {resolveCommentDesignation(latestComment, users)}
            </span>
            <span className="ml-auto text-[10px] text-slate-400">{formatCommentAgo(latestComment.createdMillis)}</span>
          </div>
          <div className="mt-1 text-slate-800 font-semibold line-clamp-2">{latestComment.text}</div>
        </div>
      )}

      <button type="button" onClick={() => setOpen(v => !v)} className="text-xs font-bold text-blue-700 hover:underline">
        {open ? `Hide comments (${mine.length})` : `💬 Write / View comments (${mine.length})`}
      </button>
      {open && (
        <div className="mt-2 space-y-2 rounded-xl bg-slate-50 p-2.5 border border-slate-200/80">
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
                  <button
                    type="button"
                    onClick={() => send(replyText, c.id, () => setReplyText(''))}
                    className="rounded-lg bg-[#1976D2] px-3 text-xs font-black text-white"
                  >
                    Send
                  </button>
                </div>
              )}
            </div>
          ))}
          <div className="flex gap-2">
            <input
              value={text}
              onChange={e => setText(e.target.value)}
              placeholder="Write a comment..."
              maxLength={1000}
              className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white p-2 text-sm text-slate-900"
            />
            <button
              type="button"
              onClick={() => send(text, null, () => setText(''))}
              className="rounded-lg bg-[#1976D2] px-3 text-xs font-black text-white"
            >
              Comment
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
