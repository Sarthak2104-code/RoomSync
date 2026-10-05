import React from 'react'

export interface TableProps extends React.TableHTMLAttributes<HTMLTableElement> {
  loading?: boolean
  empty?: boolean
  emptyMessage?: string
  loadingMessage?: string
  columnCount?: number
}

export const Table: React.FC<TableProps> = ({
  children,
  loading = false,
  empty = false,
  emptyMessage = 'No data available',
  loadingMessage = 'Loading data...',
  columnCount = 1,
  className = '',
  ...props
}) => {
  return (
    <div className="w-full overflow-x-auto rounded-lg border border-brand-slate/20 bg-brand-white shadow-xs">
      <table className={`w-full text-left text-sm text-brand-navy border-collapse ${className}`} {...props}>
        {children}
        {loading && (
          <tbody>
            <tr>
              <td colSpan={columnCount} className="px-6 py-12 text-center text-brand-slate">
                <div className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-brand-navy border-t-transparent rounded-full animate-spin" />
                  <span>{loadingMessage}</span>
                </div>
              </td>
            </tr>
          </tbody>
        )}
        {!loading && empty && (
          <tbody>
            <tr>
              <td colSpan={columnCount} className="px-6 py-12 text-center text-brand-slate text-sm">
                {emptyMessage}
              </td>
            </tr>
          </tbody>
        )}
      </table>
    </div>
  )
}

export const TableHeader: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = ({
  className = '',
  children,
  ...props
}) => (
  <thead className={`bg-brand-light-gray border-b border-brand-slate/20 text-xs font-semibold text-brand-navy uppercase tracking-wider ${className}`} {...props}>
    {children}
  </thead>
)

export const TableBody: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = ({
  className = '',
  children,
  ...props
}) => (
  <tbody className={`divide-y divide-brand-slate/10 bg-brand-white ${className}`} {...props}>
    {children}
  </tbody>
)

export const TableRow: React.FC<React.HTMLAttributes<HTMLTableRowElement>> = ({
  className = '',
  children,
  ...props
}) => (
  <tr className={`transition-colors hover:bg-slate-50/80 ${className}`} {...props}>
    {children}
  </tr>
)

export const TableHead: React.FC<React.ThHTMLAttributes<HTMLTableCellElement>> = ({
  className = '',
  children,
  ...props
}) => (
  <th scope="col" className={`px-6 py-3 font-semibold ${className}`} {...props}>
    {children}
  </th>
)

export const TableCell: React.FC<React.TdHTMLAttributes<HTMLTableCellElement>> = ({
  className = '',
  children,
  ...props
}) => (
  <td className={`px-6 py-4 whitespace-nowrap text-brand-navy ${className}`} {...props}>
    {children}
  </td>
)

export default Table
