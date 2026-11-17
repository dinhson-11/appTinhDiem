import { useEffect, useState } from 'react'
import { ApiError, eventsUrl, getGame } from './api'
import type { Game } from './types'

export interface UseGameResult {
  game: Game | null
  /** Luồng realtime đang mở. */
  connected: boolean
  /** Bàn không tồn tại (404). */
  notFound: boolean
  /** Bàn vừa bị xóa khi đang xem (server báo qua sự kiện `deleted`). */
  deleted: boolean
}

/**
 * Theo dõi một bàn: tải một lần để biết bàn có tồn tại không, rồi mở luồng SSE.
 * Mỗi sự kiện `game` mang toàn bộ trạng thái bàn; mất kết nối thì EventSource tự nối lại
 * và server gửi lại trạng thái đầy đủ. Dùng `key={id}` ở nơi gọi để đổi bàn thì đặt lại state.
 */
export function useGame(id: string): UseGameResult {
  const [game, setGame] = useState<Game | null>(null)
  const [connected, setConnected] = useState(false)
  const [notFound, setNotFound] = useState(false)
  const [deleted, setDeleted] = useState(false)

  useEffect(() => {
    let cancelled = false
    let source: EventSource | null = null

    async function start() {
      try {
        const initial = await getGame(id)
        if (cancelled) return
        setGame(initial)
      } catch (error) {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true)
          return // bàn không có thì không mở luồng
        }
        // lỗi khác (ví dụ server vừa tắt): vẫn mở luồng để EventSource tự nối lại khi server về
      }
      source = new EventSource(eventsUrl(id))
      source.onopen = () => setConnected(true)
      source.onerror = () => {
        setConnected(false)
        // Trình duyệt tự nối lại khi mất mạng; chỉ khi nó bỏ cuộc (server trả lỗi, thường là 404 vì bàn đã bị
        // xóa lúc máy này mất kết nối) mới cần hỏi lại server xem bàn còn không.
        if (source?.readyState === EventSource.CLOSED) {
          getGame(id).catch((error: unknown) => {
            if (!cancelled && error instanceof ApiError && error.status === 404) setDeleted(true)
          })
        }
      }
      source.addEventListener('game', (event) => {
        setGame(JSON.parse((event as MessageEvent<string>).data) as Game)
      })
      source.addEventListener('deleted', () => {
        // Đóng luồng, nếu không EventSource sẽ cứ nối lại tới một bàn không còn.
        source?.close()
        setConnected(false)
        setDeleted(true)
      })
    }
    void start()

    return () => {
      cancelled = true
      source?.close()
    }
  }, [id])

  return { game, connected, notFound, deleted }
}
