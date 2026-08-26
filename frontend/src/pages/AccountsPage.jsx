import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { accountApi, getErrorMessage } from '../lib/api'
import toast from 'react-hot-toast'
import PageHeader from '../components/PageHeader'
import Spinner from '../components/Spinner'
import Modal from '../components/Modal'
import EmptyState from '../components/EmptyState'
import { CreditCard, Plus, ArrowUpRight } from 'lucide-react'

const ACCOUNT_TYPES = ['SAVINGS', 'CHECKING', 'FIXED_DEPOSIT']

export default function AccountsPage() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({ accountHolderName: '', accountType: 'SAVINGS' })

  const { data: accounts = [], isLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountApi.list().then((r) => r.data.data),
  })

  const createMutation = useMutation({
    mutationFn: accountApi.create,
    onSuccess: () => {
      toast.success('Account created successfully')
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      setShowCreate(false)
      setForm({ accountHolderName: '', accountType: 'SAVINGS' })
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Failed to create account')),
  })

  const handleCreate = (e) => {
    e.preventDefault()
    if (!form.accountHolderName) { toast.error('Account holder name is required'); return }
    createMutation.mutate(form)
  }

  const statusColor = {
    ACTIVE:    'bg-green-100 text-green-700',
    INACTIVE:  'bg-gray-100 text-gray-600',
    SUSPENDED: 'bg-yellow-100 text-yellow-700',
    CLOSED:    'bg-red-100 text-red-700',
  }

  return (
    <div>
      <PageHeader
        title="Accounts"
        subtitle="Manage your bank accounts"
        action={
          <button onClick={() => setShowCreate(true)} className="btn-primary">
            <Plus size={16} /> New Account
          </button>
        }
      />

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : accounts.length === 0 ? (
        <EmptyState
          icon={CreditCard}
          title="No accounts yet"
          subtitle="Open your first account to start banking"
          action={
            <button onClick={() => setShowCreate(true)} className="btn-primary">
              <Plus size={16} /> Open Account
            </button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {accounts.map((account) => (
            <Link
              key={account.id}
              to={`/accounts/${account.id}`}
              className="card hover:shadow-md transition-shadow group"
            >
              <div className="flex items-start justify-between mb-4">
                <div className="p-2 bg-brand-50 rounded-lg">
                  <CreditCard size={20} className="text-brand-600" />
                </div>
                <span className={`badge ${statusColor[account.status] ?? 'bg-gray-100 text-gray-600'}`}>
                  {account.status}
                </span>
              </div>
              <p className="text-xs text-gray-400 mb-1">{account.accountType}</p>
              <p className="font-mono text-sm text-gray-600 mb-3">{account.accountNumber}</p>
              <p className="text-2xl font-bold text-gray-900">
                ${Number(account.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
              </p>
              <p className="text-xs text-gray-400 mt-1">{account.accountHolderName}</p>
              <div className="flex items-center gap-1 mt-4 text-brand-600 text-sm font-medium
                              opacity-0 group-hover:opacity-100 transition-opacity">
                View details <ArrowUpRight size={14} />
              </div>
            </Link>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Open New Account">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="label" htmlFor="accountHolderName">Account holder name</label>
            <input
              id="accountHolderName"
              className="input"
              placeholder="Full name on account"
              value={form.accountHolderName}
              onChange={(e) => setForm((f) => ({ ...f, accountHolderName: e.target.value }))}
              disabled={createMutation.isPending}
            />
          </div>
          <div>
            <label className="label" htmlFor="accountType">Account type</label>
            <select
              id="accountType"
              className="input"
              value={form.accountType}
              onChange={(e) => setForm((f) => ({ ...f, accountType: e.target.value }))}
              disabled={createMutation.isPending}
            >
              {ACCOUNT_TYPES.map((t) => (
                <option key={t} value={t}>{t.replace('_', ' ')}</option>
              ))}
            </select>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={() => setShowCreate(false)} className="btn-secondary flex-1">
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1" disabled={createMutation.isPending}>
              {createMutation.isPending ? <Spinner size="sm" /> : 'Open Account'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
