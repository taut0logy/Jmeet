import Image from 'next/image';

export function Logo({ size = 28, className }: { size?: number; className?: string }) {
  return <Image src="/logo.png" alt="" width={size} height={size} className={className} priority />;
}
