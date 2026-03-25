import http from './http'

export function fetchParagraphs(chapterId) {
  return http.get(`/read/${chapterId}/paragraphs`)
}

export function translateParagraph(paragraphId) {
  return http.post(`/read/paragraph/${paragraphId}/translate`)
}

export function fetchFurigana(paragraphId) {
  return http.post(`/read/paragraph/${paragraphId}/furigana`)
}

export function analyzeGrammar(paragraphId) {
  return http.post(`/read/paragraph/${paragraphId}/grammar`)
}

export function lookupWord(payload) {
  return http.post('/read/word/definition', payload)
}
