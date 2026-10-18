# LumiGuide

Paper handbook plugin using native Java dialogs and optional Floodgate/Cumulus Bedrock forms. Requires Paper 1.21.10+ and Java 21+. Install Floodgate on the same server for Bedrock forms (including when Geyser runs on a proxy).

## Install

1. Build with `./gradlew build` and put `build/libs/LumiGuide-1.0.0.jar` in the server's `plugins` directory.
2. Deploy the new `src/routes/api/docs/index.ts` endpoint in `../Luminara/website` using the website's normal deployment process.
3. Start the server. Configure `plugins/LumiGuide/config.yml` if your website uses a different URL.

`/guide` (aliases `/docs`, `/lumiguide`) opens categories. `/guide <words>` searches titles, descriptions, keywords, and paths; `/guide travel/waystones` opens a guide directly. The main handbook, category, and results dialogs include a search field: enter a query and press Search. Results retain the query for editing. Back buttons return to the previous category, search results, or article; the main handbook has no Back button. Bedrock combines the field with an Action dropdown; select Search or a guide and press Submit. Java buttons use vanilla Minecraft item sprites, matching Lumistones, with the appropriate atlas for the server version. Bedrock forms retain plain text labels, as in Lumistones. Guide and category icons come from each page’s `icon` metadata in the API feed; missing or invalid identifiers fall back to paper. Articles display their full content in scrollable dialogs on Java and forms on Bedrock unless explicit page separators are present (see below). Linked guides appear as individual buttons with their icons and titles; Back returns to the source article. Categories and search results show all entries without pagination. Markdown headings, bold, italics, strikethrough, colored code in the regular Minecraft font, numbered and nested lists, blockquotes, links, and image descriptions are rendered in-game. Tables use labeled rows to fit narrow screens. Java links have hover details and HTTP(S) links can open in the browser; internal links have corresponding guide buttons below the article. Bedrock retains colors, bold, and italics; its forms do not support clickable text, custom fonts, underline, or strikethrough. Inline styled spans support explicit color and formatting previews; other HTML is omitted. External links are displayed as text and commands are never executed from Markdown.

`/guide reload` reloads configuration and refreshes the feed. `lumiguide.use` defaults to everyone; `lumiguide.reload` defaults to operators. Docs refresh every ten minutes by default. Requests run asynchronously with size limits and HTTP timeouts. A failed request preserves the last valid feed, including across restarts via `handbook.json`. A first start requires a successful website response.

## Website contract

Players receive a Player handbook book once when joining if they have `lumiguide.use`. Receipt is stored in the player's persistent data across reconnects and server restarts, so storing or losing the book does not grant another. Right-click it to open the handbook on Java or Bedrock. Full inventories are left untouched; the next join checks again until the book is successfully given. Set `join-item.enabled: false` and run `/guide reload` to disable distribution. Existing books still work. Players who received a book before receipt tracking was added receive one more on their next eligible join.

`GET /api/docs/` returns `{ "version": 1, "menu": [...], "pages": [...] }`. Each menu node has `text`, optional `href`, and optional nested `items`. Each page contains `path` (canonical `/docs/.../`), `title`, optional `description`, `keywords`, `icon`, and `extra_buttons`, and `markdown` without YAML frontmatter. The endpoint bundles existing website Markdown at build time and shares the website's metadata and menu builder; docs updates become available after website deployment. No filesystem paths or remote fetch targets come from API request parameters.

Set an item sprite in each Markdown file’s YAML frontmatter, for example `icon: 'minecraft:item/ender_pearl'`. The website passes this field through `/api/docs/`; guide, category, search, and related-guide buttons use it after the feed refreshes. Use vanilla item texture names (for example `minecraft:item/compass_16` for a compass), rather than material names. Redeploy the website and run `/guide reload` to refresh immediately. No plugin rebuild is needed for future icon metadata changes.

Add optional `extra_buttons` in a guide’s frontmatter to create direct Minecraft navigation buttons, including from website-only content:

```yaml
extra_buttons:
  - label: 'Get home or meet a friend'
    href: '/docs/travel/homes-and-requests/'
    # icon: 'minecraft:item/red_bed' # optional; defaults to the target guide icon
```

Targets must be canonical `/docs/.../` paths. Extra buttons appear before ordinary article links, replacing duplicate links to the same target. They also appear on the corresponding category dialog. The main menu keeps a Player handbook button; its text and extra buttons appear together when opened. Missing targets are skipped. Older feeds without this field continue to work.

## Unread documentation updates

LumiGuide tracks the last successful documentation change and a content revision for every guide in `plugins/LumiGuide/doc-updates.json`. New guides, changed Markdown, titles, icons, navigation metadata, or publication dates create an update. An unchanged feed or reordered pages do not. Content fingerprints catch multiple edits on the same day even if the author forgets to change `last_updated`.

Players with `lumiguide.use` receive a chat notice shortly after joining if they have not opened the handbook since the latest changes. The notice lists each unread guide as **New** or **Updated**, with its date and a clickable command to open it. Online players are also notified when a successful feed refresh discovers changes. The same revision is announced once per session; reminders return on subsequent joins until the handbook is opened.

Opening any handbook menu or article through `/guide`, its aliases, the handbook item, or a chat link acknowledges the current revision and records the opening time in the player's persistent data. This works across reconnects and restarts. A failed Bedrock form or an unavailable feed does not acknowledge anything. Previously unseen players see the current docs as new until their first successful opening.

Existing website fields `date_created` and `last_updated` supply publication dates on first discovery. Missing, invalid, or future dates fall back to the detection date (UTC). Subsequent content changes use their detection date, so stale author dates do not hide updates. Failed refreshes retain the last valid history and feed; removed guides are not announced as updates. No website API change is needed.

## Optional article pagination

By default, articles stay in one scrollable dialog, regardless of length. To split an article, put this standalone marker on its own line, with blank lines around it:

```md
## First day

Updates for the first day.

{/* pagebreak */}

## Second day

Updates for the second day.
```

LumiGuide displays one section at a time on both Java and Bedrock, adds Previous page / Next page buttons where available, and shows the page number in the title. Back still returns to the original menu or search results; following a linked guide and going Back returns to the same article page. Related guide links come from the current section; frontmatter extra buttons remain available on every page.

The MDX comment marker is invisible on the website and passes through the handbook feed unchanged. Plain Markdown sources can also use `<!-- pagebreak -->`. Markers inside code examples, lists, or quotes do not split pages, and ordinary horizontal rules (`---`) remain horizontal rules. Leading, trailing, or repeated separators do not create blank pages. No separators means no pagination controls or page counter. Deploy the updated website content and run `/guide reload` after installing the updated plugin.

## Audience-specific content

Set a file-wide audience in YAML frontmatter to restrict an entire guide:

```yaml
audience: website # or minecraft; omitted / both includes both audiences
```

Website-only guides are excluded from Minecraft pages, search, category menus, and extra button targets. Minecraft-only guides are excluded from website navigation and return 404 when opened on the website. Joining instructions are website-only because in-game readers are already connected.

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

## Styling text in Markdown

Use inline spans directly in a paragraph, list, or table cell to style text in the website and Minecraft handbook:

```html
<span style="color: #ff5555">Red text</span>
<span style="color: #ffaa00; font-weight: bold">Golden title</span>
<span style="font-style: italic; text-decoration: underline">Emphasis</span>
```

Supported properties are `color` (hex or a Minecraft named color), `font-weight` (`bold` / `normal`, or `700` / `400`), `font-style` (`italic` / `normal`), and `text-decoration` (`underline`, `line-through`, or `none`). Prefer hex colors for consistent website and Minecraft colors. Combine decorations with spaces. Spans can nest, and closing a span restores the surrounding style. Keep each opening and closing pair within the same paragraph or table cell; standalone HTML blocks are omitted. Inline and fenced code remain literal examples.

For Minecraft magic text, use `<span style="--minecraft-obfuscated: true">Magic</span>`; this custom property has no visual effect on the website. Bedrock retains supported colors, bold, italic, and obfuscation, but cannot show underline or strikethrough. Other CSS properties are ignored in Minecraft; spans cannot add click actions or change fonts. No table headings or color-guide-specific logic controls styling.

## Inline sprites in Markdown

Use ordinary Markdown image syntax with a Minecraft texture identifier:

```md
Craft ![Paper](minecraft:item/paper) with an ![Ender pearl](minecraft:item/ender_pearl).
Use ![Stone bricks](minecraft:block/stone_bricks) and ![Obsidian](minecraft:block/obsidian).
```

Java articles show the sprite beside its label, including in lists and crafting table cells. Item textures use the appropriate server-version atlas; block textures always use the blocks atlas. Use texture names rather than Bukkit material names. Labels remain visible on Bedrock and in search/plain text, and code examples stay literal. Missing alt text falls back to the texture name. Only `minecraft:item/...` and `minecraft:block/...` identifiers with lowercase letters, digits, and underscores are accepted. Unknown texture names can display Minecraft's missing-texture sprite; ordinary image URLs retain their existing image-description behavior.

The website resolves these identifiers to pixelated texture images from MCAsset Cloud's Minecraft 1.21.10 assets and keeps the label visible if an image cannot load. Add sprites directly to the shared Markdown and deploy the website; refresh the in-game feed with `/guide reload`.

Add the image title `"sprite-only"` to omit the visible label while retaining a Java hover label and Bedrock text fallback:

```md
![Paper](minecraft:item/paper "sprite-only")
```

For crafting grids, write three lines of three icon-only images separated by ` | `, using Markdown hard line breaks (two spaces at each row end). Barrier sprites can mark empty slots; explain the placeholder under the grid. The shared recipe guides use this layout inside `audience:minecraft` blocks. Their `audience:website` blocks show larger SVG crafting diagrams instead.
