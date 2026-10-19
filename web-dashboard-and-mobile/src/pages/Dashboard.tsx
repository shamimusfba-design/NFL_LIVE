import React, { useEffect, useState } from 'react';
import { 
  LayoutDashboard, 
  TrendingUp, 
  AlertTriangle, 
  CheckCircle2, 
  Tv, 
  Filter,
  ChevronDown 
} from 'lucide-react';
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area 
} from 'recharts';

import { databaseService } from '../services/databaseService';
import { ProductionEntry, QualityEntry } from '../types/nfl-types';
import { KPICard } from '../components/ui/KPICard';
import { StatusBadge } from '../components/ui/StatusBadge';

// Mock data for the chart until real data flows in
const MOCK_CHART_DATA = [
  { time: '08:00', prod: 400 }, { time: '10:00', prod: 700 },
  { time: '12:00', prod: 600 }, { time: '14:00', prod: 900 },
  { time: '16:00', prod: 1100 },
];

export const Dashboard = () => {
  const [production, setProduction] = useState<ProductionEntry[]>([]);
  const [isTvMode, setIsTvMode] = useState(false);
  const [selectedFloor, setSelectedFloor] = useState<string>("");

  // REAL-TIME SYNC: Subscribe to production updates
  useEffect(() => {
    const unsubscribe = databaseService.subscribeToProduction(
      (data) => setProduction(data),
      selectedFloor
    );
    return () => unsubscribe();
  }, [selectedFloor]);

  // Calculate aggregate KPIs from real-time data
  const totalQty = production.reduce((sum, item) => sum + item.qty, 0);
  const avgEff = production.length > 0 
    ? (production.reduce((sum, item) => sum + item.efficiency, 0) / production.length).toFixed(1) 
    : "0";

  return (
    <div className={cn(
      "min-h-screen transition-all duration-500",
      isTvMode ? "bg-slate-950 p-10 scale-125 origin-top" : "bg-slate-50 p-4 md:p-8"
    )}>
      
      {/* HEADER & FILTERS */}
      <header className={cn(
        "flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4",
        isTvMode ? "text-white" : "text-slate-900"
      )}>
        <div>
          <h1 className={cn("text-3xl font-bold tracking-tight", isTvMode && "text-5xl")}>
            NFL LIVE Dashboard
          </h1>
          <p className={cn("text-slate-500", isTvMode && "text-slate-400 text-xl")}>
            Real-time Factory Performance
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button 
            onClick={() => setIsTvMode(!isTvMode)}
            className={cn(
              "flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors",
              isTvMode ? "bg-white text-slate-900" : "bg-slate-900 text-white hover:bg-slate-800"
            )}
          >
            <Tv size={18} /> {isTvMode ? "Exit TV Mode" : "Enter TV Mode"}
          </button>

          <div className="relative group">
            <button className={cn(
              "flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-lg text-slate-700 shadow-sm",
              isTvMode && "bg-slate-800 text-white border-slate-700"
            )}>
              <Filter size={18} /> Floor Select
              <ChevronDown size={16} />
            </button>
          </div>
        </div>
      </header>

      {/* KPI SECTION */}
      <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <KPICard 
          title="Total Production" 
          value={totalQty.toLocaleString()} 
          subtitle="Today's accumulated"
          icon={TrendingUp} 
          trend={{ value: 12, isPositive: true }}
          color="blue"
        />
        <KPICard 
          title="Avg Efficiency" 
          value={`${avgEff}%`} 
          subtitle="Target: 92%"
          icon={CheckCircle2} 
          trend={{ value: 2.4, isPositive: true }}
          color="green"
        />
        <KPICard 
          title="Defect Rate" 
          value="1.2%" 
          subtitle="Target: < 1.5%"
          icon={AlertTriangle} 
          trend={{ value: 0.5, isPositive: false }}
          color="amber"
        />
        <KPICard 
          title="NPT Minutes" 
          value="42" 
          subtitle="Downtime today"
          icon={AlertTriangle} 
          trend={{ value: 5, isPositive: false }}
          color="red"
        />
      </section>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* CHART SECTION */}
        <section className={cn(
          "lg:col-span-2 rounded-2xl p-6 border shadow-sm transition-colors",
          isTvMode ? "bg-slate-900 border-slate-800 text-white" : "bg-white border-slate-100"
        )}>
          <h2 className="text-lg font-semibold mb-6">Production Trend (Hourly)</h2>
          <div className="h-[300px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={MOCK_CHART_DATA}>
                <defs>
                  <linearGradient id="colorProd" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={isTvMode ? "#334155" : "#f1f5f9"} />
                <XAxis dataKey="time" stroke={isTvMode ? "#94a3b8" : "#64748b"} fontSize={12} />
                <YAxis stroke={isTvMode ? "#94a3b8" : "#64748b"} fontSize={12} />
                <Tooltip 
                  contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Area type="monotone" dataKey="prod" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorProd)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </section>

        {/* RECENT ACTIVITY / PERFORMANCE MATRIX PREVIEW */}
        <section className={cn(
          "rounded-2xl p-6 border shadow-sm",
          isTvMode ? "bg-slate-900 border-slate-800 text-white" : "bg-white border-slate-100"
        )}>
          <h2 className="text-lg font-semibold mb-4">Line Status</h2>
          <div className="space-y-4">
            {[1, 2, 3, 4].map((line) => (
              <div key={line} className="flex items-center justify-between p-3 rounded-xl bg-slate-50 dark:bg-slate-800/50">
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                  <span className="font-medium">Line 0{line}</span>
                </div>
                <StatusBadge status={line === 3 ? 'warning' : 'on-track'} />
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};

// Simple helper for conditional classes
const cn = (...classes: any[]) => classes.filter(Boolean).join(' ');