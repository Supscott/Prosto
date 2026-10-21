import { Camera, Play } from 'lucide-react';

type MediaPlaceholderProps = {
  type: 'photo' | 'video';
  title: string;
  description: string;
  className?: string;
  wide?: boolean;
};

export function MediaPlaceholder({ type, title, description, className = '', wide = false }: MediaPlaceholderProps) {
  const Icon = type === 'photo' ? Camera : Play;

  return (
    <div className={`media-placeholder ${wide ? 'media-placeholder--wide' : ''} ${className}`}>
      <span className="media-label">
        <Icon size={wide ? 13 : 10} strokeWidth={2.2} />
        {type}
      </span>
      <span className="media-title">{title}</span>
      <span className="media-desc">{description}</span>
    </div>
  );
}
