interface TalkingPointsProps {
  items: string[]
}

export default function TalkingPoints({ items }: TalkingPointsProps) {
  if (items.length === 0) return null

  return (
    <section className="lb-card">
      <h2 className="lb-card__title">Recommended Talking Points</h2>
      <ol className="lb-list lb-list--numbered">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ol>
    </section>
  )
}
