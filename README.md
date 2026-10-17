# LumiGuide

Paper handbook plugin using native Java dialogs and optional Floodgate/Cumulus Bedrock forms. Requires Paper 1.21.10+ and Java 21+. Install Floodgate on the same server for Bedrock forms (including when Geyser runs on a proxy).

## Install

1. Build with `./gradlew build` and put `build/libs/LumiGuide-1.0.0.jar` in the server's `plugins` directory.
2. Deploy the new `src/routes/api/docs/index.ts` endpoint in `../Luminara/website` using the website's normal deployment process.
3. Start the server. Configure `plugins/LumiGuide/config.yml` if your website uses a different URL.

`/guide` (aliases `/docs`, `/lumiguide`) opens categories. `/guide <words>` searches titles, descriptions, keywords, and paths; `/guide travel/waystones` opens a guide directly. The main handbook, category, and results dialogs include a search field: enter a query and press Search. Results retain the query for editing. Back buttons return to the previous category, search results, article, or related-guide list; the main handbook has no Back button. Bedrock combines the field with an Action dropdown; select Search or a guide and press Submit. Java buttons use vanilla Minecraft item sprites, matching Lumistones, with the appropriate atlas for the server version. Bedrock forms retain plain text labels, as in Lumistones. Guide and category icons come from each page’s `icon` metadata in the API feed; missing or invalid identifiers fall back to paper. Articles display their full content in a single scrollable view, with related-guide links and category overviews. Categories, search results, and related guides show all entries without pagination. Markdown headings, bold, italics, strikethrough, colored monospace code, numbered and nested lists, blockquotes, links, and image descriptions are rendered in-game. Tables use labeled rows to fit narrow screens. Java links have hover details and HTTP(S) links can open in the browser; internal links are available through Related guides. Bedrock retains colors, bold, and italics; its forms do not support clickable text, custom fonts, underline, or strikethrough. HTML is omitted. External links are displayed as text and commands are never executed from Markdown.

`/guide reload` reloads configuration and refreshes the feed. `lumiguide.use` defaults to everyone; `lumiguide.reload` defaults to operators. Docs refresh every ten minutes by default. Requests run asynchronously with size limits and HTTP timeouts. A failed request preserves the last valid feed, including across restarts via `handbook.json`. A first start requires a successful website response.

## Website contract

`GET /api/docs/` returns `{ "version": 1, "menu": [...], "pages": [...] }`. Each menu node has `text`, optional `href`, and optional nested `items`. Each page contains `path` (canonical `/docs/.../`), `title`, optional `description`, `keywords`, and `icon`, and `markdown` without YAML frontmatter. The endpoint bundles existing website Markdown at build time and shares the website's metadata and menu builder; docs updates become available after website deployment. No filesystem paths or remote fetch targets come from API request parameters.

Set an item sprite in each Markdown file’s YAML frontmatter, for example `icon: 'minecraft:item/ender_pearl'`. The website passes this field through `/api/docs/`; guide, category, search, and related-guide buttons use it after the feed refreshes. Use vanilla item texture names (for example `minecraft:item/compass_16` for a compass), rather than material names. Redeploy the website and run `/guide reload` to refresh immediately. No plugin rebuild is needed for future icon metadata changes.

## Audience-specific content

Unmarked Markdown appears on both the website and in Minecraft. Wrap standalone blocks with audience comments to show them in only one place:

```md
{/* audience:website */}
Use the sidebar to browse guides.
{/* /audience */}

{/* audience:minecraft */}
Use the handbook search field, then press Search.
{/* /audience */}
```

Markers must be on their own lines with blank lines around them. They work in `.md` and `.mdx` files and can wrap paragraphs, lists, tables, and headings. Both renderers remove the markers and excluded content before rendering; Minecraft-only content is absent from website HTML, and website-only content is absent from the API feed. Fenced code examples keep markers as literal text. Unmatched markers fail the build or feed generation so content is not silently exposed. Redeploy the website and refresh the guide feed after editing content.

## Verification

`./gradlew test build` checks feed validation, search, Markdown rendering, long articles, Unicode preservation, and related links. Website API coverage is in `../Luminara/website/tests/docs-api.test.ts`.

For a live smoke test, join with Java and Floodgate Bedrock clients, run `/guide`, browse a category, search for `waystones`, scroll through an article, follow a related guide, and close the UI. Stop the website and check that cached docs still open. Live client behavior requires a running Minecraft server and is not covered by the unit tests.
