import { Fragment, type ReactNode } from 'react';

type Block =
  | { kind: 'paragraph'; lines: string[] }
  | { kind: 'heading'; text: string }
  | { kind: 'list'; ordered: boolean; items: { text: string; nested: boolean }[] };

const LIST_ITEM = /^(\s*)([-*•]|\d+[.)])\s+(.*)$/;
const HEADING = /^#{1,6}\s+(.*)$/;

/** Splits the Markdown a language model writes into paragraphs, headings and lists. */
export function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  for (const line of text.replace(/\r/g, '').split('\n')) {
    const item = LIST_ITEM.exec(line);
    const heading = HEADING.exec(line.trim());
    const last = blocks.at(-1);
    if (item) {
      const ordered = /\d/.test(item[2] ?? '');
      const entry = { text: item[3] ?? '', nested: (item[1] ?? '').length >= 2 };
      if (last?.kind === 'list' && (last.ordered === ordered || entry.nested)) {
        last.items.push(entry);
      } else {
        blocks.push({ kind: 'list', ordered, items: [entry] });
      }
    } else if (heading) {
      blocks.push({ kind: 'heading', text: heading[1] ?? '' });
    } else if (line.trim() === '') {
      blocks.push({ kind: 'paragraph', lines: [] });
    } else if (last?.kind === 'paragraph') {
      last.lines.push(line.trim());
    } else {
      blocks.push({ kind: 'paragraph', lines: [line.trim()] });
    }
  }
  return blocks.filter((block) => block.kind !== 'paragraph' || block.lines.length > 0);
}

/** **bold** and `code`; everything else stays plain text, so nothing the model writes becomes markup. */
export function renderInline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return <code key={index}>{part.slice(1, -1)}</code>;
    }
    return part;
  });
}

export function Markdown({ text }: { text: string }) {
  return (
    <div className="markdown">
      {parseBlocks(text).map((block, index) => {
        if (block.kind === 'heading') {
          return (
            <p key={index} className="markdown-heading">
              {renderInline(block.text)}
            </p>
          );
        }
        if (block.kind === 'list') {
          const List = block.ordered ? 'ol' : 'ul';
          return (
            <List key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex} className={item.nested ? 'nested' : undefined}>
                  {renderInline(item.text)}
                </li>
              ))}
            </List>
          );
        }
        return (
          <p key={index}>
            {block.lines.map((line, lineIndex) => (
              <Fragment key={lineIndex}>
                {lineIndex > 0 && <br />}
                {renderInline(line)}
              </Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}
