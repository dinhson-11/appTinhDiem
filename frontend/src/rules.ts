// Luật tính điểm phía trình duyệt. Server (Rules.java) vẫn là nơi quyết định cuối cùng;
// bản này chỉ để tự điền và báo lỗi tức thì khi người dùng đang gõ.

export const MIN_PLAYERS = 2
export const MAX_PLAYERS = 12
export const MAX_NAME_LENGTH = 30

export type RoundStatus = 'empty' | 'incomplete' | 'invalid' | 'unbalanced' | 'ok'

export interface RoundEvaluation {
  /** Điểm từng người; ô chưa có giá trị là null. Ô tự điền đã được tính sẵn. */
  scores: (number | null)[]
  /** Chỉ số ô được tự điền, hoặc null nếu không có. */
  autoIndex: number | null
  status: RoundStatus
  invalidIndexes: number[]
  /** Tổng các ô người dùng đã nhập hợp lệ (không tính ô tự điền). */
  sum: number
}

const INTEGER = /^[+-]?\d{1,9}$/

export function evaluateRound(cells: string[]): RoundEvaluation {
  const scores: (number | null)[] = []
  const invalidIndexes: number[] = []
  const blankIndexes: number[] = []
  let sum = 0

  cells.forEach((cell, index) => {
    const text = cell.trim()
    if (text === '') {
      scores.push(null)
      blankIndexes.push(index)
    } else if (!INTEGER.test(text)) {
      scores.push(null)
      invalidIndexes.push(index)
    } else {
      const value = Number(text) || 0 // `|| 0` đổi -0 thành 0
      scores.push(value)
      sum += value
    }
  })

  if (invalidIndexes.length > 0) {
    return { scores, autoIndex: null, status: 'invalid', invalidIndexes, sum }
  }
  if (blankIndexes.length === 0) {
    return { scores, autoIndex: null, status: sum === 0 ? 'ok' : 'unbalanced', invalidIndexes, sum }
  }
  if (blankIndexes.length === 1) {
    const autoIndex = blankIndexes[0]
    scores[autoIndex] = -sum || 0 // `|| 0` đổi -0 thành 0
    return { scores, autoIndex, status: 'ok', invalidIndexes, sum }
  }
  const status = blankIndexes.length === cells.length ? 'empty' : 'incomplete'
  return { scores, autoIndex: null, status, invalidIndexes, sum }
}

export function computeTotals(rounds: { scores: number[] }[], playerCount: number): number[] {
  const totals = new Array<number>(playerCount).fill(0)
  for (const round of rounds) {
    round.scores.forEach((score, index) => {
      totals[index] += score
    })
  }
  return totals
}

export function formatScore(n: number): string {
  if (n === 0) return '0' // cũng đúng với -0
  return n > 0 ? `+${n}` : String(n)
}
