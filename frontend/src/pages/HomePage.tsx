import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, createGame, deleteGame, listGames } from '../api'
import { MAX_NAME_LENGTH, MAX_PLAYERS, MIN_PLAYERS } from '../rules'
import type { GameSummary } from '../types'

const MAX_GAME_NAME_LENGTH = 40
const DEFAULT_PLAYER_COUNT = 4
const COUNT_OPTIONS = Array.from({ length: MAX_PLAYERS - MIN_PLAYERS + 1 }, (_, i) => MIN_PLAYERS + i)

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Có lỗi xảy ra.'
}

export default function HomePage() {
  const navigate = useNavigate()
  const [games, setGames] = useState<GameSummary[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [gameName, setGameName] = useState('')
  const [count, setCount] = useState(DEFAULT_PLAYER_COUNT)
  // Giữ đủ MAX_PLAYERS tên để đổi số người qua lại không làm mất tên đã gõ.
  const [names, setNames] = useState<string[]>(() =>
    Array.from({ length: MAX_PLAYERS }, (_, i) => `Người ${i + 1}`),
  )
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  useEffect(() => {
    listGames()
      .then(setGames)
      .catch((error: unknown) => setLoadError(errorMessage(error)))
  }, [])

  function setName(index: number, value: string) {
    setNames((current) => current.map((name, i) => (i === index ? value : name)))
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    try {
      const game = await createGame(gameName, names.slice(0, count))
      navigate(`/g/${game.id}`)
    } catch (error) {
      setFormError(errorMessage(error))
      setSubmitting(false)
    }
  }

  async function onDelete(game: GameSummary) {
    const ok = window.confirm(
      `Xóa bàn "${game.name}"? Toàn bộ ${game.roundCount} ván sẽ mất và không khôi phục được.`,
    )
    if (!ok) return
    setDeleteError(null)
    const removeFromList = () => setGames((current) => current && current.filter((g) => g.id !== game.id))
    try {
      await deleteGame(game.id)
      removeFromList()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        removeFromList() // đã bị xóa ở nơi khác, danh sách chỉ cần theo kịp
      } else {
        setDeleteError(errorMessage(error))
      }
    }
  }

  return (
    <main className="page">
      <h1>Tính điểm</h1>

      <section className="card">
        <h2>Tạo bàn mới</h2>
        <form onSubmit={onSubmit}>
          <label className="field">
            <span>Tên bàn (tùy chọn)</span>
            <input
              type="text"
              maxLength={MAX_GAME_NAME_LENGTH}
              value={gameName}
              placeholder="Ví dụ: Tối thứ bảy"
              onChange={(event) => setGameName(event.target.value)}
            />
          </label>

          <label className="field">
            <span>Số người chơi</span>
            <select value={count} onChange={(event) => setCount(Number(event.target.value))}>
              {COUNT_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>

          <div className="player-fields">
            {names.slice(0, count).map((name, index) => (
              <label className="field" key={index}>
                <span>Tên người chơi {index + 1}</span>
                <input
                  type="text"
                  maxLength={MAX_NAME_LENGTH}
                  value={name}
                  onChange={(event) => setName(index, event.target.value)}
                />
              </label>
            ))}
          </div>

          <button className="btn btn-primary" type="submit" disabled={submitting}>
            Tạo bàn
          </button>
          {formError && (
            <p className="error" role="alert">
              {formError}
            </p>
          )}
        </form>
      </section>

      <section className="card">
        <h2>Các bàn đã tạo</h2>
        {loadError && (
          <p className="error" role="alert">
            {loadError}
          </p>
        )}
        {games === null && !loadError && <p className="muted">Đang tải…</p>}
        {games !== null && games.length === 0 && (
          <p className="muted">Chưa có bàn nào. Tạo bàn đầu tiên ở trên nhé.</p>
        )}
        {deleteError && (
          <p className="error" role="alert">
            {deleteError}
          </p>
        )}
        {games !== null && games.length > 0 && (
          <ul className="game-list">
            {games.map((game) => (
              <li key={game.id}>
                <Link to={`/g/${game.id}`}>
                  <strong>{game.name}</strong>
                  <span className="muted">
                    {game.playerCount} người · {game.roundCount} ván ·{' '}
                    {new Date(game.updatedAt).toLocaleString('vi-VN')}
                  </span>
                </Link>
                <button
                  className="btn btn-small btn-danger"
                  type="button"
                  aria-label={`Xóa bàn ${game.name}`}
                  onClick={() => void onDelete(game)}
                >
                  Xóa
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
