package wiki.asaf.wikisayit.ui.welcome

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WelcomeViewModelTest {
    @Test
    fun `onGetStartedClicked increments the tap count`() =
        runTest {
            val viewModel = WelcomeViewModel()

            assertEquals(0, viewModel.uiState.value.getStartedTapCount)

            viewModel.onGetStartedClicked()
            viewModel.onGetStartedClicked()

            assertEquals(2, viewModel.uiState.value.getStartedTapCount)
        }
}
