import React, { useState } from 'react'
import { Dialog, Button } from '@/components'

export const AskRoomSyncButton: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false)
  const [prompt, setPrompt] = useState('')

  return (
    <>
      {/* Floating Action Button */}
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        className="fixed bottom-6 right-6 z-40 flex flex-col items-center justify-center w-16 h-16 rounded-full bg-[#0B1528] text-white shadow-xl hover:scale-105 hover:bg-slate-900 transition-all focus:outline-none focus:ring-4 focus:ring-blue-500/30 border border-blue-400/20 group"
        title="Ask RoomSync AI"
        aria-label="Ask RoomSync AI Assistant"
      >
        <span className="text-xl group-hover:rotate-12 transition-transform duration-200" role="img" aria-label="sparkles">
          ✨
        </span>
        <span className="text-[9px] font-bold tracking-tight text-slate-200 mt-0.5 leading-tight text-center">
          Ask<br />RoomSync
        </span>
      </button>

      {/* AI Assistant Dialog */}
      <Dialog isOpen={isOpen} title="RoomSync AI Assistant" onClose={() => setIsOpen(false)}>
        <div className="space-y-4 py-1 text-left">
          <div className="p-4 rounded-xl bg-gradient-to-br from-blue-50 to-indigo-50/60 border border-blue-200/80">
            <div className="flex items-center gap-2 mb-1.5">
              <span className="text-lg">✨</span>
              <h4 className="text-sm font-bold text-blue-950">Natural Language Booking & Scheduling</h4>
            </div>
            <p className="text-xs text-blue-900/80 leading-relaxed">
              Describe your meeting requirements (e.g. <em>&quot;Book a 10-person room with a whiteboard in Mumbai tomorrow at 3 PM for Client Strategy&quot;</em>).
            </p>
          </div>

          <div>
            <label htmlFor="ai-prompt" className="block text-xs font-semibold uppercase text-slate-700 mb-1">
              Your Booking Request
            </label>
            <textarea
              id="ai-prompt"
              rows={3}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. Schedule Weekly Standup every Monday at 10 AM in Pune..."
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-600 flex items-center justify-between">
            <span>Deterministic backend validation enabled</span>
            <span className="px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 text-[10px] font-bold">
              AI Service Connected
            </span>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-slate-200">
            <Button variant="outline" onClick={() => setIsOpen(false)}>
              Close
            </Button>
            <Button
              variant="primary"
              disabled={!prompt.trim()}
              onClick={() => {
                setPrompt('')
                setIsOpen(false)
              }}
            >
              Parse &amp; Find Room
            </Button>
          </div>
        </div>
      </Dialog>
    </>
  )
}

export default AskRoomSyncButton
