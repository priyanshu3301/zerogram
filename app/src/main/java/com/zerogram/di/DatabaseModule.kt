package com.zerogram.di

import android.content.Context
import androidx.room.Room
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.dao.FileDao
import com.zerogram.data.local.dao.FolderDao
import com.zerogram.data.local.dao.TransferJobDao
import com.zerogram.data.local.dao.VaultConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE vault_config ADD COLUMN vault_name TEXT NOT NULL DEFAULT 'My Vault'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE files ADD COLUMN deleted_at INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE folders ADD COLUMN deleted_at INTEGER DEFAULT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE transfer_jobs ADD COLUMN source_uri TEXT DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Recreate transfer_jobs to rename source_uri to source_path
        database.execSQL("CREATE TABLE IF NOT EXISTS `transfer_jobs_new` (`id` TEXT NOT NULL, `file_id` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL DEFAULT 'queued', `progress_bytes` INTEGER NOT NULL DEFAULT 0, `total_bytes` INTEGER NOT NULL, `retry_count` INTEGER NOT NULL DEFAULT 0, `last_error` TEXT, `source_path` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`file_id`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        database.execSQL("INSERT INTO `transfer_jobs_new` (`id`, `file_id`, `type`, `status`, `progress_bytes`, `total_bytes`, `retry_count`, `last_error`, `source_path`, `created_at`, `updated_at`) SELECT `id`, `file_id`, `type`, `status`, `progress_bytes`, `total_bytes`, `retry_count`, `last_error`, `source_uri`, `created_at`, `updated_at` FROM `transfer_jobs`")
        database.execSQL("DROP TABLE `transfer_jobs`")
        database.execSQL("ALTER TABLE `transfer_jobs_new` RENAME TO `transfer_jobs`")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_jobs_file_id` ON `transfer_jobs` (`file_id`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_jobs_status` ON `transfer_jobs` (`status`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZerogramDatabase {
        return Room.databaseBuilder(
            context,
            ZerogramDatabase::class.java,
            ZerogramDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        // NOTE: destructive-migration fallback was removed on purpose.
        // This is a database that gets swapped in wholesale from an encrypted
        // Telegram backup. If it's ever opened before it matches the expected
        // schema/version (e.g. a race, a corrupted decrypt, or a backup made
        // by an older app build with no matching migration), Room would
        // otherwise SILENTLY DROP AND RECREATE every table as empty instead
        // of failing loudly. That silent wipe is what was producing the
        // "loads a brand new empty database" reports. Now a mismatch throws
        // an IllegalStateException that the vault-unlock code below catches
        // and reports as a real error instead of pretending it succeeded.
        .build()
    }

    @Provides
    fun provideFolderDao(db: ZerogramDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideFileDao(db: ZerogramDatabase): FileDao = db.fileDao()

    @Provides
    fun provideTransferJobDao(db: ZerogramDatabase): TransferJobDao = db.transferJobDao()

    @Provides
    fun provideVaultConfigDao(db: ZerogramDatabase): VaultConfigDao = db.vaultConfigDao()
}
