import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createGame, deleteGame, listGames } from '../api'
import type { Game, GameSummary } from '../types'
import HomePage from './HomePage'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, listGames: vi.fn(), createGame: vi.fn(), deleteGame: vi.fn() }
})

function GameStub() {
  const { id } = useParams()
  return <p>GAME PAGE {id}</p>
}

function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/g/:id" element={<GameStub />} />
      </Routes>
    </MemoryRouter>,
  )
}

const nameInputs = () => screen.getAllByLabelText(/^Tên người chơi \d+$/) as HTMLInputElement[]

beforeEach(() => {
  vi.mocked(listGames).mockReset().mockResolvedValue([])
  vi.mocked(createGame).mockReset()
  vi.mocked(deleteGame).mockReset()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('HomePage', () => {
  it('lists existing games as links to their page', async () => {
    vi.mocked(listGames).mockResolvedValue([
      { id: 'g1', name: 'Tối nay', playerCount: 3, roundCount: 2, updatedAt: 1_700_000_000_000 },
    ])
    renderHome()

    const link = await screen.findByRole('link', { name: /Tối nay/ })
    expect(link).toHaveAttribute('href', '/g/g1')
    expect(link).toHaveTextContent('3 người · 2 ván')
  })

  it('shows a hint when there are no games yet', async () => {
    renderHome()

    expect(await screen.findByText(/Chưa có bàn nào/)).toBeInTheDocument()
  })

  it('starts with 4 players named Người 1..4', async () => {
    renderHome()
    await screen.findByText(/Chưa có bàn nào/)

    expect(nameInputs().map((input) => input.value)).toEqual(['Người 1', 'Người 2', 'Người 3', 'Người 4'])
  })

  it('keeps typed names when the number of players changes and fills new ones with defaults', async () => {
    const user = userEvent.setup()
    renderHome()
    await screen.findByText(/Chưa có bàn nào/)
    const count = screen.getByLabelText('Số người chơi')

    await user.selectOptions(count, '6')
    expect(nameInputs()).toHaveLength(6)

    await user.clear(nameInputs()[0])
    await user.type(nameInputs()[0], 'An')
    await user.selectOptions(count, '3')
    expect(nameInputs().map((input) => input.value)).toEqual(['An', 'Người 2', 'Người 3'])

    await user.selectOptions(count, '6')
    expect(nameInputs().map((input) => input.value)).toEqual([
      'An', 'Người 2', 'Người 3', 'Người 4', 'Người 5', 'Người 6',
    ])
  })

  it('creates the game with the typed name and players, then opens its page', async () => {
    const user = userEvent.setup()
    vi.mocked(createGame).mockResolvedValue({ id: 'abc' } as Game)
    renderHome()
    await screen.findByText(/Chưa có bàn nào/)

    await user.type(screen.getByLabelText(/Tên bàn/), 'Tối thứ bảy')
    await user.click(screen.getByRole('button', { name: 'Tạo bàn' }))

    expect(createGame).toHaveBeenCalledWith('Tối thứ bảy', ['Người 1', 'Người 2', 'Người 3', 'Người 4'])
    expect(await screen.findByText('GAME PAGE abc')).toBeInTheDocument()
  })

  it('shows the server error under the form and stays on the page', async () => {
    const user = userEvent.setup()
    vi.mocked(createGame).mockRejectedValue(new ApiError(400, 'Tên người chơi không được để trống.'))
    renderHome()
    await screen.findByText(/Chưa có bàn nào/)

    await user.clear(nameInputs()[1])
    await user.click(screen.getByRole('button', { name: 'Tạo bàn' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Tên người chơi không được để trống.')
    expect(screen.queryByText(/GAME PAGE/)).not.toBeInTheDocument()
  })
})

describe('HomePage deleting a game', () => {
  const twoGames: GameSummary[] = [
    { id: 'g1', name: 'Tối nay', playerCount: 3, roundCount: 2, updatedAt: 1_700_000_000_000 },
    { id: 'g2', name: 'Cuối tuần', playerCount: 4, roundCount: 0, updatedAt: 1_700_000_100_000 },
  ]

  beforeEach(() => {
    vi.mocked(listGames).mockResolvedValue(twoGames)
  })

  it('asks for confirmation, deletes that game and removes only it from the list', async () => {
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(deleteGame).mockResolvedValue(undefined)
    renderHome()

    await user.click(await screen.findByRole('button', { name: 'Xóa bàn Tối nay' }))

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Tối nay'))
    expect(deleteGame).toHaveBeenCalledExactlyOnceWith('g1')
    await waitFor(() => expect(screen.queryByRole('link', { name: /Tối nay/ })).not.toBeInTheDocument())
    expect(screen.getByRole('link', { name: /Cuối tuần/ })).toBeInTheDocument()
  })

  it('does nothing when the user cancels the confirmation', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderHome()

    await user.click(await screen.findByRole('button', { name: 'Xóa bàn Tối nay' }))

    expect(deleteGame).not.toHaveBeenCalled()
    expect(screen.getByRole('link', { name: /Tối nay/ })).toBeInTheDocument()
  })

  it('keeps the game and shows the server error when deleting fails', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(deleteGame).mockRejectedValue(new ApiError(500, 'Không ghi được dữ liệu, thử lại sau.'))
    renderHome()

    await user.click(await screen.findByRole('button', { name: 'Xóa bàn Tối nay' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Không ghi được dữ liệu, thử lại sau.')
    expect(screen.getByRole('link', { name: /Tối nay/ })).toBeInTheDocument()
  })

  it('drops the game from the list without an error when it was already deleted elsewhere', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(deleteGame).mockRejectedValue(new ApiError(404, 'Không tìm thấy bàn.'))
    renderHome()

    await user.click(await screen.findByRole('button', { name: 'Xóa bàn Tối nay' }))

    await waitFor(() => expect(screen.queryByRole('link', { name: /Tối nay/ })).not.toBeInTheDocument())
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
