interface PendingActionsProps {
  items: string[]
}

export default function PendingActions({ items }: PendingActionsProps) {
  if (items.length === 0) return null

  return (
    <section className="lb-card">
      <h2 className="lb-card__title">Pending Actions</h2>
      <ul className="lb-list lb-list--pending">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
