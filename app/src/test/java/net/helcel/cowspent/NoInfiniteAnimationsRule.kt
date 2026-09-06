package net.helcel.cowspent

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import kotlinx.coroutines.CancellationException
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Robolectric's paused looper only goes idle once nothing is left to run, but a Compose infinite
 * animation (the pull-to-refresh spinner, for instance) keeps requesting frames forever. Any test
 * that launches an activity whose UI shows one then burns minutes of CPU inside
 * ActivityScenario.launch() before it gives up.
 *
 * ComposeTestRule solves this by installing an InfiniteAnimationPolicy that cancels those
 * animations; this rule does the same for tests driving an activity directly.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NoInfiniteAnimationsRule : TestWatcher() {

    private object CancelInfiniteAnimations : InfiniteAnimationPolicy {
        override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
            throw CancellationException("Infinite animations are disabled in unit tests")
    }

    override fun starting(description: Description) {
        WindowRecomposerPolicy.setFactory { view ->
            view.createLifecycleAwareWindowRecomposer(CancelInfiniteAnimations)
        }
    }

    override fun finished(description: Description) {
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory.LifecycleAware)
    }
}
