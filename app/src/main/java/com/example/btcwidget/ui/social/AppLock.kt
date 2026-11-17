package com.example.btcwidget.ui.social

import android.content.Context
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
        AppPrefs.setAppPin(context, Base64.encodeToString(salt, Base64.NO_WRAP), Base64.encodeToString(hash, Base64.NO_WRAP))
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
}

/** Blocks the app after launch and each time it returns from the background. */
@Composable
fun AppLockGate(activity: FragmentActivity?) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(AppPrefs.hasAppPin(context)) }
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
    var useBiometric by remember(locked, preferBiometric) { mutableStateOf(preferBiometric) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (AppPrefs.hasAppPin(context)) {
            locked = true
            pin = ""
            error = null
        }
    }
    if (!locked) return
    BackHandler(enabled = true) { }

    val prompt = remember(activity) {
        activity?.let { owner ->
            BiometricPrompt(owner, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                    pin = ""
                    error = null
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

    LaunchedEffect(locked, preferBiometric) {
        if (locked && preferBiometric) launchBiometric()
    }

    LaunchedEffect(pin) {
        checking = false
        if (pin.length in 4..6) {
            val entered = pin
            checking = true
            kotlinx.coroutines.delay(80)
            val valid = withContext(Dispatchers.Default) { AppPin.verify(context, entered) }
            if (pin != entered) return@LaunchedEffect
            checking = false
            if (valid) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                locked = false
                pin = ""
                error = null
            } else if (entered.length == 6) {
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

    Box(Modifier.fillMaxSize().background(EInk.Background), contentAlignment = Alignment.Center) {
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
                    Text(tr("Welcome back"), color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        tr(if (useBiometric) "Unlock with fingerprint or face" else "Enter your PIN to continue"),
                        color = EInk.Caption, fontSize = 14.sp
                    )
                    if (useBiometric) {
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = EInk.Background,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = ::launchBiometric)
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
                            modifier = Modifier.fillMaxWidth().height(50.dp).clickable(onClickLabel = tr("Try biometric again"), onClick = ::launchBiometric)
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
                        PinKeypad(
                            onDigit = { digit ->
                                if (pin.length < 6) {
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
                            enabled = error == null
                        )
                    }
                    if (canUseBiometric && prompt != null) {
                        Text(
                            tr(if (useBiometric) "Use PIN instead" else "Use fingerprint or face instead"),
                            color = EInk.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable {
                                useBiometric = !useBiometric
                                pin = ""
                                error = null
                                if (useBiometric) launchBiometric()
                            }.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
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
        modifier = Modifier.size(68.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(icon ?: label, color = EInk.Value, fontSize = if (icon == null) 22.sp else 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}
