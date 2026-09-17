interface MissingInformationProps {
  items: string[]
}

export default function MissingInformation({ items }: MissingInformationProps) {
  if (items.length === 0) return null

  return (
    <section className="lb-card lb-card--muted">
      <h2 className="lb-card__title">Missing Information</h2>
      <ul className="lb-list lb-list--missing">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  )
}
