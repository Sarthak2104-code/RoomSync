import React from 'react'
import type { AuditLogResponse } from '@/types/audit'
import Modal from '@/components/Modal/Modal'
import Badge from '@/components/Badge/Badge'
import Button from '@/components/Button/Button'

interface AuditLogDetailModalProps {
  isOpen: boolean
  onClose: () => void
  auditLog: AuditLogResponse | null
}

export const AuditLogDetailModal: React.FC<AuditLogDetailModalProps> = ({
  isOpen,
  onClose,
  auditLog,
}) => {
  if (!auditLog) return null

  const formatTimestamp = (isoString?: string) => {
    if (!isoString) return '—'
    try {
      return new Date(isoString).toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  const getActionBadgeVariant = (action: string) => {
    if (action.includes('CANCEL') || action.includes('DEACTIVATE') || action.includes('DELETE')) {
      return 'error'
    }
    if (action.includes('LOCKED') || action.includes('LOCK')) {
      return 'warning'
    }
    if (action.includes('CREATED') || action.includes('ACTIVE') || action.includes('RESOLVED')) {
      return 'success'
    }
    return 'info'
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Audit Event #${auditLog.id} — ${auditLog.action}`}
      description="Immutable business/security audit log entry."
      size="lg"
    >
      <div className="space-y-5 pt-2 text-left">
        {/* Banner with Action and Entity */}
        <div className="flex items-center justify-between p-3.5 bg-slate-50 border border-slate-200 rounded-lg">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-brand-slate uppercase tracking-wider">
              Action:
            </span>
            <Badge variant={getActionBadgeVariant(auditLog.action)} size="md">
              {auditLog.action}
            </Badge>
          </div>
          <div className="text-xs font-mono text-brand-slate">
            Event ID: #{auditLog.id}
          </div>
        </div>

        {/* Actor and Affected User Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          {/* Actor */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Actor (Performed Action)
            </div>
            <div className="font-bold text-brand-navy flex items-center gap-2">
              {auditLog.actorWissenId && (
                <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200">
                  {auditLog.actorWissenId}
                </span>
              )}
              <span>{auditLog.actorName || 'SYSTEM'}</span>
            </div>
            {auditLog.actorEmail && (
              <div className="text-xs text-brand-slate mt-0.5">{auditLog.actorEmail}</div>
            )}
            {auditLog.actorUserId != null && (
              <div className="text-[11px] font-mono text-brand-slate mt-1">
                Internal ID: #{auditLog.actorUserId}
              </div>
            )}
          </div>

          {/* Affected User */}
          <div className="p-3 bg-white rounded-lg border border-brand-slate/20">
            <div className="text-xs font-semibold text-brand-slate uppercase tracking-wider mb-1">
              Affected User / Resource Owner
            </div>
            {auditLog.affectedUserName || auditLog.affectedUserWissenId || auditLog.affectedUserId != null ? (
              <>
                <div className="font-bold text-brand-navy flex items-center gap-2">
                  {auditLog.affectedUserWissenId && (
                    <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-md bg-slate-100 text-brand-navy border border-slate-200">
                      {auditLog.affectedUserWissenId}
                    </span>
                  )}
                  <span>{auditLog.affectedUserName || (auditLog.affectedUserWissenId ? '' : `User #${auditLog.affectedUserId}`)}</span>
                </div>
                {auditLog.affectedUserEmail && (
                  <div className="text-xs text-brand-slate mt-0.5">{auditLog.affectedUserEmail}</div>
                )}
                {auditLog.affectedUserId != null && (
                  <div className="text-[11px] font-mono text-brand-slate mt-1">
                    Internal ID: #{auditLog.affectedUserId}
                  </div>
                )}
              </>
            ) : (
              <div className="text-xs text-brand-slate italic mt-1">
                No specific user affected
              </div>
            )}
          </div>
        </div>

        {/* Affected Resources Grid */}
        <div className="p-3.5 bg-slate-50/70 rounded-lg border border-brand-slate/20 space-y-2 text-xs">
          <div className="font-bold uppercase tracking-wider text-brand-navy text-[11px]">
            Target Resource Context
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div>
              <span className="text-brand-slate block">Entity Type:</span>
              <span className="font-bold text-brand-navy">{auditLog.entityType}</span>
            </div>
            <div>
              <span className="text-brand-slate block">Entity ID:</span>
              <span className="font-mono font-bold text-brand-navy">
                {auditLog.entityId ? `#${auditLog.entityId}` : '—'}
              </span>
            </div>
            <div>
              <span className="text-brand-slate block">Location:</span>
              <span className="font-medium text-brand-navy">
                {auditLog.locationName
                  ? `${auditLog.locationName} (#${auditLog.locationId})`
                  : auditLog.locationId
                  ? `Location #${auditLog.locationId}`
                  : '—'}
              </span>
            </div>
            <div>
              <span className="text-brand-slate block">Room:</span>
              <span className="font-medium text-brand-navy">
                {auditLog.roomName
                  ? `${auditLog.roomName} (#${auditLog.roomId})`
                  : auditLog.roomId
                  ? `Room #${auditLog.roomId}`
                  : '—'}
              </span>
            </div>
          </div>
          {auditLog.bookingId != null && (
            <div className="pt-2 border-t border-slate-200">
              <span className="text-brand-slate mr-2">Associated Booking:</span>
              <span className="font-mono font-bold text-brand-navy">
                Booking #{auditLog.bookingId}
              </span>
            </div>
          )}
        </div>

        {/* Result and Correlation ID (if available) */}
        {(auditLog.result || auditLog.correlationId) && (
          <div className="p-3 bg-blue-50/60 rounded-lg border border-blue-100 text-xs space-y-1.5 text-blue-900">
            {auditLog.result && (
              <div>
                <span className="font-bold mr-1">Result:</span>
                <span className="font-semibold">{auditLog.result}</span>
              </div>
            )}
            {auditLog.correlationId && (
              <div>
                <span className="font-bold mr-1">Correlation / Trace ID:</span>
                <span className="font-mono select-all">{auditLog.correlationId}</span>
              </div>
            )}
          </div>
        )}

        {/* Immutability & Audit Guarantee Notice */}
        <div className="p-2.5 bg-slate-100 rounded-lg text-xs text-brand-slate flex items-center justify-between">
          <span>Immutable audit record persisted at transaction commit.</span>
          <span className="font-mono">{formatTimestamp(auditLog.createdAt)}</span>
        </div>

        {/* Modal Controls */}
        <div className="flex justify-end pt-3 border-t border-slate-200">
          <Button variant="primary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  )
}

export default AuditLogDetailModal
