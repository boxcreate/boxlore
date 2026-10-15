# `:feature:history`

## Purpose

Provides UI and logic for viewing the user's listening history.

## Public API

- History episode artwork retains its show-art alternative through actual load failures using the shared loader, without changing history ordering or navigation.

- `HistoryScreen`

## Internal structure

- UI components and ViewModels for history.

## Dependencies

- `:core:model`
- `:core:domain`

## Threading / lifecycle

- Main for UI, ViewModels.

## Persistence & identity

- None.

## Testing notes

- The tablet history list is centered within the shared 720dp Reading width, bounding statistics, graphs and timeline rows together without changing their arrangement or queries.

- Unit tests in `src/test`.

## CI relevance

- General CI pipeline.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
