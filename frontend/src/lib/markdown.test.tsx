import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { Markdown, parseBlocks } from './markdown';

describe('parseBlocks', () => {
  it('groups lines into paragraphs and lists', () => {
    expect(parseBlocks('Top flips:\n\n- Gold leaf\n- Thin snail\n\n1. First\n2. Second\nDone.')).toEqual([
      { kind: 'paragraph', lines: ['Top flips:'] },
      {
        kind: 'list',
        ordered: false,
        items: [
          { text: 'Gold leaf', nested: false },
          { text: 'Thin snail', nested: false },
        ],
      },
      {
        kind: 'list',
        ordered: true,
        items: [
          { text: 'First', nested: false },
          { text: 'Second', nested: false },
        ],
      },
      { kind: 'paragraph', lines: ['Done.'] },
    ]);
  });

  it('keeps nested items with their list', () => {
    expect(parseBlocks('1. **Gold leaf**\n  - Buy at 135,479\n2. Thin snail')).toEqual([
      {
        kind: 'list',
        ordered: true,
        items: [
          { text: '**Gold leaf**', nested: false },
          { text: 'Buy at 135,479', nested: true },
          { text: 'Thin snail', nested: false },
        ],
      },
    ]);
  });
});

describe('Markdown', () => {
  it('renders bold, code and headings', () => {
    expect(renderToStaticMarkup(<Markdown text={'### Best flips\n**Gold leaf** at `135,479` gp'} />)).toBe(
      '<div class="markdown"><p class="markdown-heading">Best flips</p>' +
        '<p><strong>Gold leaf</strong> at <code>135,479</code> gp</p></div>',
    );
  });

  it('never turns model output into markup', () => {
    const html = renderToStaticMarkup(<Markdown text={'<img src=x onerror=alert(1)> **<b>hi</b>**'} />);

    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(html).toContain('<strong>&lt;b&gt;hi&lt;/b&gt;</strong>');
  });
});
