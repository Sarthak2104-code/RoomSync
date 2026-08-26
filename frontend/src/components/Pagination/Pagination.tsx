import React from 'react'
import Button from '../Button/Button'

export interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
  disabled?: boolean
  className?: string
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
  disabled = false,
  className = '',
}) => {
  if (totalPages <= 1) return null

  const isFirstPage = currentPage <= 1
  const isLastPage = currentPage >= totalPages

  return (
    <nav
      className={`flex items-center justify-between px-4 py-3 bg-brand-white border-t border-brand-slate/20 sm:px-6 ${className}`}
      aria-label="Pagination Navigation"
    >
      <div className="flex flex-1 justify-between sm:hidden">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={disabled || isFirstPage}
        >
          Previous
        </Button>
        <span className="text-xs text-brand-slate self-center">
          Page {currentPage} of {totalPages}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={disabled || isLastPage}
        >
          Next
        </Button>
      </div>

      <div className="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
        <div>
          <p className="text-xs text-brand-slate">
            Showing page <span className="font-semibold text-brand-navy">{currentPage}</span> of{' '}
            <span className="font-semibold text-brand-navy">{totalPages}</span>
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(currentPage - 1)}
            disabled={disabled || isFirstPage}
            aria-label="Go to previous page"
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={disabled || isLastPage}
            aria-label="Go to next page"
          >
            Next
          </Button>
        </div>
      </div>
    </nav>
  )
}

export default Pagination
