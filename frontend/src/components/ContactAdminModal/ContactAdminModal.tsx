import React, { useState } from 'react'
import { Dialog, Button, toast } from '@/components'

export interface ContactAdminModalProps {
  isOpen: boolean
  onClose: () => void
}

export const ContactAdminModal: React.FC<ContactAdminModalProps> = ({ isOpen, onClose }) => {
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [category, setCategory] = useState('GENERAL')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!message.trim()) return

    setIsSubmitting(true)
    setTimeout(() => {
      setIsSubmitting(false)
      toast.success('Your message has been dispatched to the Workspace Administrator.')
      setSubject('')
      setMessage('')
      onClose()
    }, 600)
  }

  return (
    <Dialog isOpen={isOpen} title="Contact Workspace Administrator" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4 py-1 text-left">
        <p className="text-xs text-slate-600">
          Have an urgent scheduling request, amenity inquiry, or room access question? Submit your message below to notify the campus administration team.
        </p>

        <div>
          <label htmlFor="admin-category" className="block text-xs font-semibold uppercase text-slate-700 mb-1">
            Category
          </label>
          <select
            id="admin-category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="GENERAL">General Inquiries</option>
            <option value="BOOKING_OVERRIDE">Booking Override / Priority Request</option>
            <option value="EQUIPMENT">Equipment & Amenity Support</option>
            <option value="ACCESS">Location & Room Access</option>
          </select>
        </div>

        <div>
          <label htmlFor="admin-subject" className="block text-xs font-semibold uppercase text-slate-700 mb-1">
            Subject
          </label>
          <input
            id="admin-subject"
            type="text"
            required
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder="e.g. Need larger room for Executive All-Hands"
            className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label htmlFor="admin-message" className="block text-xs font-semibold uppercase text-slate-700 mb-1">
            Message Details
          </label>
          <textarea
            id="admin-message"
            rows={4}
            required
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Provide relevant details such as expected capacity, timing, or required AV gear..."
            className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex justify-end gap-2.5 pt-3 border-t border-slate-200">
          <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={isSubmitting}>
            Send Message
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

export default ContactAdminModal
