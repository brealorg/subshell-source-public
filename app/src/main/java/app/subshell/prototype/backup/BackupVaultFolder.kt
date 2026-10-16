package app.subshell.prototype.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** One file in the backup folder. [handle] is provider-specific (a document Uri for SAF). */
internal data class VaultDocument(
    val name: String,
    val size: Long,
    val lastModifiedEpochMillis: Long,
    val handle: Any,
)

/**
 * BACKUP08_VAULT_FOLDER_SEAM_V1: the folder operations the backup sync
 * needs, so the sync rules can be tested without a DocumentsProvider.
 */
internal interface BackupVaultFolder {
    fun list(): List<VaultDocument>
    fun read(document: VaultDocument, maxChars: Int): String
    fun create(name: String, mimeType: String): VaultDocument
    fun write(document: VaultDocument, content: String)
    fun delete(document: VaultDocument)

    /** The renamed document, or null when the provider cannot rename. */
    fun rename(document: VaultDocument, newName: String): VaultDocument?
}

internal class SafBackupVaultFolder(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : BackupVaultFolder {
    override fun list(): List<VaultDocument> {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            // LOW_BACKUP_PARTIAL_LISTING_IS_NOT_A_LISTING_V1 (OCB-597): a cloud
            // provider still loading the folder returns part of it; a sync on
            // that would create duplicates of the files it did not list yet.
            if (cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true) {
                throw IllegalStateException("The backup folder is still loading")
            }
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    add(
                        VaultDocument(
                            name = name,
                            size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                            lastModifiedEpochMillis =
                                if (modifiedColumn < 0 || cursor.isNull(modifiedColumn)) 0L
                                else cursor.getLong(modifiedColumn),
                            handle = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                cursor.getString(idColumn),
                            ),
                        ),
                    )
                }
            }
        } ?: throw IllegalStateException("The selected backup folder is unavailable")
    }

    override fun read(document: VaultDocument, maxChars: Int): String =
        requireNotNull(resolver.openInputStream(document.handle as Uri)) {
            "Could not read ${document.name}"
        }.bufferedReader().use { reader -> reader.readBackupTextBounded(maxChars) }

    override fun create(name: String, mimeType: String): VaultDocument {
        val uri = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
            mimeType,
            name,
        ) ?: throw IllegalStateException("Could not create $name")
        return VaultDocument(name = name, size = 0L, lastModifiedEpochMillis = 0L, handle = uri)
    }

    override fun write(document: VaultDocument, content: String) {
        requireNotNull(resolver.openOutputStream(document.handle as Uri, "wt")) {
            "Could not write ${document.name}"
        }.bufferedWriter().use { writer -> writer.write(content) }
    }

    override fun delete(document: VaultDocument) {
        DocumentsContract.deleteDocument(resolver, document.handle as Uri)
    }

    override fun rename(document: VaultDocument, newName: String): VaultDocument? {
        val renamed = try {
            DocumentsContract.renameDocument(resolver, document.handle as Uri, newName)
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        return document.copy(name = newName, handle = renamed)
    }
}
