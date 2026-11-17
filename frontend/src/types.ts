// Khớp với JSON do backend (Java) trả về.

export interface Round {
  id: string
  /** Cùng độ dài với players, tổng bằng 0. */
  scores: number[]
}

export interface Game {
  id: string
  name: string
  players: string[]
  rounds: Round[]
  createdAt: number
  updatedAt: number
}

export interface GameSummary {
  id: string
  name: string
  playerCount: number
  roundCount: number
  updatedAt: number
}
