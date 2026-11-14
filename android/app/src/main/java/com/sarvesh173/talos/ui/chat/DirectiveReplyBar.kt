package com.sarvesh173.talos.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.applePressable

/**
 * Custom free-text answer affordance for a directive question.
 *
 * Optically matches the app composer in AppleAgentChatScreen: a pill row
 * (minimum 52dp, 6dp/4dp padding), 15sp BasicTextField with the accent caret,
 * Send IME action, and a 48dp circular send button that turns accent
 * #0A84FF once there is text. The field takes focus on entry so a custom
 * answer can be typed immediately. Sends trimmed text through [onSend].
 */
@Composable
fun DirectiveReplyBar(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a custom answer"
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    fun send() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            text = ""
            onSend(trimmed)
        }
    }
    val hasText = text.isNotBlank()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2C2C2E))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = Color.White
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                }
                inner()
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .focusRequester(focusRequester)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (hasText) MaterialTheme.colorScheme.primary else Color(0xFF1C1C1E)
                )
                .applePressable(0.97f, pressHaptic = true) { send() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send custom answer",
                tint = if (hasText) Color.White else Color.White.copy(alpha = 0.40f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
