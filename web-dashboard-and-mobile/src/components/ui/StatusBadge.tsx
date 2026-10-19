import React from 'react';

interface StatusBadgeProps {
  status: 'on-track' | 'warning' | 'critical';
}

export const StatusBadge = ({ status }: StatusBadgeProps) => {
  const styles = {
    'on-track': 'bg-emerald-100 text-emerald-700 border-emerald-200',
    'warning': 'bg-amber-100 text-amber-700 border-amber-200',
    'critical': 'bg-rose-100 text-rose-700 border-rose-200',
  };

  return (
    <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${styles[status]}`}>
      {status.replace('-', ' ').toUpperCase()}
    </span>
  );
};