import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import GamePage from './pages/GamePage'
import HomePage from './pages/HomePage'

function NotFoundPage() {
  return (
    <main className="page">
      <h1>Không tìm thấy trang</h1>
      <p>
        <Link to="/">← Về trang chủ</Link>
      </p>
    </main>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/g/:id" element={<GamePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}
