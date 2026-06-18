import { useState, useEffect, useCallback } from 'react';

export interface AnimalVariant {
  id: string;
  name: string;
  frames: string[];
  tip: string;
}

export const MIN_ANIMATION_WIDTH = 60;
export const MIN_ANIMATION_HEIGHT = 20;

export interface AsciiAnimation {
  currentFrame: string;
  variant: AnimalVariant;
  switchVariant: () => void;
}

/**
 * Cycles through `animals[currentIdx].frames` at `fps`. Provides
 * `switchVariant()` to jump to a different animal (never the current one).
 * Mirrors codex ascii_animation.rs frame indexing.
 *
 * `active` gates the ticker so the host component is not forced to re-render
 * when the animation is off-screen (e.g. during generation).
 */
export function useAsciiAnimation(
  animals: AnimalVariant[],
  fps = 8,
  active = true,
): AsciiAnimation {
  const [idx, setIdx] = useState(() => (animals.length ? Math.floor(Math.random() * animals.length) : 0));
  const [frame, setFrame] = useState(0);

  const current = animals[idx] || animals[0];
  const frameCount = current ? current.frames.length : 0;

  useEffect(() => {
    if (!active || !current || frameCount <= 1) return;
    const interval = Math.max(16, Math.floor(1000 / fps));
    const id = setInterval(() => setFrame(f => (f + 1) % frameCount), interval);
    return () => clearInterval(id);
  }, [idx, fps, current, frameCount, active]);

  const switchVariant = useCallback(() => {
    if (animals.length <= 1) return;
    setIdx(prev => {
      let next = prev;
      while (next === prev) next = Math.floor(Math.random() * animals.length);
      return next;
    });
    setFrame(0);
  }, [animals.length]);

  const currentFrame = current ? (current.frames[frame] || current.frames[0] || '') : '';
  return { currentFrame, variant: current, switchVariant };
}
