import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { transactionApi, accountApi, getErrorMessage } from '../lib/api'
import toast from 'react-hot-toast'
import PageHeader from '../components/PageHeader'
import Spinner from '../components/Spinner'
import Modal from '../components/Modal'
import EmptyState from '../components/EmptyState'
import { TransactionTypeBadge, TransactionStatusBadge } from '../components/TransactionBadge'
import { ArrowLeftRight, Plus, ChevronLeft, ChevronRight } from 'lucide-react'
import { format } from 'date-fns'

const TXN_TYPES = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER']

export default function TransactionsPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [showModal, setShowModal] = useState(false)
  const [txnType, setTxnType] = useState('DEPOSIT')
  const [form, setForm] = useState({ accountNumber: '', fromAccountNumber: '', toAccountNumber: '', amount: '', description: '' })

  const { data: txnPage, isLoading } = useQuery({
    queryKey: ['transactions', { page, size: 15 }],
    queryFn: () => transactionApi.list({ page, size: 15 }).then((r) => r.data.data),
  })

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountApi.list().then((r) => r.data.data),
  })

  const mutation = useMutation({
    mutationFn: (payload) => {
      if (txnType === 'DEPOSIT')    return transactionApi.deposit(payload)
      if (txnType === 'WITHDRAWAL') return transactionApi.withdraw(payload)
      return transactionApi.transfer(payload)
    },
    onSuccess: () => {
      toast.success('Transaction completed')
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      setShowModal(false)
      setForm({ accountNumber: '', fromAccountNumber: '', toAccountNumber: '', amount: '', description: '' })
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Transaction failed')),
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    const amount = parseFloat(form.amount)
    if (!amount || amount <= 0) { toast.error('Enter a valid amount'); return }

    if (txnType === 'TRANSFER') {
      if (!form.fromAccountNumber || !form.toAccountNumber) {
        toast.error('Both account numbers are required')
        return
      }
      mutation.mutate({ transactionType: 'TRANSFER', amount, fromAccountNumber: form.fromAccountNumber, toAccountNumber: form.toAccountNumber, description: form.description })
    } else {
      if (!form.accountNumber) { toast.error('Select an account'); return }
      mutation.mutate({ transactionType: txnType, amount, accountNumber: form.accountNumber, description: form.description })
    }
  }

  const transactions = txnPage?.content ?? []
  const totalPages = txnPage?.totalPages ?? 1
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE')

  return (
    <div>
      <PageHeader
        title="Transactions"
        subtitle="Full history of your banking activity"
        action={
          <button onClick={() => setShowModal(true)} className="btn-primary">
            <Plus size={16} /> New Transaction
          </button>
        }
      />

      <div className="card">
        {isLoading ? (
          <div className="flex justify-center py-16"><Spinner size="lg" /></div>
        ) : transactions.length === 0 ? (
          <EmptyState
            icon={ArrowLeftRight}
            title="No transactions yet"
            subtitle="Make a deposit, withdrawal, or transfer to get started"
            action={
              <button onClick={() => setShowModal(true)} className="btn-primary">
                <Plus size={16} /> New Transaction
              </button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-400 border-b border-gray-100">
                    <th className="pb-3 font-medium">Reference</th>
                    <th className="pb-3 font-medium">Type</th>
                    <th className="pb-3 font-medium">From</th>
                    <th className="pb-3 font-medium">To</th>
                    <th className="pb-3 font-medium">Amount</th>
                    <th className="pb-3 font-medium">Status</th>
                    <th className="pb-3 font-medium">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {transactions.map((txn) => (
                    <tr key={txn.id} className="hover:bg-gray-50/50">
                      <td className="py-3 font-mono text-xs text-gray-500">{txn.transactionReference}</td>
                      <td className="py-3"><TransactionTypeBadge type={txn.transactionType} /></td>
                      <td className="py-3 font-mono text-xs text-gray-500">{txn.fromAccountNumber ?? '—'}</td>
                      <td className="py-3 font-mono text-xs text-gray-500">{txn.toAccountNumber ?? '—'}</td>
                      <td className={`py-3 font-semibold ${
                        txn.transactionType === 'DEPOSIT' ? 'text-green-600' : 'text-red-600'
                      }`}>
                        {txn.transactionType === 'DEPOSIT' ? '+' : '-'}
                        ${Number(txn.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </td>
                      <td className="py-3"><TransactionStatusBadge status={txn.status} /></td>
                      <td className="py-3 text-gray-400 text-xs whitespace-nowrap">
                        {format(new Date(txn.createdAt), 'MMM d, yyyy h:mm a')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-100">
                <p className="text-sm text-gray-400">
                  Page {page + 1} of {totalPages}
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="btn-secondary py-1.5 px-3 disabled:opacity-40"
                  >
                    <ChevronLeft size={16} />
                  </button>
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="btn-secondary py-1.5 px-3 disabled:opacity-40"
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* New Transaction Modal */}
      <Modal open={showModal} onClose={() => setShowModal(false)} title="New Transaction" maxWidth="max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Type selector */}
          <div className="flex gap-2">
            {TXN_TYPES.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setTxnType(t)}
                className={`flex-1 py-2 rounded-lg text-xs font-medium border transition-colors ${
                  txnType === t
                    ? 'bg-brand-600 text-white border-brand-600'
                    : 'bg-white text-gray-600 border-gray-200 hover:border-brand-300'
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          {txnType === 'TRANSFER' ? (
            <>
              <div>
                <label className="label">From account</label>
                <select
                  className="input"
                  value={form.fromAccountNumber}
                  onChange={(e) => setForm((f) => ({ ...f, fromAccountNumber: e.target.value }))}
                  disabled={mutation.isPending}
                >
                  <option value="">Select account</option>
                  {activeAccounts.map((a) => (
                    <option key={a.id} value={a.accountNumber}>
                      {a.accountNumber} — ${Number(a.balance).toFixed(2)}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">To account number</label>
                <input
                  className="input font-mono"
                  placeholder="ACC123456789012"
                  value={form.toAccountNumber}
                  onChange={(e) => setForm((f) => ({ ...f, toAccountNumber: e.target.value }))}
                  disabled={mutation.isPending}
                />
              </div>
            </>
          ) : (
            <div>
              <label className="label">Account</label>
              <select
                className="input"
                value={form.accountNumber}
                onChange={(e) => setForm((f) => ({ ...f, accountNumber: e.target.value }))}
                disabled={mutation.isPending}
              >
                <option value="">Select account</option>
                {activeAccounts.map((a) => (
                  <option key={a.id} value={a.accountNumber}>
                    {a.accountNumber} — ${Number(a.balance).toFixed(2)}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="label">Amount ($)</label>
            <input
              type="number"
              min="0.01"
              step="0.01"
              className="input"
              placeholder="0.00"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              disabled={mutation.isPending}
            />
          </div>
          <div>
            <label className="label">Description (optional)</label>
            <input
              className="input"
              placeholder="What's this for?"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              disabled={mutation.isPending}
            />
          </div>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={() => setShowModal(false)} className="btn-secondary flex-1">
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1" disabled={mutation.isPending}>
              {mutation.isPending ? <Spinner size="sm" /> : 'Submit'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
