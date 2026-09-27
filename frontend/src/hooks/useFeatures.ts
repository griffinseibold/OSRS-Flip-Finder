import { useEffect, useState } from 'react';

import { fetchFeatures, type Features } from '../lib/api';

const STANDALONE: Features = { chat: false, runelite: false };

/** The server's optional features; null until known. A server that cannot say is treated as standalone. */
export function useFeatures(): Features | null {
  const [features, setFeatures] = useState<Features | null>(null);

  useEffect(() => {
    let current = true;
    fetchFeatures().then(
      (result) => current && setFeatures(result),
      () => current && setFeatures(STANDALONE),
    );
    return () => {
      current = false;
    };
  }, []);

  return features;
}
