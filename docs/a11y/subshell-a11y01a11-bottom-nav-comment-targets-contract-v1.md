# A11Y01A1.1 — large-text bottom navigation and comment touch targets

## Runtime evidence

A11Y01A1 was manually inspected on the Pixel 6 at a large system font setting.

Home header, post cards, post detail, Inbox and Composer retained usable layout.
The remaining visible regression is the main navigation label `Communities`,
which wraps across two lines (`Communiti` / `es`) at large text.

The same runtime review also confirms two explicit comment controls remain below
the 48 dp accessibility target:

- comment collapse / expand: 40 dp;
- comment overflow / options: 36 dp.

## A1.1 contract

1. Normal font scale keeps the visible navigation label `Communities`.
2. At `fontScale >= 1.3`, only the visual label changes to `Subs`.
3. The accessibility name remains the full `Communities` in both label modes.
4. The visible navigation label is single-line; the A1 large-text navigation
   height remains unchanged.
5. Comment collapse / expand becomes 48 dp.
6. Comment overflow / options becomes 48 dp.
7. Comment action meanings and existing content descriptions remain unchanged.
8. No Reddit, feed, auth, persistence, navigation destination, Room schema,
   Composer behavior, or expanded-width adaptive architecture changes.
9. Expanded-width / tablet / foldable work remains A11Y01A2.

## Runtime acceptance

At the same large system font setting used for the A1 screenshots:

- `Subs` must fit on one line in the bottom navigation;
- Home / Inbox / You labels remain unchanged;
- navigation selection and long-press You account switching still work;
- comment collapse / expand remains visually aligned and tappable;
- comment overflow remains visually aligned and opens the same menu.

TalkBack should announce the Communities destination as `Communities`, not `Subs`.
