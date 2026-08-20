'use client';

import { FiGrid, FiUser, FiSidebar } from 'react-icons/fi';
import { Button } from '@/components/ui/button';

const LAYOUTS = [
  { mode: 'tiled', icon: FiGrid, label: 'Tiled' },
  { mode: 'spotlight', icon: FiUser, label: 'Spotlight' },
  { mode: 'sidebar', icon: FiSidebar, label: 'Sidebar' },
];

// Manual pin (video-grid.jsx) overrides the
// automatic spotlight choice within 'spotlight' mode.
export function LayoutSwitcher({ layoutMode, onChange }) {
  return (
    <div className="flex items-center gap-1 rounded-lg bg-white/5 p-1" role="group" aria-label="Layout">
      {LAYOUTS.map(({ mode, icon: Icon, label }) => (
        <Button
          key={mode}
          type="button"
          size="icon-sm"
          variant={layoutMode === mode ? 'secondary' : 'ghost'}
          aria-label={`${label} layout`}
          aria-pressed={layoutMode === mode}
          onClick={() => onChange(mode)}
        >
          <Icon />
        </Button>
      ))}
    </div>
  );
}
