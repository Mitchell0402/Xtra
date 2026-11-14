package com.github.andreyasadchy.xtra.ui.login

import android.view.View
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonSlider
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.settings.LagoonLogo
import com.github.andreyasadchy.xtra.ui.settings.SheetButtons
import com.github.andreyasadchy.xtra.ui.settings.SheetExplanation

const val LoginStartTag = "loginStart"
const val LoginCodeTag = "loginCode"

/** Which of the login steps is on screen (plan 7.7). The flow and every request stay in LoginActivity, unchanged. */
enum class LoginStep {
    /** Brand, one sentence, one button; the first web page already loads behind it. */
    Intro,
    /** Step 1: the Twitch web login (or the Helix authorize page) in the first web view. */
    Web,
    /** Step 2 in the app: the Android TV login page in the second web view, then twitch.tv/activate in the first. */
    TvWeb,
    /** Step 2 with an external browser: the device code from the API, entered at twitch.tv/activate. */
    Code,
    /** A token is being checked. */
    Busy,
}

/** The questions the login flow asks; each answer calls back into the activity. */
sealed interface LoginPrompt {
    /** "Log in as an Android TV device too?" after step 1. [external] is the code flow after an external login. */
    data class TvLogin(val external: Boolean, val onAnswer: (Boolean) -> Unit) : LoginPrompt
    /** "Having trouble?": open the authorize page in a browser, then paste the address it ends on. */
    data class Trouble(val onOpenUrl: () -> Unit, val onSubmit: (String) -> Unit) : LoginPrompt
    data class TextZoom(val zoom: Int, val onZoom: (Int) -> Unit) : LoginPrompt
}

@Stable
class LoginUiState {
    var step by mutableStateOf(LoginStep.Intro)
    /** Shown on the step-2 web page: the button below the TV login reads "Next". */
    var deviceCode by mutableStateOf<String?>(null)
    var prompt by mutableStateOf<LoginPrompt?>(null)
    /** The step the intro leads to, set when the activity knows which page loads first. */
    var afterIntro = LoginStep.Web
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    primary: WebView?,
    secondary: WebView?,
    toast: ToastHostState,
    onClose: () -> Unit,
    onTrouble: () -> Unit,
    onTextZoom: () -> Unit,
    onTvNext: () -> Unit,
    onCopyCode: () -> Unit,
    onOpenActivate: () -> Unit,
    onCodeNext: () -> Unit,
) {
    val colors = Lagoon.colors
    val step = state.step
    Box(Modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)))) {
            LoginTopBar(step, onClose, onTextZoom)
            Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
                // the web views stay in the composition so the pages keep loading while the intro shows
                if (primary != null) {
                    WebHost(primary, visible = step == LoginStep.Web || step == LoginStep.Intro, invisibleWhenHidden = step != LoginStep.Busy)
                }
                if (secondary != null) {
                    WebHost(secondary, visible = step == LoginStep.TvWeb, invisibleWhenHidden = true)
                }
                when (step) {
                    LoginStep.Intro -> Intro(onStart = { state.step = state.afterIntro })
                    LoginStep.Busy -> Busy()
                    LoginStep.Code -> CodePanel(state.deviceCode, onCopyCode, onOpenActivate, onCodeNext)
                    else -> Unit
                }
            }
            AnimatedVisibility(step == LoginStep.Web || step == LoginStep.TvWeb) {
                LoginBottomBar(step, onTrouble, onTvNext)
            }
        }
        ToastHost(toast, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 96.dp))
    }
    state.prompt?.let { prompt -> LoginPromptSheet(prompt, onDismiss = { state.prompt = null }) }
}

@Composable
private fun WebHost(view: WebView, visible: Boolean, invisibleWhenHidden: Boolean) {
    AndroidView(
        factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
        modifier = Modifier.fillMaxSize(),
        update = { it.visibility = if (visible) View.VISIBLE else if (invisibleWhenHidden) View.INVISIBLE else View.GONE },
    )
}

@Composable
private fun LoginTopBar(step: LoginStep, onClose: () -> Unit, onTextZoom: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LagoonIconButton(LucideIcons.X, stringResource(R.string.st_back), onClose, filled = false)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.login_title), style = Lagoon.type.headline, color = Lagoon.colors.textPrimary, modifier = Modifier.semantics { heading() })
            if (step != LoginStep.Intro) {
                Text(
                    stringResource(if (step == LoginStep.TvWeb || step == LoginStep.Code) R.string.login_step_tv else R.string.login_step_web),
                    style = Lagoon.type.label, color = Lagoon.colors.textSecondary,
                )
            }
        }
        if (step == LoginStep.Web || step == LoginStep.TvWeb) {
            LagoonIconButton(LucideIcons.Type, stringResource(R.string.text_size), onTextZoom)
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    val colors = Lagoon.colors
    Box(Modifier.fillMaxSize().background(colors.page), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LagoonLogo(size = 88.dp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.login_intro_title), style = Lagoon.type.title, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.login_intro_body), style = Lagoon.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            StepLine("1", stringResource(R.string.login_intro_step1))
            Spacer(Modifier.height(10.dp))
            StepLine("2", stringResource(R.string.login_intro_step2))
            Spacer(Modifier.height(32.dp))
            LagoonButton(stringResource(R.string.login_start), onStart, Modifier.fillMaxWidth().testTag(LoginStartTag), icon = LucideIcons.LogIn)
        }
    }
}

@Composable
private fun StepLine(number: String, text: String) {
    val colors = Lagoon.colors
    Row(
        Modifier.fillMaxWidth().background(colors.card, LagoonShapes.row).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        Box(Modifier.size(28.dp).background(colors.accentSoft, LagoonShapes.pill), contentAlignment = Alignment.Center) {
            Text(number, style = Lagoon.type.labelStrong, color = colors.accent)
        }
        Text(text, style = Lagoon.type.label, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Busy() {
    Box(Modifier.fillMaxSize().background(Lagoon.colors.page), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Lagoon.colors.accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.login_checking), style = Lagoon.type.label, color = Lagoon.colors.textSecondary)
        }
    }
}

/** The device code, big, with copy (plan 7.7: "大字显示验证码并可一键复制"). */
@Composable
private fun CodePanel(code: String?, onCopy: () -> Unit, onOpenActivate: () -> Unit, onNext: () -> Unit) {
    val colors = Lagoon.colors
    Box(Modifier.fillMaxSize().background(colors.page), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.login_code_title), style = Lagoon.type.title, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.login_code_body), style = Lagoon.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(LagoonShapes.card)
                    .background(colors.card)
                    .padding(vertical = 28.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                if (code != null) {
                    // the one place a bigger size than the type scale is right: the code is read off and typed elsewhere
                    Text(
                        code,
                        style = Lagoon.type.display.copy(fontSize = 40.sp, lineHeight = 48.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                        color = colors.accent,
                        modifier = Modifier.testTag(LoginCodeTag).semantics { contentDescription = code.toCharArray().joinToString(" ") },
                    )
                } else {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s)) {
                LagoonButton(stringResource(R.string.login_copy_code), onCopy, Modifier.weight(1f), style = ButtonStyle.Secondary, icon = LucideIcons.Copy, enabled = code != null)
                LagoonButton(stringResource(R.string.login_open_activate), onOpenActivate, Modifier.weight(1f), style = ButtonStyle.Secondary, icon = LucideIcons.ExternalLink)
            }
            Spacer(Modifier.height(12.dp))
            LagoonButton(stringResource(R.string.login_code_done), onNext, Modifier.fillMaxWidth(), enabled = code != null)
        }
    }
}

@Composable
private fun LoginBottomBar(step: LoginStep, onTrouble: () -> Unit, onTvNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Lagoon.colors.page).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (step == LoginStep.TvWeb) {
            Text(stringResource(R.string.login_tv_hint), style = Lagoon.type.label, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            LagoonButton(stringResource(R.string.next), onTvNext, Modifier.fillMaxWidth())
        } else {
            LagoonButton(stringResource(R.string.trouble_logging_in), onTrouble, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, icon = LucideIcons.CircleAlert)
        }
    }
}

@Composable
private fun LoginPromptSheet(prompt: LoginPrompt, onDismiss: () -> Unit) {
    when (prompt) {
        is LoginPrompt.TvLogin -> {
            // v0.1: dismissing the dialog in any way other than OK meant "no"
            var answered by remember { mutableStateOf(false) }
            val answer = { yes: Boolean ->
                if (!answered) {
                    answered = true
                    onDismiss()
                    prompt.onAnswer(yes)
                }
            }
            LagoonBottomSheet(onDismiss = { answer(false) }, title = stringResource(R.string.login_tv_title), skipHalfHeight = true) {
                Text(
                    stringResource(if (prompt.external) R.string.external_tv_login_message else R.string.tv_login_message),
                    style = Lagoon.type.body, color = Lagoon.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                )
                SheetButtons(
                    secondary = stringResource(R.string.login_tv_skip) to { answer(false) },
                    primary = stringResource(R.string.login_tv_continue) to { answer(true) },
                )
            }
        }
        is LoginPrompt.Trouble -> {
            var text by remember { mutableStateOf("") }
            LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.trouble_logging_in), skipHalfHeight = true) {
                Text(
                    stringResource(R.string.external_login_message), style = Lagoon.type.body, color = Lagoon.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
                )
                LagoonButton(
                    stringResource(R.string.open_url), prompt.onOpenUrl, Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
                    style = ButtonStyle.Secondary, icon = LucideIcons.ExternalLink,
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .background(Lagoon.colors.card, LagoonShapes.input)
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                        .semantics { contentDescription = "url" },
                    textStyle = Lagoon.type.label.copy(color = Lagoon.colors.textPrimary, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Lagoon.colors.accent),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text(stringResource(R.string.login_paste_hint), style = Lagoon.type.label, color = Lagoon.colors.textHint)
                            inner()
                        }
                    },
                )
                SheetButtons(
                    secondary = stringResource(R.string.st_cancel) to onDismiss,
                    primary = stringResource(R.string.login_paste_continue) to { onDismiss(); prompt.onSubmit(text) },
                    primaryEnabled = text.isNotBlank(),
                )
            }
        }
        is LoginPrompt.TextZoom -> {
            var zoom by remember { mutableFloatStateOf(prompt.zoom.toFloat()) }
            LagoonBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.text_size)) {
                SheetExplanation(stringResource(R.string.login_zoom_summary))
                LagoonSlider(
                    title = stringResource(R.string.st_value),
                    value = zoom,
                    onValueChange = { zoom = it; prompt.onZoom(it.toInt()) },
                    valueLabel = "${zoom.toInt()}%",
                    valueRange = 50f..200f,
                    steps = 14,
                )
                SheetButtons(secondary = stringResource(R.string.st_reset_default) to { zoom = 100f; prompt.onZoom(100) }, primary = stringResource(R.string.st_save) to onDismiss)
            }
        }
    }
}
