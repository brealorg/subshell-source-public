# WIKI01A2R3 — modal window Back ownership

## Runtime defect

R2 disabled `ModalBottomSheet` Back dismissal while Wiki was active and relied
on a regular Compose `BackHandler`.

On Android the modal sheet owns a separate focusable popup/application-panel
window. Disabling `shouldDismissOnBackPress` also disables the window-level Back
callback, so the intended Wiki Back ladder was not a reliable owner of system
Back.

## Correct ownership

The modal window now keeps `shouldDismissOnBackPress = true`.

While Wiki is active, the SheetState vetoes transitions to `SheetValue.Hidden`.
The resulting `onDismissRequest` is therefore routed through the Wiki hierarchy
without allowing the sheet to disappear:

1. Wiki page → Wiki index
2. Wiki index → Community info
3. Community info → dismiss ModalBottomSheet

The visible header arrow continues to call the same `navigateWikiBack` lambda.

## Boundary

No OAuth, Wiki transport, Room, backup, write, DEV1, or visual design change.

R3 is a runtime Back fix only.

No checkpoint is created before manual runtime verification.
