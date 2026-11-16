package com.github.andreyasadchy.xtra.ui.common

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import kotlinx.coroutines.launch

/**
 * A DialogFragment that shows a Lagoon bottom sheet (plan 6) in place of an old dialog, so the callers that show it
 * with a fragment manager (the player, the old pages) keep working unchanged until their own stage. Its own window is
 * transparent and undimmed; the sheet brings its scrim. [close] slides the sheet away before the fragment goes.
 */
abstract class ComposeSheetDialog : DialogFragment() {

    private var closeSheet: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar).apply {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LagoonTheme {
                    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val scope = rememberCoroutineScope()
                    closeSheet = {
                        scope.launch { state.hide() }.invokeOnCompletion { if (isAdded) dismissAllowingStateLoss() }
                    }
                    LagoonBottomSheet(onDismiss = { if (isAdded) dismissAllowingStateLoss() }, state = state, title = sheetTitle()) {
                        SheetContent()
                    }
                }
            }
        }
    }

    /** Slides the sheet down, then dismisses the dialog. */
    fun close() {
        closeSheet?.invoke() ?: dismissAllowingStateLoss()
    }

    @Composable
    protected open fun sheetTitle(): String? = null

    @Composable
    protected abstract fun ColumnScope.SheetContent()
}
