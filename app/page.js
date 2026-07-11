export default function Home() {
  return (
    <main style={{ fontFamily: "system-ui, sans-serif", padding: 32, maxWidth: 760 }}>
      <h1>ContentLens API</h1>
      <p>Smithware Studios content safety proxy.</p>
      <ul>
        <li><code>/api/health</code></li>
        <li><code>/api/v1/safety/tmdb/movie/:tmdbId</code></li>
        <li><code>/api/v1/safety/tmdb/tv/:tmdbId</code></li>
      </ul>
    </main>
  );
}
