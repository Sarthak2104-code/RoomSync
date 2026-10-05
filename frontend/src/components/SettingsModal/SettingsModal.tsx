import React from 'react'
import { Dialog, Button } from '@/components'
import { IntegrationPending } from '@/components/IntegrationPending/IntegrationPending'

export interface SettingsModalProps {
  isOpen: boolean
  onClose: () => void
}

export const SettingsModal: React.FC<SettingsModalProps> = ({ isOpen, onClose }) => {
  return (
    <Dialog isOpen={isOpen} title="User Preferences & Settings" onClose={onClose}>
      <div className="space-y-4 py-2 text-left">
        <IntegrationPending
          title="Personal Preferences & Notification Settings"
          description="Customizable default locations, notification channels (Slack/Email), and timezone overrides will be configurable here."
          badgeLabel="Settings Foundation Ready"
        />

        <div className="flex justify-end pt-2 border-t border-slate-100">
          <Button variant="primary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Dialog>
  )
}

export default SettingsModal
