import type { Game, GameSummary } from './types'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Không kết nối được tới máy chủ.')
  }

  const text = await response.text()
  let data: unknown = null
  try {
    data = text === '' ? null : JSON.parse(text)
  } catch {
    // phản hồi không phải JSON; dùng thông báo mặc định bên dưới nếu là lỗi
  }

  if (!response.ok) {
    const message =
      typeof data === 'object' && data !== null && 'error' in data && typeof data.error === 'string'
        ? data.error
        : `Lỗi ${response.status}`
    throw new ApiError(response.status, message)
  }
  return data as T
}

const gamePath = (id: string) => `/api/games/${encodeURIComponent(id)}`

export const listGames = () => request<GameSummary[]>('GET', '/api/games')

export const createGame = (name: string, players: string[]) =>
  request<Game>('POST', '/api/games', { name, players })

export const getGame = (id: string) => request<Game>('GET', gamePath(id))

export const deleteGame = (id: string) => request<void>('DELETE', gamePath(id))

export const addRound =(id: string, scores: number[]) =>
  request<Game>('POST', `${gamePath(id)}/rounds`, { scores })

export const updateRound = (id: string, roundId: string, scores: number[]) =>
  request<Game>('PUT', `${gamePath(id)}/rounds/${encodeURIComponent(roundId)}`, { scores })

export const deleteRound = (id: string, roundId: string) =>
  request<Game>('DELETE', `${gamePath(id)}/rounds/${encodeURIComponent(roundId)}`)

export const renamePlayers = (id: string, players: string[]) =>
  request<Game>('PUT', `${gamePath(id)}/players`, { players })

export const eventsUrl = (id: string) => `${gamePath(id)}/events`
