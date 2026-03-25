import http from './http'

export function fetchBookmarks() {
  return http.get('/bookmarks')
}

export function createBookmark(payload) {
  return http.post('/bookmarks', payload)
}

export function deleteBookmark(bookmarkId) {
  return http.delete(`/bookmarks/${bookmarkId}`)
}

export function fetchWords() {
  return http.get('/words')
}

export function createWord(payload) {
  return http.post('/words', payload)
}

export function deleteWord(wordId) {
  return http.delete(`/words/${wordId}`)
}
