import { ref } from 'vue'
import { defineStore } from 'pinia'
import { deleteBook, fetchBookDetail, fetchBooks, uploadBook } from '@/api/book'

export const useBookStore = defineStore('book', () => {
  const books = ref([])
  const currentBook = ref(null)

  async function loadBooks() {
    const { data } = await fetchBooks()
    books.value = data
    return data
  }

  async function loadBook(bookId) {
    const { data } = await fetchBookDetail(bookId)
    currentBook.value = data
    return data
  }

  async function uploadNovel(file) {
    const { data } = await uploadBook(file)
    currentBook.value = data
    return data
  }

  async function removeBook(bookId) {
    await deleteBook(bookId)
    books.value = books.value.filter((book) => book.id !== bookId)
    if (currentBook.value?.id === bookId) {
      currentBook.value = null
    }
  }

  return {
    books,
    currentBook,
    loadBooks,
    loadBook,
    uploadNovel,
    removeBook,
  }
})
