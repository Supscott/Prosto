# Custom Search Providers & Shortcuts Implementation Plan

## Summary
Add support for dynamic custom search providers, shortcuts (e.g. `g query`, `d query`, `b query`), and provider management (add, edit, reorder via drag-and-drop, delete) in both Search Overlay and Settings.

---

## 1. Requirements & User Alignments (from /grill-me)

1. **Shortcut Execution**:
   - Built-in shortcuts: `g` (Google), `d` (DuckDuckGo), `b` (Bing). Custom shortcuts can be assigned to custom engines (e.g. `y` for YouTube).
   - **Real-time trigger**: Typing `<shortcut> ` (shortcut + space) in the search input automatically selects that search engine in the selector button and clears the `<shortcut> ` prefix from the input field.
   - **Submit trigger**: Submitting a search with `<shortcut> <query>` directly queries the provider even if not switched in advance (e.g. pasted text).
2. **Built-in & Custom Engines**:
   - Built-in and custom search engines can both be edited, reordered, and deleted, as long as at least 1 engine remains in the list.
   - If the currently active engine is deleted, the first available engine becomes active.
3. **Dropdown Menu Management**:
   - Pinned flex row at bottom: `[Edit Pencil]` and `[Plus Icon]`.
   - Clicking `[Edit Pencil]` enters edit/manage mode:
     - The bottom row switches to a full-width "Done" button to exit edit mode.
     - Each item displays:
       - Left: Drag handle (`Icons.Default.DragHandle`) + Edit pencil (`Icons.Default.Edit`).
       - Middle: Favicon/Badge + Display Name + (shortcut badge).
       - Right: Trash icon (`Icons.Default.DeleteOutline`) with disabled state if only 1 engine left.
     - Dragging the handle vertically reorders items dynamically.
     - Tapping Edit pencil opens the Edit Provider dialog.
     - Tapping Trash deletes the engine.
   - Clicking `[Plus Icon]` opens the Add Provider dialog.
4. **Add/Edit Provider Modal Dialog**:
   - Styled using Serene Canvas tokens (24dp rounded card, tactile borders, backdrop scrim).
   - 3 input fields:
     1. Title / Name (e.g. "YouTube")
     2. Shortcut (optional, e.g. "y")
     3. URL template (prefilled with `https://`)
   - Bottom hint text:
     `Write the search url and replace the query with %s.\n\nFor example: https://youtube.com/search?q=%s.`
   - Inline validation:
     - Error if URL does not contain `%s`.
     - Error if shortcut is already taken by another engine.
     - Add/Save button disabled until valid.
   - Immediate favicon extraction using `FaviconManager.extractFallbackFaviconUrl(url)`. Loads immediately without manual app reload.
5. **Settings Screen Integration**:
   - Under the Search Engine picker card in Settings, an "Add search engine" item/button opens the same Add Provider modal dialog.

---

## 2. Technical Architecture

### 2.1 Domain & Database Layer
- **Room Entity**: `SearchEngineEntity`
  - `id`: String (e.g. `"google"`, `"duckduckgo"`, `"bing"`, or UUID)
  - `displayName`: String
  - `searchUrlTemplate`: String (containing `%s`)
  - `iconUrl`: String (asset URL or Google favicon API URL)
  - `shortcut`: String?
  - `orderPosition`: Int
  - `isBuiltIn`: Boolean
- **Room DAO**: `SearchEngineDao`
  - `getSearchEngines(): Flow<List<SearchEngineEntity>>`
  - `insert(engine: SearchEngineEntity)`
  - `update(engine: SearchEngineEntity)`
  - `delete(id: String)`
  - `updatePositions(items: List<SearchEngineEntity>)`
- **Domain Model**: `SearchEngine`
  - Convert `SearchEngine` from enum to `data class` with companion constants `DUCKDUCKGO`, `GOOGLE`, `BING`, and `DEFAULT_ENGINES`.
  - Retain `buildSearchUrl`, `matchesUrl`, `extractQuery`, `findMatchingEngineAndQuery`.
- **Database Migration**: Bump Room version (version 4 -> 5), seed `DEFAULT_ENGINES` on first creation or if table is empty.

### 2.2 ViewModel & State
- Add to `BrowserUiState`:
  - `searchEngines: List<SearchEngine>`
  - `showAddEditSearchEngineDialog: Boolean`
  - `editingSearchEngine: SearchEngine?` (null for Add, non-null for Edit)
- Events in `BrowserUiEvent`:
  - `SaveSearchEngine(id: String?, title: String, shortcut: String?, urlTemplate: String)`
  - `DeleteSearchEngine(id: String)`
  - `ReorderSearchEngines(reordered: List<SearchEngine>)`
  - `ShowAddSearchEngineDialog(engineToEdit: SearchEngine? = null)`
  - `DismissAddSearchEngineDialog`
- Shortcut detection logic in `HeroSearchInputField`:
  - On typing `<shortcut> `: detect if text starts with `<shortcut> `, switch active engine to target, trim `<shortcut> ` from input.
  - On `SubmitSearch`: fallback check if input starts with `<shortcut> <query>`.

### 2.3 UI Components
1. `AddEditSearchEngineDialog`:
   - Title: "Add Search Provider" / "Edit Search Provider"
   - Inputs: Name, Shortcut, URL
   - Hint text and inline error messages
   - Save / Cancel buttons
2. `SearchEngineDropdownCard`:
   - Normal mode: list with checkmark for selected + bottom row (`[Edit]`, `[+]`).
   - Edit mode: reorder handle, edit button, title + shortcut, trash button + full-width "Done" button at bottom.
   - Drag gesture handling for vertical reorder.
3. `SettingsScreen`:
   - "Add search engine" button/row in Search Engine settings card opening `ShowAddSearchEngineDialog()`.

### 2.4 Testing Strategy
- Unit tests:
  - `SearchEngineDaoTest`: insert, update, delete, reorder.
  - `SearchEngineTest`: URL building, query matching, shortcut matching.
  - `BrowserViewModelTest`: shortcut trigger on typing `<shortcut> `, shortcut query submission, adding provider with favicon, deleting engine fallback, reordering.
- Visual Preview tests:
  - Compose preview for `AddEditSearchEngineDialog` (Light & Dark).
  - Compose preview for `SearchEngineDropdownCard` in both Normal and Edit modes.
