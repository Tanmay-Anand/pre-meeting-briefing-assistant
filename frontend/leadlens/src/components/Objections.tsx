import type { Objection } from '../types/leadBrief'

interface ObjectionsProps {
  items: Objection[]
}

export default function Objections({ items }: ObjectionsProps) {
  if (items.length === 0) return null

  return (
    <section className="lb-card">
      <h2 className="lb-card__title">Objections</h2>
      <ul className="lb-list lb-objections">
        {items.map((objection) => (
          <li key={objection.title} className="lb-objections__item">
            <span className={`lb-severity-dot lb-severity-dot--${objection.severity}`} aria-hidden="true" />
            <div>
              <p className="lb-objections__title">{objection.title}</p>
              <p className="lb-objections__meta">
                {objection.source} · {objection.date}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
