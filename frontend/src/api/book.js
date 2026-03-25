import http from './http'

export function fetchBooks() {
  return http.get('/books')
}

export function fetchBookDetail(bookId) {
  return http.get(`/books/${bookId}`)
}

export function deleteBook(bookId) {
  return http.delete(`/books/${bookId}`)
}

export function fetchChapters(bookId) {
  return http.get(`/books/${bookId}/chapters`)
}

export function fetchProgress(bookId) {
  return http.get(`/books/${bookId}/progress`)
}

export function saveProgress(bookId, payload) {
  return http.put(`/books/${bookId}/progress`, payload)
}

export function uploadBook(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/books/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}
