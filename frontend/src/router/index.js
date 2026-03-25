import { createRouter, createWebHistory } from 'vue-router'
import { hasUsableSession } from '@/utils/session'

const LoginView = () => import('@/views/LoginView.vue')
const RegisterView = () => import('@/views/RegisterView.vue')
const LibraryView = () => import('@/views/LibraryView.vue')
const UploadView = () => import('@/views/UploadView.vue')
const ReaderView = () => import('@/views/ReaderView.vue')
const BookmarksView = () => import('@/views/BookmarksView.vue')
const WordsView = () => import('@/views/WordsView.vue')
const ProfileView = () => import('@/views/ProfileView.vue')

const routes = [
  {
    path: '/',
    redirect: () => (hasUsableSession() ? '/library' : '/login'),
  },
  {
    path: '/login',
    component: LoginView,
    meta: { publicOnly: true },
  },
  {
    path: '/register',
    component: RegisterView,
    meta: { publicOnly: true },
  },
  {
    path: '/library',
    component: LibraryView,
    meta: { requiresAuth: true },
  },
  {
    path: '/library/upload',
    component: UploadView,
    meta: { requiresAuth: true },
  },
  {
    path: '/read/:bookId',
    component: ReaderView,
    meta: { requiresAuth: true },
  },
  {
    path: '/read/:bookId/chapter/:chapterId?',
    component: ReaderView,
    meta: { requiresAuth: true },
  },
  {
    path: '/bookmarks',
    component: BookmarksView,
    meta: { requiresAuth: true },
  },
  {
    path: '/words',
    component: WordsView,
    meta: { requiresAuth: true },
  },
  {
    path: '/profile',
    component: ProfileView,
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const hasSession = hasUsableSession()

  if (to.meta.requiresAuth && !hasSession) {
    return '/login'
  }

  if (to.meta.publicOnly && hasSession) {
    return '/library'
  }

  return true
})

export default router
