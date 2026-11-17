import { Link, useParams } from 'react-router-dom'
import { addRound, deleteRound, renamePlayers, updateRound } from '../api'
import ScoreTable from '../components/ScoreTable'
import { useGame } from '../useGame'

function GameView({ id }: { id: string }) {
  const { game, connected, notFound, deleted } = useGame(id)

  if (deleted) {
    return (
      <main className="page">
        <h1>Bàn này đã bị xóa</h1>
        <p>Có người vừa xóa bàn này, nên không xem hay nhập điểm tiếp được nữa.</p>
        <p>
          <Link to="/">← Về trang chủ</Link>
        </p>
      </main>
    )
  }

  if (notFound) {
    return (
      <main className="page">
        <h1>Không tìm thấy bàn</h1>
        <p>Bàn này không tồn tại (có thể link bị sai hoặc dữ liệu đã bị xóa).</p>
        <p>
          <Link to="/">← Về trang chủ</Link>
        </p>
      </main>
    )
  }

  return (
    <main className="page game-page">
      <header className="game-header">
        <Link to="/">← Các bàn</Link>
        <h1>{game?.name ?? 'Đang tải…'}</h1>
        {game && (
          <span className={`status ${connected ? 'online' : 'offline'}`} role="status">
            {connected ? '● Trực tiếp' : 'Mất kết nối, đang nối lại…'}
          </span>
        )}
      </header>
      {game && (
        <ScoreTable
          game={game}
          onAddRound={async (scores) => {
            await addRound(id, scores)
          }}
          onUpdateRound={async (roundId, scores) => {
            await updateRound(id, roundId, scores)
          }}
          onDeleteRound={async (roundId) => {
            await deleteRound(id, roundId)
          }}
          onRenamePlayers={async (players) => {
            await renamePlayers(id, players)
          }}
        />
      )}
    </main>
  )
}

export default function GamePage() {
  const { id } = useParams()
  // key={id}: đổi sang bàn khác thì đặt lại toàn bộ state của trang.
  return id ? (
    <GameView key={id} id={id} />
  ) : (
    <main className="page">
      <h1>Thiếu mã bàn</h1>
      <Link to="/">← Về trang chủ</Link>
    </main>
  )
}
