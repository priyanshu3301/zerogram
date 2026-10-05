package com.zerogram.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZerogramDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        var db = helper.createDatabase(TEST_DB, 5)

        // Insert a dummy record into V5 database (with base_iv column)
        db.execSQL("""
            INSERT INTO files (
                id, folder_id, display_name, mime_type, size_bytes, encrypted_size_bytes, 
                base_iv, checksum, telegram_message_id, telegram_file_id, upload_status, 
                created_at, updated_at, deleted_at
            ) VALUES (
                'file_1', NULL, 'test_file.txt', 'text/plain', 100, 150, 
                'dummy_iv', NULL, NULL, NULL, 'completed', 
                1000, 1000, NULL
            )
        """.trimIndent())
        
        // Verify base_iv column exists in V5
        var cursor = db.query("SELECT * FROM files WHERE id = 'file_1'")
        cursor.moveToFirst()
        assertEquals("dummy_iv", cursor.getString(cursor.getColumnIndex("base_iv")))
        cursor.close()
        
        db.close()

        // Re-open the database with version 6 and provide Migration5To6
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            ZerogramDatabase.Migration5To6()
        )
        
        // Verify base_iv column no longer exists
        cursor = db.query("SELECT * FROM files WHERE id = 'file_1'")
        cursor.moveToFirst()
        val columnIndex = cursor.getColumnIndex("base_iv")
        assertTrue("Column base_iv should not exist", columnIndex == -1)
        
        // Verify data was preserved
        assertEquals("test_file.txt", cursor.getString(cursor.getColumnIndex("display_name")))
        cursor.close()
    }
}
