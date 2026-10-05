# Database Schema

Zerogram uses Android's **Room** persistence library to manage the local metadata of your vault. The database acts as a local cache for the file hierarchy, mapping local file paths to Telegram message IDs.

## Entities

- **`FileEntity`**: Represents an encrypted file. Stores the original filename, mime type, size, parent folder ID, and critically, the `telegramMessageId` and `telegramFileId` needed to retrieve the payload from Telegram.
- **`FolderEntity`**: Represents a folder in the hierarchy. Supports recursive nesting via a `parentFolderId`.
- **`TransferJobEntity`**: A persistent queue of active, paused, or failed uploads and downloads. `TransferService` reads from this table to resume operations after an app restart.
- **`VaultConfigEntity`**: Stores non-sensitive vault configuration and statistics.

## Schema Migrations

The project enforces strict schema migrations. `exportSchema = true` is enabled to ensure all schema changes are validated at compile time and tracked in version control.

### Current Version: `5`

- `V1 -> V2`: Initial schema definition.
- `V2 -> V3`: Added `TransferJobEntity` table.
- `V3 -> V4`: Added `uploadStatus` to `FileEntity`.
- `V4 -> V5`: Added `mimeType` and `thumbnailId` fields to support media categorization.

*Note: There is an unused `baseIv` column in `FileEntity` that is scheduled to be dropped in migration 6.*

## Recursive Folder Queries

Because folders can be infinitely nested, `FolderDao.kt` utilizes SQLite Common Table Expressions (CTEs) for recursive queries. This allows us to efficiently calculate the total size of a folder (including all subfolders) or recursively delete a directory tree in a single SQL transaction.
