package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared building blocks for the dashboard and widgets screens. */

internal val CardShape = RoundedCornerShape(14.dp)

@Composable
fun HeaderSection(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = EInk.Value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = EInk.Caption,
                fontSize = 13.sp
            )
        }
    }
}

/** Invitation to enter holdings; replaces the empty "Your stack" section. */
@Composable
internal fun StackSetupCard(onSetUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = tr("Your stack"),
                color = EInk.Value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr("See what your bitcoin is worth here and on the widget."),
                color = EInk.Caption,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        EInkChip(text = tr("Set up"), selected = true, onClick = onSetUp)
    }
}

/** Tappable section title that collapses or expands its metrics. */
@Composable
internal fun CategoryHeader(title: String, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (collapsed) tr("Expand") else tr("Collapse"), onClick = onClick)
            .semantics { stateDescription = if (collapsed) "Collapsed" else "Expanded" }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = EInk.Value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = " · $count",
            color = EInk.Caption,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = EInk.Caption,
            modifier = Modifier.rotate(rotation)
        )
    }
}

/** A grid item spanning both columns. */
internal fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}
