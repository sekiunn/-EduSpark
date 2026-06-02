import { marked } from 'marked'
import katex from 'katex'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'

marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (e) {}
    }
    return hljs.highlightAuto(code).value
  },
  breaks: false,
  gfm: true
})

const renderMath = (text) => {
  if (!text) return ''

  let html = text

  html = html.replace(/\$\$(.+?)\$\$/gs, (match, formula) => {
    try { return katex.renderToString(formula.trim(), { displayMode: true, throwOnError: false }) }
    catch (e) { return match }
  })
  html = html.replace(/\$(.+?)\$/g, (match, formula) => {
    try { return katex.renderToString(formula.trim(), { displayMode: false, throwOnError: false }) }
    catch (e) { return match }
  })
  html = html.replace(/\\\[(.+?)\\\]/gs, (match, formula) => {
    try { return katex.renderToString(formula.trim(), { displayMode: true, throwOnError: false }) }
    catch (e) { return match }
  })
  html = html.replace(/\\\((.+?)\\\)/g, (match, formula) => {
    try { return katex.renderToString(formula.trim(), { displayMode: false, throwOnError: false }) }
    catch (e) { return match }
  })

  return html
}

export const renderMarkdown = (text) => {
  if (!text) return ''
  const rendered = marked.parse(renderMath(text))
  return DOMPurify.sanitize(rendered)
}

export const renderBody = (text) => {
  if (!text) return ''
  const body = text.split(/【参考文献】/)[0]
  let html = renderMarkdown(body)
  html = html.replace(/\[(\d+)\](?!\.)/g, '')
  return html
}

export const markdownToPlainText = (text) => {
  if (!text) return ''

  const html = renderMarkdown(text)
  if (typeof DOMParser !== 'undefined') {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    return (doc.body.textContent || '').replace(/\s+\n/g, '\n').trim()
  }

  return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}
