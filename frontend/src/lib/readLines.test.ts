import { describe, expect, it } from 'vitest';

import { readLines } from './api';

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}

describe('readLines', () => {
  it('reassembles lines split across chunks', async () => {
    const lines: string[] = [];

    await readLines(streamOf('{"type":"te', 'xt","text":"Hi"}\n{"type":', '"done"}\n'), (line) => lines.push(line));

    expect(lines).toEqual(['{"type":"text","text":"Hi"}', '{"type":"done"}']);
  });

  it('keeps a last line without a newline and skips blank ones', async () => {
    const lines: string[] = [];

    await readLines(streamOf('a\n\n', 'b'), (line) => lines.push(line));

    expect(lines).toEqual(['a', 'b']);
  });

  it('decodes characters split between chunks', async () => {
    const bytes = new TextEncoder().encode('coins → gp\n');
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, 7));
        controller.enqueue(bytes.slice(7));
        controller.close();
      },
    });
    const lines: string[] = [];

    await readLines(stream, (line) => lines.push(line));

    expect(lines).toEqual(['coins → gp']);
  });
});
