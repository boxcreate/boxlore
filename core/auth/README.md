# `:core:auth`

## Purpose

Provides authentication abstractions and implementations (Firebase).

## Public API

- `AuthRepository`
- `AuthException`

## Internal structure

- Authentication implementation.

## Dependencies

- `:core:model`

## Threading / lifecycle

- IO for network/auth operations.

## Persistence & identity

- PendingEmailStore (DataStore).

## Testing notes

- Unit tests in `src/test`.

## CI relevance

- General CI pipeline.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
