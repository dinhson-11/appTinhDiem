import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getGame } from '../api'
import type { Game } from '../types'
import GamePage from './GamePage'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, getGame: vi.fn() }
})

/** jsdom không có EventSource; bản giả này cho test tự phát sự kiện như server làm. */
class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  readyState = 1
  closed = false
  private listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>()

  constructor() {
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  /** Mất kết nối nhưng trình duyệt vẫn đang tự nối lại (ví dụ server vừa khởi động lại). */
  dropWhileRetrying() {
    this.readyState = FakeEventSource.CONNECTING
    this.onerror?.()
  }

  /** Trình duyệt bỏ cuộc, không nối lại nữa (ví dụ server trả 404 khi nối lại). */
  giveUp() {
    this.readyState = FakeEventSource.CLOSED
    this.onerror?.()
  }

  emit(type: string, data: string) {
    for (const listener of this.listeners.get(type) ?? []) {
      listener(new MessageEvent(type, { data }))
    }
  }
}

const game: Game = {
  id: 'g1',
  name: 'Tối nay',
  players: ['An', 'Bình', 'Chi'],
  rounds: [{ id: 'r1', scores: [10, -5, -5] }],
  createdAt: 1,
  updatedAt: 1,
}

function renderGamePage() {
  return render(
    <MemoryRouter initialEntries={['/g/g1']}>
      <Routes>
        <Route path="/" element={<p>HOME</p>} />
        <Route path="/g/:id" element={<GamePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
  vi.mocked(getGame).mockReset().mockResolvedValue(game)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('GamePage', () => {
  it('tells the viewer the game was deleted, offers the way home and stops listening', async () => {
    renderGamePage()
    await screen.findByRole('heading', { name: 'Tối nay' })
    const source = FakeEventSource.instances[0]

    act(() => source.emit('deleted', 'g1'))

    expect(await screen.findByRole('heading', { name: 'Bàn này đã bị xóa' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Tối nay' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Về trang chủ/ })).toHaveAttribute('href', '/')
    expect(source.closed).toBe(true)
  })

  it('shows the deleted notice when the browser gives up reconnecting and the server says the game is gone', async () => {
    renderGamePage()
    await screen.findByRole('heading', { name: 'Tối nay' })
    vi.mocked(getGame).mockRejectedValue(new ApiError(404, 'Không tìm thấy bàn.'))

    act(() => FakeEventSource.instances[0].giveUp())

    expect(await screen.findByRole('heading', { name: 'Bàn này đã bị xóa' })).toBeInTheDocument()
  })

  it('keeps showing the game as reconnecting while the browser is still retrying', async () => {
    renderGamePage()
    await screen.findByRole('heading', { name: 'Tối nay' })
    vi.mocked(getGame).mockRejectedValue(new ApiError(404, 'Không tìm thấy bàn.'))

    act(() => FakeEventSource.instances[0].dropWhileRetrying())

    expect(await screen.findByRole('status')).toHaveTextContent('Mất kết nối, đang nối lại…')
    expect(screen.getByRole('heading', { name: 'Tối nay' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Bàn này đã bị xóa' })).not.toBeInTheDocument()
  })

  it('does not call the game deleted when the server fails for another reason', async () => {
    renderGamePage()
    await screen.findByRole('heading', { name: 'Tối nay' })
    vi.mocked(getGame).mockRejectedValue(new ApiError(500, 'Lỗi 500'))

    act(() => FakeEventSource.instances[0].giveUp())

    await waitFor(() => expect(getGame).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('heading', { name: 'Tối nay' })).toBeInTheDocument()
  })
})
