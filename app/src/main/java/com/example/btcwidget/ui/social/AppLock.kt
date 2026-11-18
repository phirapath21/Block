package com.example.btcwidget.ui.social

import android.content.Context
import com.example.btcwidget.ui.main.screenBackground
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.ui.main.EInk
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local PIN verifier. The PIN itself is never persisted. */
object AppPin {
    private const val ITERATIONS = 150_000

    fun save(context: Context, pin: String) {
        require(pin.matches(Regex("\\d{4,6}")))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        AppPrefs.setAppPin(context, Base64.encodeToString(salt, Base64.NO_WRAP), Base64.encodeToString(hash, Base64.NO_WRAP), pin.length)
    }

    fun verify(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("\\d{4,6}"))) return false
        val salt = AppPrefs.appPinSalt(context)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } ?: return false
        val expected = AppPrefs.appPinHash(context)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() } ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    /** Wrong PINs allowed before each further one makes you wait. */
    private const val FREE_TRIES = 5
    private const val FIRST_WAIT_MS = 30_000L
    private const val LONGEST_WAIT_MS = 60 * 60_000L

    /**
     * How long to wait after [failures] wrong PINs in a row: nothing for the first few, then
     * 30 s, doubling each time up to an hour, so guessing every PIN would take years.
     */
    fun waitAfter(failures: Int): Long =
        if (failures < FREE_TRIES) 0L
        else (FIRST_WAIT_MS shl (failures - FREE_TRIES).coerceAtMost(7)).coerceAtMost(LONGEST_WAIT_MS)

    /** Notes a wrong PIN and returns when the next try is allowed (epoch ms). */
    fun recordFailure(context: Context, now: Long = System.currentTimeMillis()): Long {
        val failures = AppPrefs.appPinFailures(context) + 1
        val until = now + waitAfter(failures)
        AppPrefs.setAppPinFailures(context, failures, until)
        return until
    }

    /** When the next try is allowed; a time further off than the longest wait (the clock moved back) is cut to it. */
    fun lockedUntil(context: Context, now: Long = System.currentTimeMillis()): Long =
        AppPrefs.appPinLockedUntil(context).coerceAtMost(now + LONGEST_WAIT_MS)
}

/** The app lock's state, shared by the gate, the guard and settings. */
object AppLockState {
    /** Whether the lock screen is up, so what's under it can be hidden from screen readers. */
    var locked by mutableStateOf(false)
        internal set
    /** Private things unlocked since Block last came back after the grace time. */
    var unlockedThisSession by mutableStateOf(false)
        internal set
    /** A private thing waiting for the PIN: what it is (said on the lock screen), and what to do then. */
    internal var request by mutableStateOf<Pair<String, () -> Unit>?>(null)
    /** When Block went to the background (elapsed realtime), while it's there. */
    internal var leftAt: Long? = null
    var hasPin by mutableStateOf(false)
        private set
    var scope by mutableStateOf(LockScope.APP)
        private set
    var after by mutableStateOf(LockAfter.NOW)
        private set

    /** Reads the settings; call after changing them. */
    fun load(context: Context) {
        hasPin = AppPrefs.hasAppPin(context)
        scope = LockScope.of(AppPrefs.appLockScope(context))
        after = LockAfter.of(AppPrefs.appLockAfterMs(context))
    }
}

/** Private things behind the app lock: the private key, wallet payments and zaps, messages. */
object AppLock {
    /** Whether opening a private thing would ask for the PIN (or face) now. */
    fun needsUnlock(): Boolean = LockRules.needsUnlock(AppLockState.hasPin, AppLockState.scope, AppLockState.unlockedThisSession)

    /**
     * Runs [action] at once, or once the PIN (or face) is given when private things are locked.
     * [reason] tells the user what's being unlocked ("Show your private key"). Cancelling the
     * lock screen drops the action. Call on the main thread.
     */
    fun guard(reason: String, action: () -> Unit) {
        if (!needsUnlock()) action() else AppLockState.request = reason to action
    }
}

/**
 * The lock screen: over the whole app after launch and each time it comes back from the
 * background (when the whole app is locked and the grace time has run out), and over any
 * private thing asked for through [AppLock.guard] (when only private things are locked).
 */
@Composable
fun AppLockGate(activity: FragmentActivity?) {
    val context = LocalContext.current
    remember { AppLockState.load(context) }
    var locked by remember { mutableStateOf(LockRules.lockedAtStart(AppLockState.hasPin, AppLockState.scope)) }
    val request = AppLockState.request
    // Up for the whole app, or for one private thing.
    val showing = locked || request != null
    // The PIN's length, so a wrong 4-digit PIN is told as soon as 4 digits are in (0: not known,
    // for PINs set before it was stored: then each length from 4 to 6 is tried).
    val pinLength = remember(showing) { AppPrefs.appPinLength(context) }
    val maxDigits = if (pinLength in 4..6) pinLength else 6
    var waitUntil by remember { mutableStateOf(AppPin.lockedUntil(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.SideEffect { AppLockState.locked = showing }
    var pin by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempt by remember { mutableStateOf(0) }
    val shakeX = remember { Animatable(0f) }
    val view = LocalView.current
    val biometricConfigured = AppPrefs.biometricLock(context)
    val canUseBiometric = biometricConfigured &&
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    val preferBiometric = AppPrefs.appLockPreference(context) == "BIOMETRIC" && canUseBiometric
    var useBiometric by remember(showing, preferBiometric) { mutableStateOf(preferBiometric) }

    // Leaving: note when, and with no grace time lock the whole app now (so Recents shows the lock).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        AppLockState.load(context)
        AppLockState.leftAt = android.os.SystemClock.elapsedRealtime()
        if (LockRules.lockOnLeave(AppLockState.hasPin, AppLockState.scope, AppLockState.after)) {
            locked = true
            pin = ""
            error = null
        }
        if (AppLockState.after == LockAfter.NOW) AppLockState.unlockedThisSession = false
    }
    // Back: past the grace time, lock again (the whole app, or private things next time).
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (LockRules.expired(AppLockState.leftAt, android.os.SystemClock.elapsedRealtime(), AppLockState.after)) {
            if (LockRules.lockedAtStart(AppLockState.hasPin, AppLockState.scope)) locked = true
            AppLockState.unlockedThisSession = false
        }
        AppLockState.leftAt = null
    }
    if (!showing) return
    // The whole app can't be backed out of; a private thing can (and isn't opened).
    BackHandler(enabled = true) { if (!locked) AppLockState.request = null }

    fun unlocked() {
        AppPrefs.clearAppPinFailures(context)
        locked = false
        AppLockState.unlockedThisSession = true
        pin = ""
        error = null
        val then = AppLockState.request
        AppLockState.request = null
        then?.second?.invoke()
    }
    // Count down while a wait after wrong PINs is on.
    LaunchedEffect(waitUntil) {
        now = System.currentTimeMillis()
        while (now < waitUntil) { kotlinx.coroutines.delay(1_000); now = System.currentTimeMillis() }
    }
    val waiting = now < waitUntil

    val prompt = remember(activity) {
        activity?.let { owner ->
            BiometricPrompt(owner, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    waitUntil = 0L
                    unlocked()
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        useBiometric = false
                        pin = ""
                        error = null
                    } else if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_CANCELED) {
                        error = message.toString()
                    }
                }
            })
        }
    }
    fun launchBiometric() {
        if (prompt == null) return
        error = null
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(tr("Unlock Block"))
                    .setSubtitle(tr("Use fingerprint or face"))
                    .setNegativeButtonText(tr("Use PIN"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()
            )
        }.onFailure { error = it.message ?: tr("Biometric unlock unavailable") }
    }

    LaunchedEffect(showing, preferBiometric) {
        if (showing && preferBiometric) launchBiometric()
    }

    LaunchedEffect(pin) {
        checking = false
        val complete = if (pinLength in 4..6) pin.length == pinLength else pin.length in 4..6
        if (complete && !waiting) {
            val entered = pin
            checking = true
            kotlinx.coroutines.delay(80)
            val valid = withContext(Dispatchers.Default) { AppPin.verify(context, entered) }
            if (pin != entered) return@LaunchedEffect
            checking = false
            if (valid) {
                waitUntil = 0L
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                unlocked()
            } else if (pinLength in 4..6 || entered.length == 6) {
                waitUntil = withContext(Dispatchers.IO) { AppPin.recordFailure(context) }
                error = tr("Incorrect PIN")
                failedAttempt++
                if (android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
            }
        }
    }

    LaunchedEffect(failedAttempt) {
        if (failedAttempt > 0) {
            shakeX.animateTo(14f, tween(45)); shakeX.animateTo(-14f, tween(70))
            shakeX.animateTo(10f, tween(60)); shakeX.animateTo(-6f, tween(55)); shakeX.animateTo(0f, tween(45))
            kotlinx.coroutines.delay(180)
            pin = ""
            error = null
        }
    }

    // Every touch stops here: the app under the lock screen must not be tapped through it.
    Box(
        Modifier.fillMaxSize().screenBackground()
            // A touch goes only to the top layer that handles touches, so listening here is
            // enough to keep it from the app underneath. Nothing is consumed: consuming froze
            // the PIN keys (they lost each tap, or saw it cancelled by the slightest move).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = 22.dp, vertical = 18.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp), color = EInk.Body,
                border = androidx.compose.foundation.BorderStroke(1.dp, EInk.BodyStroke),
                modifier = Modifier.graphicsLayer { translationX = shakeX.value }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = EInk.Accent.copy(alpha = .14f), modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (useBiometric) Glyphs.Person else Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(26.dp))
                        }
                    }
                    // For one private thing: say what's being unlocked.
                    val forThing = if (!locked) request?.first else null
                    Text(
                        if (forThing != null) tr("Unlock to continue") else tr("Welcome back"),
                        color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    if (forThing != null) Text(forThing, color = EInk.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr(if (useBiometric) "Unlock with fingerprint or face" else "Enter your PIN to continue"),
                        color = EInk.Caption, fontSize = 14.sp
                    )
                    if (useBiometric) {
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = EInk.Background,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = ::launchBiometric)
                        ) {
                            Column(Modifier.padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Glyphs.Person, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(38.dp))
                                Text(tr("Touch the sensor to unlock"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(tr("Use fingerprint or face"), color = EInk.Caption, fontSize = 13.sp)
                            }
                        }
                        error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp) }
                        Surface(
                            shape = RoundedCornerShape(50), color = EInk.Accent,
                            modifier = Modifier.fillMaxWidth().height(50.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Try biometric again"), onClick = ::launchBiometric)
                        ) { Box(contentAlignment = Alignment.Center) { Text(tr("Unlock with biometrics"), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold) } }
                    } else {
                        Row(Modifier.height(30.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (pin.isEmpty()) Text(tr("PIN"), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            repeat(pin.length) { index ->
                                val dotScale by animateFloatAsState(
                                    targetValue = if (checking && index == pin.lastIndex) 1.22f else 1f,
                                    animationSpec = tween(180), label = "pin-dot-$index"
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (error != null) EInk.Alert else EInk.Accent,
                                    modifier = Modifier.size(12.dp).graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                                ) {}
                            }
                        }
                        error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp) }
                        if (waiting) {
                            val seconds = ((waitUntil - now + 999) / 1000).coerceAtLeast(1)
                            Text(
                                tr("Too many wrong PINs. Try again in {0}", if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else tr("{0} s", seconds.toString())),
                                color = EInk.Alert, fontSize = 13.sp
                            )
                        }
                        PinKeypad(
                            onDigit = { digit ->
                                if (pin.length < maxDigits) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    pin += digit
                                    error = null
                                }
                            },
                            onDelete = {
                                if (pin.isNotEmpty()) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    pin = pin.dropLast(1)
                                }
                                error = null
                            },
                            enabled = error == null && !waiting
                        )
                    }
                    if (canUseBiometric && prompt != null) {
                        Text(
                            tr(if (useBiometric) "Use PIN instead" else "Use fingerprint or face instead"),
                            color = EInk.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                useBiometric = !useBiometric
                                pin = ""
                                error = null
                                if (useBiometric) launchBiometric()
                            }.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            if (!locked) Text(
                tr("Cancel"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { AppLockState.request = null }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
            Text(tr("Your private data stays on this device."), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit, enabled: Boolean) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                row.forEach { digit -> PinKey(digit, enabled = enabled, onClick = { onDigit(digit) }) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PinKey(tr("Delete last digit"), icon = "⌫", enabled = enabled, onClick = onDelete)
            PinKey("0", enabled = enabled, onClick = { onDigit("0") })
            Spacer(Modifier.size(68.dp))
        }
    }
}

@Composable
private fun PinKey(label: String, icon: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = EInk.Background,
        border = androidx.compose.foundation.BorderStroke(1.dp, EInk.BodyStroke),
        modifier = Modifier.size(68.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = enabled, onClickLabel = label, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon ?: label, color = EInk.Value, fontSize = if (icon == null) 22.sp else 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * A private screen (messages, wallet) behind the app lock when only private things are locked:
 * a lock card with Unlock until the PIN (or face) is given this session, then [content].
 * [askAtOnce] opens the lock screen straight away (a screen opened on purpose); off for
 * screens that may be composed off-screen, such as a tab beside the one shown.
 */
@Composable
fun PrivateContent(reason: String, askAtOnce: Boolean = false, content: @Composable () -> Unit) {
    if (!AppLock.needsUnlock()) { content(); return }
    if (askAtOnce) LaunchedEffect(Unit) { AppLock.guard(reason) {} }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = EInk.Accent.copy(alpha = .14f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(28.dp)) }
            }
            Text(tr("Locked"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(reason, color = EInk.Caption, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Surface(
                shape = RoundedCornerShape(50), color = EInk.Accent,
                modifier = Modifier.padding(top = 8.dp).height(48.dp).clip(RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { AppLock.guard(reason) {} }
            ) {
                Box(Modifier.padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                    Text(tr("Unlock"), color = EInk.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
