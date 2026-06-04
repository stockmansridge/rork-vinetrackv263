package com.rork.vinetrack.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.vinetrack.BuildConfig
import com.rork.vinetrack.data.AppConfig
import com.rork.vinetrack.ui.AuthFormState
import com.rork.vinetrack.ui.components.LoginVineyardBackground
import com.rork.vinetrack.ui.theme.VineColors

private enum class Mode(val label: String) { SignIn("Sign In"), SignUp("Sign Up") }

@Composable
fun LoginScreen(
    state: AuthFormState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onForgotPassword: (String, (Boolean) -> Unit) -> Unit,
) {
    var mode by remember { mutableStateOf(Mode.SignIn) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LoginVineyardBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            // Logo mark
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\uD83C\uDF47", fontSize = 48.sp)
            }
            Text("VineTrack", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Built by viticulturists to manage\nvineyard work, row by row.",
                color = Color.White.copy(alpha = 0.94f),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FeatureChip("GPS Pins", Modifier.weight(1f))
                FeatureChip("Row Tracking", Modifier.weight(1f))
                FeatureChip("Spray Records", Modifier.weight(1f))
            }

            // Mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Mode.entries.forEach { m ->
                    val selected = m == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) VineColors.LoginPickerActive else Color.Transparent)
                            .clickable { mode = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.label,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else Color(0xFF053A1A),
                        )
                    }
                }
            }

            // Form card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.96f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (mode == Mode.SignUp) {
                    LoginField(name, { name = it }, "Name", Icons.Filled.Person)
                }
                LoginField(email, { email = it }, "Email", Icons.Filled.Email, keyboardType = KeyboardType.Email)
                LoginField(
                    password, { password = it }, "Password", Icons.Filled.Lock,
                    keyboardType = KeyboardType.Password,
                    isSecure = true,
                    showSecure = showPassword,
                    onToggleSecure = { showPassword = !showPassword },
                )
            }

            // Action button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(VineColors.Primary)
                    .clickable(enabled = !state.isLoading && canSubmit(mode, name, email, password)) {
                        when (mode) {
                            Mode.SignIn -> onSignIn(email, password)
                            Mode.SignUp -> onSignUp(name, email, password)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (mode == Mode.SignIn) "Sign In" else "Create Account",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    )
                }
            }

            if (mode == Mode.SignIn) {
                TextButton(onClick = {
                    if (email.isNotBlank()) onForgotPassword(email) {}
                }) {
                    Text("Forgot password?", color = Color(0xFFEFEBB8), fontWeight = FontWeight.Medium)
                }
            }

            state.error?.let { message ->
                Text(
                    message,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(VineColors.Destructive.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (BuildConfig.DEBUG) {
                DebugConfigPanel()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebugConfigPanel() {
    val d = remember { AppConfig.diagnostics() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "DEBUG · Supabase config",
            color = Color(0xFFEFEBB8),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        DebugRow("Supabase URL present", d.supabaseUrlPresent.toString())
        DebugRow("Supabase URL", d.supabaseUrl)
        DebugRow("Config.kt key present", d.rorkConfigAnonKeyPresent.toString())
        DebugRow("Config.kt key length", d.rorkConfigAnonKeyLength.toString())
        DebugRow("BuildConfig key present", d.buildConfigAnonKeyPresent.toString())
        DebugRow("BuildConfig key length", d.buildConfigAnonKeyLength.toString())
        DebugRow("Final key present", d.finalAnonKeyPresent.toString())
        DebugRow("Final key length", d.finalAnonKeyLength.toString())
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun canSubmit(mode: Mode, name: String, email: String, password: String): Boolean {
    val base = email.isNotBlank() && password.isNotBlank()
    return if (mode == Mode.SignUp) base && name.isNotBlank() else base
}

@Composable
private fun FeatureChip(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isSecure: Boolean = false,
    showSecure: Boolean = false,
    onToggleSecure: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF055224)) },
        trailingIcon = if (isSecure && onToggleSecure != null) {
            {
                IconButton(onClick = onToggleSecure) {
                    Icon(
                        if (showSecure) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF055224).copy(alpha = 0.7f),
                    )
                }
            }
        } else null,
        singleLine = true,
        visualTransformation = if (isSecure && !showSecure) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
