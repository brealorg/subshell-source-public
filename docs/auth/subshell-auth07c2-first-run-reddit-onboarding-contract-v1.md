# AUTH07C2 first-run Reddit onboarding contract v1

## Goal

Subshell presents a product-level Reddit setup flow when an installation
does not yet have an authenticated Reddit account.

The normal user does not need to discover the historical RedReader
compatibility dialog in Settings before connecting Reddit.

## Entry policy

The onboarding decision is derived from current product state.

An authenticated product account skips first-run Reddit onboarding.

Without an authenticated account:

- unknown configuration state waits;
- missing Reddit client configuration opens setup;
- configured Reddit client state goes directly to Ready to connect.

AUTH07C2 does not add a durable `onboarding_completed` preference.

## Setup flow

The first-run flow is:

`Set up Reddit -> Get your Client ID -> Paste Client ID -> Ready to connect -> Reddit -> Connected -> Start using Subshell`

The setup guide explains the RedReader compatibility process and links to
Google Play and F-Droid.

External help destinations are owned outside the Compose UI. The
configuration boundary constructs the HTTPS destinations structurally
without embedding raw HTTP URL literals in application source.

The normal Client ID screen asks only for the Client ID.

Advanced exposes the fixed compatibility identity:

- redirect URI `redreader://rr_oauth_redir`
- user agent `org.quantumbadger.redreader/1.25.1`

Those values remain owned by `RedditOAuth` and are surfaced to UI through
the existing Reddit compatibility configuration controller.

## Configuration storage

Client ID validation and persistence continue through
`RedditCompatibilityConfigurationController`.

The Client ID remains encrypted in no-backup storage.

AUTH07C2 does not place the Client ID in preferences, Room, logs,
BuildConfig, source, or backups.

Successful save reuses AUTH07C1 automatic host reload.

## Account authorization

Ready to connect reuses the canonical AUTH07B1 daily-driver account
connection flow.

AUTH07C2 does not create a second OAuth implementation.

Additional Reddit accounts do not enter first-run setup because the
installation already has authenticated product accounts and the existing
Client ID is reused.

## Onboarding visual identity

The first-run Reddit setup is explicitly branded as subshell.

The setup top bar uses the subshell in-app identity and the stable
product title `subshell`.

The first landing surface uses one primary content headline:
`Connect subshell to your Reddit account`.

The persistent app bar owns product branding through the subshell logo and
lowercase `subshell` title. The landing content does not duplicate that logo
or add a competing welcome headline.

The product name is always rendered as `subshell` in user-facing
onboarding copy.

Google Play and F-Droid actions use locally packaged store badges in
one compact horizontal row.

The Reddit authorization example visually mirrors the structure of the
actual Reddit authorization email without displaying or persisting a
real App ID.

## Presentation boundary

Reddit onboarding is rendered inside the canonical Subshell theme boundary
and before normal main-content and composer presentation.

Existing interaction-side and backup onboarding remain intact and can run
after Reddit setup completes.

## Completion

When the account directory publishes the newly authenticated account,
AUTH07C2 shows a transient connected completion surface.

`Start using Subshell` leaves onboarding.

On a later cold start an existing authenticated account skips onboarding
without consulting a completion flag.

## Boundaries

AUTH07C2 does not change OAuth scopes, Reddit endpoints, Room schema,
session storage, account IDs, profile behavior, subscription landing
policy, or AUTH07C1 runtime ownership.
