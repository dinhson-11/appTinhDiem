import { useRef, useState } from 'react'
import type { KeyboardEvent, RefObject } from 'react'
import { ApiError } from '../api'
import { computeTotals, evaluateRound, formatScore, MAX_NAME_LENGTH } from '../rules'
import type { Game } from '../types'

export interface ScoreTableProps {
  game: Game
  onAddRound(scores: number[]): Promise<void>
  onUpdateRound(roundId: string, scores: number[]): Promise<void>
  onDeleteRound(roundId: string): Promise<void>
  onRenamePlayers(players: string[]): Promise<void>
}

const DELETED_ELSEWHERE = 'Ván này đã bị xóa ở thiết bị khác.'

const blankCells = (count: number) => Array<string>(count).fill('')

const messageOf = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra.')

const scoreClass = (n: number) => (n > 0 ? 'pos' : n < 0 ? 'neg' : 'zero')

/** Đổi dấu số đang gõ: "7" ↔ "-7"; ô trống thành "-" để gõ tiếp số âm. */
function toggleSign(text: string): string {
  const t = text.trim()
  if (t.startsWith('-')) return t.slice(1)
  if (t.startsWith('+')) return '-' + t.slice(1)
  return '-' + t
}

/** Dấu "-"/"+" đứng một mình là đang gõ dở, không phải số sai. */
const forEvaluation = (cells: string[]) =>
  cells.map((cell) => (cell.trim() === '-' || cell.trim() === '+' ? '' : cell))

interface InputRowProps {
  /** Nhãn hiển thị ở cột đầu, ví dụ "Ván mới" hoặc "Ván 1". */
  label: string
  rowLabel: string
  players: string[]
  cells: string[]
  onCellsChange(cells: string[]): void
  submitText: string
  busy: boolean
  onSubmit(scores: number[]): void
  onCancel?: () => void
  firstInputRef?: RefObject<HTMLInputElement | null>
}

/** Một hàng nhập điểm (ván mới hoặc đang sửa) cộng dòng gợi ý bên dưới. Điều khiển hoàn toàn bằng props. */
function InputRow(props: InputRowProps) {
  const { label, rowLabel, players, cells, onCellsChange, submitText, busy, onSubmit, onCancel, firstInputRef } = props
  const evaluation = evaluateRound(forEvaluation(cells))
  const canSave = evaluation.status === 'ok' && !busy

  const hint =
    evaluation.status === 'incomplete'
      ? 'Bỏ trống đúng 1 ô để tự tính'
      : evaluation.status === 'invalid'
        ? 'Chỉ nhập số nguyên'
        : evaluation.status === 'unbalanced'
          ? `Tổng đang là ${evaluation.sum}, cần bằng 0 — bỏ trống 1 ô để tự tính`
          : null

  function update(index: number, text: string) {
    onCellsChange(cells.map((cell, i) => (i === index ? text : cell)))
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' && canSave) {
      event.preventDefault()
      onSubmit(evaluation.scores as number[])
    } else if (event.key === 'Escape' && onCancel) {
      onCancel()
    }
  }

  return (
    <>
      <tr aria-label={rowLabel} className="input-row">
        <th scope="row">{label}</th>
        {players.map((player, index) => {
          const ariaLabel = `${label}, điểm ${player}`
          const isAuto = evaluation.autoIndex === index
          const isInvalid = evaluation.invalidIndexes.includes(index)
          return (
            <td key={index}>
              <div className="cell">
                <input
                  ref={index === 0 ? firstInputRef : undefined}
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
                  aria-label={ariaLabel}
                  aria-invalid={isInvalid || undefined}
                  className={isInvalid ? 'invalid' : isAuto ? 'auto' : undefined}
                  value={cells[index]}
                  placeholder={isAuto ? formatScore(evaluation.scores[index] as number) : ''}
                  onChange={(event) => update(index, event.target.value)}
                  onKeyDown={onKeyDown}
                />
                <button
                  type="button"
                  className="sign"
                  aria-label={`Đổi dấu, ${ariaLabel}`}
                  onClick={() => update(index, toggleSign(cells[index]))}
                >
                  ±
                </button>
              </div>
            </td>
          )
        })}
        <td className="actions">
          <button
            type="button"
            className="btn btn-primary btn-small"
            disabled={!canSave}
            onClick={() => onSubmit(evaluation.scores as number[])}
          >
            {submitText}
          </button>
          {onCancel && (
            <button type="button" className="btn btn-small" onClick={onCancel}>
              Hủy
            </button>
          )}
        </td>
      </tr>
      {hint && (
        <tr className="hint-row">
          <td colSpan={players.length + 2}>
            <p className="hint">{hint}</p>
          </td>
        </tr>
      )}
    </>
  )
}

/**
 * Bảng điểm: hàng tên và hàng TỔNG cố định ở trên, từng ván bên dưới, hàng nhập ván mới ở cuối.
 * Không tự gọi API. Bản nháp đang gõ nằm trong state của component này, độc lập với prop `game`,
 * nên cập nhật realtime từ thiết bị khác không làm mất chữ hay focus.
 */
export default function ScoreTable({ game, onAddRound, onUpdateRound, onDeleteRound, onRenamePlayers }: ScoreTableProps) {
  const playerCount = game.players.length
  const totals = computeTotals(game.rounds, playerCount)

  const [newCells, setNewCells] = useState(() => blankCells(playerCount))
  const [editing, setEditing] = useState<{ roundId: string; cells: string[] } | null>(null)
  const [renaming, setRenaming] = useState<{ index: number; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const firstInputRef = useRef<HTMLInputElement>(null)
  const renameActive = useRef(false)

  // Ván đang sửa vừa bị xóa ở thiết bị khác: báo ngay và thoát chế độ sửa.
  if (editing !== null && !game.rounds.some((round) => round.id === editing.roundId)) {
    setEditing(null)
    setMessage(DELETED_ELSEWHERE)
  }

  async function perform(action: () => Promise<void>): Promise<boolean> {
    setBusy(true)
    setMessage(null)
    try {
      await action()
      return true
    } catch (error) {
      setMessage(messageOf(error))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function saveNew(scores: number[]) {
    if (await perform(() => onAddRound(scores))) {
      setNewCells(blankCells(playerCount))
      firstInputRef.current?.focus()
    }
  }

  async function saveEdit(roundId: string, scores: number[]) {
    setBusy(true)
    setMessage(null)
    try {
      await onUpdateRound(roundId, scores)
      setEditing(null)
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setEditing(null)
        setMessage(DELETED_ELSEWHERE)
      } else {
        setMessage(messageOf(error))
      }
    } finally {
      setBusy(false)
    }
  }

  async function remove(roundId: string, index: number) {
    if (!window.confirm(`Xóa ván ${index + 1}?`)) return
    await perform(() => onDeleteRound(roundId))
  }

  function startEdit(roundId: string, scores: number[]) {
    setMessage(null)
    // Điền sẵn N−1 ô đầu, để trống ô cuối: ô cuối tự điền, đúng quy tắc mặc định.
    setEditing({ roundId, cells: [...scores.slice(0, -1).map(String), ''] })
  }

  function startRename(index: number) {
    renameActive.current = true
    setRenaming({ index, text: game.players[index] })
  }

  function commitRename() {
    if (!renameActive.current || renaming === null) return
    renameActive.current = false
    const { index, text } = renaming
    setRenaming(null)
    const name = text.trim()
    if (name === '' || name === game.players[index]) return
    void perform(() => onRenamePlayers(game.players.map((player, i) => (i === index ? name : player))))
  }

  return (
    <>
      {message && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
      <div className="table-wrap">
        <table className="score-table">
          <thead>
            <tr className="names">
              <th scope="col">Người chơi</th>
              {game.players.map((name, index) => (
                <th scope="col" key={index}>
                  {renaming?.index === index ? (
                    <input
                      autoFocus
                      type="text"
                      aria-label={`Tên người chơi ${index + 1}`}
                      maxLength={MAX_NAME_LENGTH}
                      value={renaming.text}
                      onChange={(event) => setRenaming({ index, text: event.target.value })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') commitRename()
                        else if (event.key === 'Escape') {
                          renameActive.current = false
                          setRenaming(null)
                        }
                      }}
                      onBlur={commitRename}
                    />
                  ) : (
                    <button type="button" className="name-btn" title="Bấm để đổi tên" onClick={() => startRename(index)}>
                      {name}
                    </button>
                  )}
                </th>
              ))}
              <th scope="col">
                <span className="sr-only">Thao tác</span>
              </th>
            </tr>
            <tr className="totals" aria-label="Tổng">
              <th scope="row">TỔNG</th>
              {totals.map((total, index) => (
                <td key={index} className={scoreClass(total)}>
                  {formatScore(total)}
                </td>
              ))}
              <td />
            </tr>
          </thead>
          <tbody>
            {game.rounds.map((round, index) =>
              editing?.roundId === round.id ? (
                <InputRow
                  key={round.id}
                  label={`Ván ${index + 1}`}
                  rowLabel={`Sửa ván ${index + 1}`}
                  players={game.players}
                  cells={editing.cells}
                  onCellsChange={(cells) => setEditing({ roundId: round.id, cells })}
                  submitText="Lưu"
                  busy={busy}
                  onSubmit={(scores) => void saveEdit(round.id, scores)}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <tr key={round.id} aria-label={`Ván ${index + 1}`}>
                  <th scope="row">Ván {index + 1}</th>
                  {round.scores.map((score, scoreIndex) => (
                    <td key={scoreIndex} className={scoreClass(score)}>
                      {formatScore(score)}
                    </td>
                  ))}
                  <td className="actions">
                    <button type="button" className="btn btn-small" onClick={() => startEdit(round.id, round.scores)}>
                      Sửa
                    </button>
                    <button
                      type="button"
                      className="btn btn-small btn-danger"
                      onClick={() => void remove(round.id, index)}
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              ),
            )}
            <InputRow
              label="Ván mới"
              rowLabel="Ván mới"
              players={game.players}
              cells={newCells}
              onCellsChange={setNewCells}
              submitText="Lưu ván"
              busy={busy}
              onSubmit={(scores) => void saveNew(scores)}
              firstInputRef={firstInputRef}
            />
          </tbody>
        </table>
      </div>
    </>
  )
}
