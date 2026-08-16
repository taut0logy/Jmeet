'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FiLoader, FiZoomIn, FiZoomOut } from 'react-icons/fi';
import { Button } from '@/components/ui/button';
import { Slider } from '@/components/ui/slider';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';

const STAGE_SIZE = 288;
const OUTPUT_SIZE = 512;
const MIN_ZOOM = 1;
const MAX_ZOOM = 3;

type Point = { x: number; y: number };

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

export function AvatarEditorDialog({
  file,
  onOpenChange,
  onSave,
}: {
  file: File | null;
  onOpenChange: (open: boolean) => void;
  onSave: (blob: Blob) => Promise<void>;
}) {
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [naturalSize, setNaturalSize] = useState<{ w: number; h: number } | null>(null);
  const [zoom, setZoom] = useState(MIN_ZOOM);
  const [pos, setPos] = useState<Point>({ x: 0, y: 0 });
  const [saving, setSaving] = useState(false);
  const imgRef = useRef<HTMLImageElement>(null);
  const dragState = useRef<{ startPointer: Point; startPos: Point } | null>(null);

  useEffect(() => {
    if (!file) {
      setImageUrl(null);
      setNaturalSize(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setImageUrl(url);
    setZoom(MIN_ZOOM);
    setPos({ x: 0, y: 0 });
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const baseScale = useMemo(() => {
    if (!naturalSize) return 1;
    return STAGE_SIZE / Math.min(naturalSize.w, naturalSize.h);
  }, [naturalSize]);

  const scale = baseScale * zoom;
  const dispW = (naturalSize?.w ?? 0) * scale;
  const dispH = (naturalSize?.h ?? 0) * scale;
  const maxOffsetX = Math.max(0, (dispW - STAGE_SIZE) / 2);
  const maxOffsetY = Math.max(0, (dispH - STAGE_SIZE) / 2);

  const clampPos = useCallback(
    (p: Point) => ({ x: clamp(p.x, -maxOffsetX, maxOffsetX), y: clamp(p.y, -maxOffsetY, maxOffsetY) }),
    [maxOffsetX, maxOffsetY],
  );

  useEffect(() => {
    setPos((p) => clampPos(p));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [maxOffsetX, maxOffsetY]);

  function handlePointerDown(e: React.PointerEvent) {
    (e.target as Element).setPointerCapture(e.pointerId);
    dragState.current = { startPointer: { x: e.clientX, y: e.clientY }, startPos: pos };
  }

  function handlePointerMove(e: React.PointerEvent) {
    if (!dragState.current) return;
    const dx = e.clientX - dragState.current.startPointer.x;
    const dy = e.clientY - dragState.current.startPointer.y;
    setPos(clampPos({ x: dragState.current.startPos.x + dx, y: dragState.current.startPos.y + dy }));
  }

  function handlePointerUp(e: React.PointerEvent) {
    dragState.current = null;
    (e.target as Element).releasePointerCapture(e.pointerId);
  }

  async function handleSave() {
    if (!imgRef.current || !naturalSize) return;
    setSaving(true);
    try {
      const displayImgLeft = STAGE_SIZE / 2 + pos.x - dispW / 2;
      const displayImgTop = STAGE_SIZE / 2 + pos.y - dispH / 2;
      const cropLeft = -displayImgLeft / scale;
      const cropTop = -displayImgTop / scale;
      const cropSize = STAGE_SIZE / scale;

      const canvas = document.createElement('canvas');
      canvas.width = OUTPUT_SIZE;
      canvas.height = OUTPUT_SIZE;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('canvas unsupported');
      ctx.drawImage(imgRef.current, cropLeft, cropTop, cropSize, cropSize, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE);

      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png', 0.92));
      if (!blob) throw new Error('export failed');
      await onSave(blob);
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={!!file} onOpenChange={(open) => !saving && onOpenChange(open)}>
      <DialogContent className="sm:max-w-md" showCloseButton={!saving}>
        <DialogHeader>
          <DialogTitle>Edit profile photo</DialogTitle>
          <DialogDescription>Drag to reposition, and use the slider to zoom.</DialogDescription>
        </DialogHeader>

        <div className="flex flex-col items-center gap-4">
          <div
            className="relative overflow-hidden rounded-full bg-muted select-none touch-none cursor-grab active:cursor-grabbing"
            style={{ width: STAGE_SIZE, height: STAGE_SIZE }}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
          >
            {imageUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                ref={imgRef}
                src={imageUrl}
                alt=""
                draggable={false}
                onLoad={(e) => setNaturalSize({ w: e.currentTarget.naturalWidth, h: e.currentTarget.naturalHeight })}
                className="absolute top-1/2 left-1/2 max-w-none"
                style={{
                  width: dispW || undefined,
                  height: dispH || undefined,
                  transform: `translate(calc(-50% + ${pos.x}px), calc(-50% + ${pos.y}px))`,
                }}
              />
            ) : null}
            <div className="pointer-events-none absolute inset-0 rounded-full ring-1 ring-inset ring-black/10" />
          </div>

          <div className="flex w-full items-center gap-3 px-1">
            <FiZoomOut className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <Slider
              aria-label="Zoom"
              value={[zoom]}
              min={MIN_ZOOM}
              max={MAX_ZOOM}
              step={0.01}
              onValueChange={(v) => setZoom(v[0])}
              disabled={!naturalSize}
            />
            <FiZoomIn className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSave} disabled={saving || !naturalSize}>
            {saving ? <FiLoader className="size-4 animate-spin" /> : null}
            Save photo
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
