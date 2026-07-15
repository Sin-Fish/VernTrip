function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderInline(source) {
  if (!source) {
    return "";
  }

  const codeSpans = [];
  let text = escapeHtml(source).replace(/`([^`\n]+)`/g, (_, code) => {
    const token = `@@CODE_${codeSpans.length}@@`;
    codeSpans.push(`<code>${code}</code>`);
    return token;
  });

  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_, label, href) => {
    return `<a href="${href}" target="_blank" rel="noreferrer noopener">${label}</a>`;
  });
  text = text.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  text = text.replace(/(^|[^\*])\*([^*\n]+)\*(?!\*)/g, "$1<em>$2</em>");
  text = text.replace(/(^|[^\w])_([^_\n]+)_/g, "$1<em>$2</em>");

  return text.replace(/@@CODE_(\d+)@@/g, (_, index) => codeSpans[Number(index)] ?? "");
}

function renderParagraph(lines) {
  const content = lines.map((line) => renderInline(line.trim())).join("<br />");
  return `<p>${content}</p>`;
}

function renderList(lines, ordered) {
  const tag = ordered ? "ol" : "ul";
  const itemPattern = ordered ? /^\d+\.\s+/ : /^[-*+]\s+/;
  const items = lines
    .map((line) => line.replace(itemPattern, ""))
    .map((line) => `<li>${renderInline(line.trim())}</li>`)
    .join("");
  return `<${tag}>${items}</${tag}>`;
}

function renderBlockquote(lines) {
  const inner = lines.map((line) => line.replace(/^>\s?/, ""));
  return `<blockquote>${renderBlocks(inner)}</blockquote>`;
}

function renderCodeBlock(lines, language) {
  const content = escapeHtml(lines.join("\n"));
  const className = language ? ` class="language-${escapeHtml(language)}"` : "";
  return `<pre><code${className}>${content}</code></pre>`;
}

function renderBlocks(lines) {
  const blocks = [];

  for (let index = 0; index < lines.length; ) {
    const line = lines[index];
    const trimmed = line.trim();

    if (!trimmed) {
      index += 1;
      continue;
    }

    const fenceMatch = trimmed.match(/^```([\w-]+)?$/);
    if (fenceMatch) {
      index += 1;
      const codeLines = [];
      while (index < lines.length && !lines[index].trim().startsWith("```")) {
        codeLines.push(lines[index]);
        index += 1;
      }
      if (index < lines.length) {
        index += 1;
      }
      blocks.push(renderCodeBlock(codeLines, fenceMatch[1] ?? ""));
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.*)$/);
    if (headingMatch) {
      const level = headingMatch[1].length;
      blocks.push(`<h${level}>${renderInline(headingMatch[2].trim())}</h${level}>`);
      index += 1;
      continue;
    }

    if (/^>\s?/.test(trimmed)) {
      const quoteLines = [];
      while (index < lines.length && /^>\s?/.test(lines[index].trim())) {
        quoteLines.push(lines[index]);
        index += 1;
      }
      blocks.push(renderBlockquote(quoteLines));
      continue;
    }

    if (/^\d+\.\s+/.test(trimmed)) {
      const listLines = [];
      while (index < lines.length && /^\d+\.\s+/.test(lines[index].trim())) {
        listLines.push(lines[index].trim());
        index += 1;
      }
      blocks.push(renderList(listLines, true));
      continue;
    }

    if (/^[-*+]\s+/.test(trimmed)) {
      const listLines = [];
      while (index < lines.length && /^[-*+]\s+/.test(lines[index].trim())) {
        listLines.push(lines[index].trim());
        index += 1;
      }
      blocks.push(renderList(listLines, false));
      continue;
    }

    const paragraphLines = [];
    while (index < lines.length) {
      const candidate = lines[index];
      const candidateTrimmed = candidate.trim();
      if (
        !candidateTrimmed
        || candidateTrimmed.startsWith("```")
        || /^(#{1,6})\s+/.test(candidateTrimmed)
        || /^>\s?/.test(candidateTrimmed)
        || /^\d+\.\s+/.test(candidateTrimmed)
        || /^[-*+]\s+/.test(candidateTrimmed)
      ) {
        break;
      }
      paragraphLines.push(candidate);
      index += 1;
    }
    blocks.push(renderParagraph(paragraphLines));
  }

  return blocks.join("");
}

export function renderMarkdownToHtml(source) {
  if (typeof source !== "string" || !source.trim()) {
    return "";
  }

  const normalized = source.replace(/\r\n?/g, "\n");
  return renderBlocks(normalized.split("\n"));
}
