import React, { useState } from 'react'

export interface RoomImageProps {
  src?: string | null
  alt?: string
  roomName?: string
  className?: string
}

export const RoomImage: React.FC<RoomImageProps> = ({
  src,
  alt = 'Room Image',
  roomName,
  className = '',
}) => {
  const [hasError, setHasError] = useState(false)

  if (src && !hasError) {
    return (
      <img
        src={src}
        alt={alt}
        onError={() => setHasError(true)}
        className={`object-cover rounded-lg ${className}`}
      />
    )
  }

  return (
    <div
      className={`relative flex flex-col items-center justify-center bg-gradient-to-br from-slate-100 to-slate-200 text-slate-400 rounded-lg border border-slate-200/80 overflow-hidden select-none p-3 text-center ${className}`}
      role="img"
      aria-label={alt}
    >
      <div className="w-10 h-10 rounded-full bg-white/80 shadow-2xs border border-slate-200 flex items-center justify-center mb-1.5 text-slate-500 shrink-0">
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
          />
        </svg>
      </div>
      <span className="text-xs font-bold text-slate-700 truncate max-w-full px-2">
        {roomName || 'Room Image'}
      </span>
      <span className="text-[10px] text-slate-500 font-medium mt-0.5">
        Image will be added
      </span>
    </div>
  )
}

export default RoomImage
