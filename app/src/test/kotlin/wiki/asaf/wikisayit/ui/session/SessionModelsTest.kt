package wiki.asaf.wikisayit.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test
import wiki.asaf.wikisayit.network.MediaWikiApiException
import java.io.IOException

class SessionModelsTest {
    @Test
    fun `a fileexists API error classifies as name taken`() {
        val error = MediaWikiApiException(200, "fileexists-no-change", "already exists", "raw")
        val (status, message) = classifyUploadFailure(error)
        assertEquals(UploadFailureStatus.NAME_TAKEN, status)
        assertEquals("already exists", message)
    }

    @Test
    fun `a non-conflict API error classifies as retrying`() {
        val error = MediaWikiApiException(200, "internal_api_error", "server hiccup", "raw")
        val (status, message) = classifyUploadFailure(error)
        assertEquals(UploadFailureStatus.RETRYING, status)
        assertEquals("server hiccup", message)
    }

    @Test
    fun `a connectivity-level exception classifies as waiting`() {
        val error = IOException("Unable to resolve host")
        val (status, message) = classifyUploadFailure(error)
        assertEquals(UploadFailureStatus.WAITING, status)
        assertEquals("Unable to resolve host", message)
    }

    @Test
    fun `an entry is only complete once both real steps have succeeded`() {
        val partial = UploadEntryState(commonsDone = true, categoriesDone = true, p443Done = false)
        val complete = partial.copy(p443Done = true)
        assertEquals(false, partial.isComplete)
        assertEquals(true, complete.isComplete)
    }

    @Test
    fun `an entry needs attention only once a step has actually failed`() {
        val healthy = UploadEntryState()
        val failed = healthy.copy(failedStep = UploadStepFailure.COMMONS, failureStatus = UploadFailureStatus.WAITING)
        assertEquals(false, healthy.needsAttention)
        assertEquals(true, failed.needsAttention)
    }

    @Test
    fun `display text is the plain label when there are no script variants`() {
        val entry = QueueEntry(label = "בית", kind = EntryKind.FORM, detail = "lexeme form", formId = "L1-F1")
        assertEquals("בית", entry.displayText)
    }

    @Test
    fun `display text is the plain label when there is only one script variant`() {
        val entry =
            QueueEntry(
                label = "בית",
                kind = EntryKind.FORM,
                detail = "lexeme form",
                formId = "L1-F1",
                scriptVariants = listOf("בית"),
            )
        assertEquals("בית", entry.displayText)
    }

    @Test
    fun `display text joins every script variant, such as plain and niqqud spellings`() {
        val entry =
            QueueEntry(
                label = "בית",
                kind = EntryKind.FORM,
                detail = "lexeme form",
                formId = "L64262-F2",
                scriptVariants = listOf("בית", "בֵּית"),
            )
        assertEquals("בית  ·  בֵּית", entry.displayText)
    }
}
