# Paper Market Frontend Redesign Design

## Goal

Refactor the main Vue frontend experience to match `docs/design/ui-redesign-preview.html` using the "Paper Market" visual direction: paper background, ink outlines, hard shadows, sticker-like cards, price tags, stamps, ticket details, and a campus flea-market tone.

## Scope

This redesign covers the main browsing experience first:

- Global SCSS design tokens and shared utility styles.
- `AppHeader` and `AppFooter`.
- `DefaultLayout` spacing/background behavior.
- `HomePage`.
- `ProductListPage`.
- `ProductCard`.
- Element Plus base overrides needed by the touched pages.

This redesign does not include a full restyle of every user-center, order-management, auth, or publish/detail workflow in this pass. Those pages should inherit only changes that come from shared global tokens, global base styles, and Element Plus overrides. Their deeper layout redesign is intentionally deferred to keep the first change safe and reviewable.

## Visual Direction

The interface should feel like a campus flea-market poster rather than a generic ecommerce template.

- Background: warm paper base with subtle grain texture.
- Surfaces: off-white paper cards with `2px` dark ink borders.
- Depth: hard offset shadows instead of blurred shadows.
- Primary action: cinnabar red, reused for publish/search/high-intent actions.
- Highlight: mustard yellow for selected nav, keyword marks, price tags, and active filters.
- Supporting colors: moss green and teal for seller dots, condition badges, and category accents.
- Typography: keep local font fallbacks reliable; use stronger title styling and mono-style labels without depending on remote Google Fonts.

## Architecture

The redesign should be implemented as a shared visual system plus page-level composition changes.

- `variables.scss` becomes the source of the Paper Market palette, borders, shadows, radius, spacing, typography, and responsive constants.
- `global.scss` applies the page paper background, selection color, scrollbar style, reusable `.container`, `.card`, and paper grain layer behavior.
- `element-overrides.scss` adapts Element Plus controls to the new border, radius, focus, and button styling.
- Shared components (`AppHeader`, `AppFooter`, `ProductCard`) receive the full visual treatment so pages stay consistent.
- Home and product listing pages reuse their existing API calls, route queries, pagination, search history, and product data shapes. The refactor should not alter backend contracts.

## Component Design

### Header

`AppHeader` should become a sticky paper bar with a black ticker above it, an ink-bordered red stamp logo, active yellow nav pill, ink-bordered search pill, publish button, order alerts, and user dropdown trigger styled like a small paper control.

Behavior remains unchanged:

- Header search routes to `/products`.
- Nav routes remain `/` and `/products`.
- Pending order polling remains unchanged.
- Logged-in and guest action branches remain unchanged.

### Footer

`AppFooter` should become a market-style footer with a dark ink band, brand block, link columns, and barcode-like decorative strip. Links can remain non-routing text if they currently do not navigate.

### Product Card

`ProductCard` should become an ink-bordered marketplace card:

- Square image area with paper fallback.
- Condition badge styled as a stamp.
- Image count as a small ink chip.
- Price rendered as a yellow price tag.
- Seller row uses avatar, name, and time with a separator.
- Sold/offline states retain disabled click behavior and visible overlay.

The component API stays unchanged: `product` and `showSeller`.

### Home Page

`HomePage` should follow the preview structure while keeping live data:

- Hero poster section with eyebrow, strong headline, highlighted phrase, search form, hot tags, publish poster, and ticket stats.
- Category sticker wall from loaded categories plus "全部商品".
- Channel signboards for latest, hottest, and budget browsing.
- Latest arrivals horizontal row with polaroid-like cards from `latestProducts`.
- Recommended product grid using `ProductCard`.

Routing stays unchanged:

- Search submits to `/products?keyword=...`.
- Hot tags route to `/products?keyword=...`.
- Category buttons route to `/products?categoryId=...`.
- Channel cards route to the existing sort query values.

### Product List Page

`ProductListPage` should become a market browsing desk:

- Hero strip uses paper card styling and publish CTA.
- Filter panel uses ink borders and paper surfaces.
- Search history dropdown remains functional.
- Category, condition, price, active filters, sorting, skeleton, empty state, and pagination remain functionally unchanged.
- Product grid continues to use `ProductCard`.

## Data Flow

No API shape changes are required.

- Categories continue to load through `getCategories`.
- Home products continue to load through `getProductPage`.
- Product list filtering continues to map route query and local filter state into `getProductPage` params.
- Header pending counts continue to load through `getPendingCount`.

## Responsiveness

The redesign must preserve usable layouts across desktop and mobile:

- Header wraps cleanly below tablet width.
- Home hero collapses from two columns to one.
- Category grid moves from 8 columns to 4 and then 2.
- Product list grid moves from 5 columns to 4, 3, and 2.
- Filter controls should scroll or wrap without text overlap.
- Hard-shadow elements must not create horizontal overflow on mobile.

## Accessibility And UX

- Keep semantic buttons and links as they are now.
- Preserve form labels and input behavior.
- Use visible focus states through Element Plus overrides and native buttons.
- Keep disabled/sold products non-clickable.
- Avoid relying on hover-only cues for critical state.
- Respect `prefers-reduced-motion` for page entrance animations.

## Testing And Verification

Verification should include:

- `npm run build` from `secondhand-platform-web`.
- Manual browser pass on `/`, `/products`, and a mobile-width viewport.
- Confirm search, category filtering, sort changes, price filter, search history, pagination rendering, and product-card navigation still work.
- Confirm no obvious text overlap or horizontal overflow in the main pages.

## Deferred Work

The following are intentionally out of scope for this pass:

- Deep redesign of login/register pages.
- Deep redesign of product detail and product publish forms.
- Deep redesign of user center pages and order-management views.
- Replacing emoji/category placeholders with generated or photographed assets.
- Adding new backend data or changing API contracts.
