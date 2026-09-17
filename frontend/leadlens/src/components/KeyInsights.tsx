interface KeyInsightsProps {
  items: string[]
}

export default function KeyInsights({ items }: KeyInsightsProps) {
  if (items.length === 0) return null

  return (
    <section className="lb-card">
      <h2 className="lb-card__title">What You Must Know</h2>
      <ul className="lb-list lb-list--bullet">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
