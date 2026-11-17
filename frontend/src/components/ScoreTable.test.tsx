import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import type { Game } from '../types'
import ScoreTable from './ScoreTable'

const game: Game = {
  id: 'g1',
  name: 'Bàn thử',
  players: ['An', 'Bình', 'Chi'],
  rounds: [{ id: 'r1', scores: [10, -5, -5] }],
  createdAt: 1,
  updatedAt: 1,
}

function setup(initial: Game = game) {
  const handlers = {
    onAddRound: vi.fn().mockResolvedValue(undefined),
    onUpdateRound: vi.fn().mockResolvedValue(undefined),
    onDeleteRound: vi.fn().mockResolvedValue(undefined),
    onRenamePlayers: vi.fn().mockResolvedValue(undefined),
  }
  const view = render(<ScoreTable game={initial} {...handlers} />)
  return {
    ...handlers,
    rerender: (next: Game) => view.rerender(<ScoreTable game={next} {...handlers} />),
  }
}

const newInput = (player: string) => screen.getByLabelText(`Ván mới, điểm ${player}`) as HTMLInputElement
const row = (name: string) => screen.getByRole('row', { name })

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ScoreTable display', () => {
  it('shows player names, the totals row and each round formatted as +n / -n', () => {
    setup()

    expect(screen.getByRole('button', { name: 'An' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bình' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Chi' })).toBeInTheDocument()

    const totals = within(row('Tổng'))
    expect(totals.getByRole('cell', { name: '+10' })).toBeInTheDocument()
    expect(totals.getAllByRole('cell', { name: '-5' })).toHaveLength(2)

    const round = within(row('Ván 1'))
    expect(round.getByRole('cell', { name: '+10' })).toBeInTheDocument()
    expect(round.getAllByRole('cell', { name: '-5' })).toHaveLength(2)
  })

  it('renders a player name containing HTML as plain text', () => {
    setup({ ...game, players: ['<b>x</b>', 'Bình', 'Chi'] })

    expect(screen.getByText('<b>x</b>')).toBeInTheDocument()
    expect(document.querySelector('b')).toBeNull()
  })
})

describe('ScoreTable new round row', () => {
  it('auto-fills the blank cell via placeholder and saves the full scores', async () => {
    const user = userEvent.setup()
    const { onAddRound } = setup()

    await user.type(newInput('An'), '10')
    await user.type(newInput('Bình'), '-5')

    expect(newInput('Chi').placeholder).toBe('-5')
    expect(newInput('Chi').value).toBe('')
    await user.click(screen.getByRole('button', { name: 'Lưu ván' }))

    expect(onAddRound).toHaveBeenCalledWith([10, -5, -5])
  })

  it('clears the row and refocuses the first cell after saving', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '10')
    await user.type(newInput('Bình'), '-5')
    await user.click(screen.getByRole('button', { name: 'Lưu ván' }))

    expect(newInput('An').value).toBe('')
    expect(newInput('Bình').value).toBe('')
    expect(newInput('An')).toHaveFocus()
  })

  it('saves with Enter when the round is valid', async () => {
    const user = userEvent.setup()
    const { onAddRound } = setup()

    await user.type(newInput('An'), '3')
    await user.type(newInput('Bình'), '4{Enter}')

    expect(onAddRound).toHaveBeenCalledWith([3, 4, -7])
  })

  it('shows 0, not -0, when the typed cells already sum to zero', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '5')
    await user.type(newInput('Bình'), '-5')

    expect(newInput('Chi').placeholder).toBe('0')
  })

  it('rejects decimals and keeps Save disabled', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '1.5')

    expect(screen.getByText('Chỉ nhập số nguyên')).toBeInTheDocument()
    expect(newInput('An')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('button', { name: 'Lưu ván' })).toBeDisabled()
  })

  it('explains an unbalanced round and keeps Save disabled', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '10')
    await user.type(newInput('Bình'), '-4')
    await user.type(newInput('Chi'), '-5')

    expect(screen.getByText(/Tổng đang là 1/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lưu ván' })).toBeDisabled()
  })

  it('asks for exactly one blank cell while several are still empty', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '10')

    expect(screen.getByText('Bỏ trống đúng 1 ô để tự tính')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lưu ván' })).toBeDisabled()
  })

  it('flips the sign of the typed number with the ± button', async () => {
    const user = userEvent.setup()
    setup()

    await user.type(newInput('An'), '7')
    await user.click(screen.getByRole('button', { name: 'Đổi dấu, Ván mới, điểm An' }))
    expect(newInput('An').value).toBe('-7')

    await user.click(screen.getByRole('button', { name: 'Đổi dấu, Ván mới, điểm An' }))
    expect(newInput('An').value).toBe('7')
  })

  it('shows the error when saving fails and keeps what was typed', async () => {
    const user = userEvent.setup()
    const { onAddRound } = setup()
    onAddRound.mockRejectedValue(new ApiError(400, 'Mỗi điểm chỉ được từ -999999999 đến 999999999.'))

    await user.type(newInput('An'), '10')
    await user.type(newInput('Bình'), '-5')
    await user.click(screen.getByRole('button', { name: 'Lưu ván' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi điểm chỉ được từ')
    expect(newInput('An').value).toBe('10')
  })

  it('keeps the typed draft and focus when a new game state arrives from another device', async () => {
    const user = userEvent.setup()
    const { rerender } = setup()

    await user.type(newInput('An'), '7')
    expect(newInput('An')).toHaveFocus()

    rerender({
      ...game,
      rounds: [...game.rounds, { id: 'r2', scores: [1, 1, -2] }],
      updatedAt: 2,
    })

    expect(within(row('Ván 2')).getAllByRole('cell')).not.toHaveLength(0)
    expect(newInput('An').value).toBe('7')
    expect(newInput('An')).toHaveFocus()
  })
})

describe('ScoreTable editing a round', () => {
  async function startEditing(user: ReturnType<typeof userEvent.setup>) {
    await user.click(within(row('Ván 1')).getByRole('button', { name: 'Sửa' }))
  }
  const editInput = (player: string) => screen.getByLabelText(`Ván 1, điểm ${player}`) as HTMLInputElement

  it('prefills all but the last cell and saves the corrected round', async () => {
    const user = userEvent.setup()
    const { onUpdateRound } = setup()

    await startEditing(user)
    expect(editInput('An').value).toBe('10')
    expect(editInput('Bình').value).toBe('-5')
    expect(editInput('Chi').value).toBe('')
    expect(editInput('Chi').placeholder).toBe('-5')

    await user.clear(editInput('An'))
    await user.type(editInput('An'), '4')
    await user.clear(editInput('Bình'))
    await user.type(editInput('Bình'), '-4')
    await user.click(within(row('Sửa ván 1')).getByRole('button', { name: 'Lưu' }))

    expect(onUpdateRound).toHaveBeenCalledWith('r1', [4, -4, 0])
  })

  it('leaves edit mode on Cancel without saving', async () => {
    const user = userEvent.setup()
    const { onUpdateRound } = setup()

    await startEditing(user)
    await user.click(within(row('Sửa ván 1')).getByRole('button', { name: 'Hủy' }))

    expect(onUpdateRound).not.toHaveBeenCalled()
    expect(within(row('Ván 1')).getByRole('button', { name: 'Sửa' })).toBeInTheDocument()
  })

  it('says the round was deleted elsewhere when saving gets a 404', async () => {
    const user = userEvent.setup()
    const { onUpdateRound } = setup()
    onUpdateRound.mockRejectedValue(new ApiError(404, 'Không tìm thấy ván.'))

    await startEditing(user)
    await user.click(within(row('Sửa ván 1')).getByRole('button', { name: 'Lưu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ván này đã bị xóa ở thiết bị khác')
    expect(screen.queryByRole('row', { name: 'Sửa ván 1' })).not.toBeInTheDocument()
  })

  it('says so right away when the round being edited disappears from the game', async () => {
    const user = userEvent.setup()
    const { rerender } = setup()

    await startEditing(user)
    rerender({ ...game, rounds: [], updatedAt: 2 })

    expect(await screen.findByRole('alert')).toHaveTextContent('Ván này đã bị xóa ở thiết bị khác')
    expect(screen.queryByRole('row', { name: 'Sửa ván 1' })).not.toBeInTheDocument()
  })
})

describe('ScoreTable deleting a round', () => {
  it('deletes after confirmation', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { onDeleteRound } = setup()

    await user.click(within(row('Ván 1')).getByRole('button', { name: 'Xóa' }))

    expect(onDeleteRound).toHaveBeenCalledWith('r1')
  })

  it('does nothing when the confirmation is declined', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { onDeleteRound } = setup()

    await user.click(within(row('Ván 1')).getByRole('button', { name: 'Xóa' }))

    expect(onDeleteRound).not.toHaveBeenCalled()
  })
})

describe('ScoreTable renaming players', () => {
  it('renames in place on Enter', async () => {
    const user = userEvent.setup()
    const { onRenamePlayers } = setup()

    await user.click(screen.getByRole('button', { name: 'An' }))
    const input = screen.getByRole('textbox', { name: 'Tên người chơi 1' })
    expect(input).toHaveValue('An')
    await user.clear(input)
    await user.type(input, 'Dũng{Enter}')

    expect(onRenamePlayers).toHaveBeenCalledWith(['Dũng', 'Bình', 'Chi'])
  })

  it('does not rename to an empty or unchanged name', async () => {
    const user = userEvent.setup()
    const { onRenamePlayers } = setup()

    await user.click(screen.getByRole('button', { name: 'An' }))
    await user.clear(screen.getByRole('textbox', { name: 'Tên người chơi 1' }))
    await user.keyboard('{Enter}')
    expect(onRenamePlayers).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Bình' }))
    await user.keyboard('{Enter}')
    expect(onRenamePlayers).not.toHaveBeenCalled()
  })
})
