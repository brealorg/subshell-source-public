package app.subshell.prototype.ui

import app.subshell.prototype.model.GalleryItem
import app.subshell.prototype.model.MediaFormat
import app.subshell.prototype.ui.components.MediaDownloadUiState
import app.subshell.prototype.ui.components.mediaDownloadDetail
import app.subshell.prototype.ui.components.mediaDownloadTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaDownloadTest {
    private fun item(id: String) = GalleryItem(id = id, label = id, format = MediaFormat.IMAGE)

    @Test
    fun aGalleryIsSavedItemByItemAndOneFailureDoesNotStopTheRest() = runBlocking {
        val progress = mutableListOf<Pair<Int, Int>>()
        val tried = mutableListOf<String>()
        val result = downloadGalleryItems(
            items = listOf(item("a"), item("b"), item("c")),
            download = { item ->
                tried += item.id
                if (item.id == "b") null else "Downloads/subshell/subshell-${item.id}.jpg"
            },
            onProgress = { done, total -> progress += done to total },
        )
        assertEquals(listOf("a", "b", "c"), tried)
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(
            listOf("Downloads/subshell/subshell-a.jpg", "Downloads/subshell/subshell-c.jpg"),
            result.savedPaths,
        )
        assertEquals(1, result.failed)
    }

    @Test
    fun theFolderIsThePathWithoutTheFileName() {
        assertEquals("Downloads/subshell", mediaDownloadFolder("Downloads/subshell/subshell-a.jpg"))
        assertEquals("name.jpg", mediaDownloadFolder("name.jpg"))
    }

    @Test
    fun titlesAndDetails() {
        assertEquals("Saving", mediaDownloadTitle(MediaDownloadUiState.Saving(0, 1)))
        assertEquals("Saving 3 of 12", mediaDownloadTitle(MediaDownloadUiState.Saving(3, 12)))
        assertEquals("Saved", mediaDownloadTitle(MediaDownloadUiState.Saved(1, 1, "Downloads/subshell")))
        assertEquals("Saved 12 items", mediaDownloadTitle(MediaDownloadUiState.Saved(12, 12, "Downloads/subshell")))
        assertEquals("Saved 11 of 12", mediaDownloadTitle(MediaDownloadUiState.Saved(11, 12, "Downloads/subshell")))
        assertEquals("In Downloads/subshell", mediaDownloadDetail(MediaDownloadUiState.Saved(12, 12, "Downloads/subshell")))
        assertEquals(
            "In Downloads/subshell · 1 could not be saved",
            mediaDownloadDetail(MediaDownloadUiState.Saved(11, 12, "Downloads/subshell")),
        )
    }
}
