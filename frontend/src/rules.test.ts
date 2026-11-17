import { describe, expect, it } from 'vitest'
import { computeTotals, evaluateRound, formatScore } from './rules'

describe('evaluateRound', () => {
  it('auto-fills the last blank cell so the round sums to zero', () => {
    const r = evaluateRound(['10', '-5', ''])
    expect(r.scores).toEqual([10, -5, -5])
    expect(r.autoIndex).toBe(2)
    expect(r.status).toBe('ok')
  })

  it('auto-fills a blank cell in the middle', () => {
    const r = evaluateRound(['10', '', '-5'])
    expect(r.autoIndex).toBe(1)
    expect(r.scores).toEqual([10, -5, -5])
  })

  it('fills in 0 and never -0 when the typed cells already sum to zero', () => {
    const r = evaluateRound(['5', '-5', ''])
    expect(Object.is(r.scores[2], 0)).toBe(true)
    expect(String((r.scores[2] as number).toLocaleString())).toBe('0')
  })

  it('reports empty when every cell is blank', () => {
    const r = evaluateRound(['', '', ''])
    expect(r.status).toBe('empty')
    expect(r.autoIndex).toBeNull()
  })

  it('reports incomplete when two or more cells are blank but some are filled', () => {
    expect(evaluateRound(['10', '', '']).status).toBe('incomplete')
  })

  it('reports unbalanced with the running sum when all cells are filled but do not sum to zero', () => {
    const r = evaluateRound(['10', '-4', '-5'])
    expect(r.status).toBe('unbalanced')
    expect(r.sum).toBe(1)
  })

  it('accepts a fully filled round that sums to zero without auto-filling', () => {
    const r = evaluateRound(['10', '-10', '0'])
    expect(r.status).toBe('ok')
    expect(r.autoIndex).toBeNull()
    expect(r.scores).toEqual([10, -10, 0])
  })

  it.each([
    [['1.5', '2', ''], [0]],
    [['abc', '', ''], [0]],
    [['1e3', '', ''], [0]],
    [['--1', '', ''], [0]],
    [['1000000000', '', ''], [0]],
    [['3', 'x', ''], [1]],
  ])('flags invalid cells in %j', (cells, invalidIndexes) => {
    const r = evaluateRound(cells)
    expect(r.status).toBe('invalid')
    expect(r.invalidIndexes).toEqual(invalidIndexes)
    expect(r.autoIndex).toBeNull()
  })

  it('trims whitespace and accepts a leading plus', () => {
    expect(evaluateRound([' 7 ', '', '']).scores[0]).toBe(7)
    expect(evaluateRound(['+7', '', '']).scores[0]).toBe(7)
  })

  it('accepts the largest allowed score', () => {
    expect(evaluateRound(['999999999', '', '']).scores[0]).toBe(999999999)
  })
})

describe('computeTotals', () => {
  it('sums each player across rounds', () => {
    expect(computeTotals([{ scores: [10, -5, -5] }, { scores: [-3, 1, 2] }], 3)).toEqual([7, -4, -3])
  })

  it('returns zeros when there are no rounds', () => {
    expect(computeTotals([], 3)).toEqual([0, 0, 0])
  })
})

describe('formatScore', () => {
  it.each([
    [0, '0'],
    [-0, '0'],
    [5, '+5'],
    [-5, '-5'],
  ])('formats %j as %j', (n, text) => {
    expect(formatScore(n)).toBe(text)
  })
})
