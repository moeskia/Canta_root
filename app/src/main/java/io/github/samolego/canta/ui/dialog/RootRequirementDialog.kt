package io.github.samolego.canta.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.samolego.canta.R
import io.github.samolego.canta.ui.theme.GreenOk
import io.github.samolego.canta.ui.theme.Orange
import io.github.samolego.canta.util.root.RootPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootRequirementDialog(
    onClose: (shouldProceed: Boolean) -> Unit,
) {
    val isRootAvailable = RootPermission.isRootAvailable()
    val isRootGranted = RootPermission.isRootGranted()

    BasicAlertDialog(
        modifier =
        Modifier
            .fillMaxWidth(0.9f)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.shapes.large
            ),
        properties =
        DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
        ),
        onDismissRequest = { onClose(false) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = stringResource(R.string.root_required),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Description
            Text(
                text = stringResource(R.string.root_requirement_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Requirements list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Root access available
                RequirementItem(
                    text = stringResource(R.string.root_access_available),
                    isCompleted = isRootAvailable,
                    enabled = false,
                    onActionClick = null
                )

                // Root permission granted
                RequirementItem(
                    text = stringResource(R.string.root_permission_granted),
                    isCompleted = isRootGranted,
                    enabled = !isRootAvailable,
                    onActionClick = {
                        // Try to request root permission
                        RootPermission.isRootGranted()
                        onClose(RootPermission.isRootGranted())
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Close button
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onClose(false) },
            ) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun RequirementItem(
    text: String,
    isCompleted: Boolean,
    enabled: Boolean = true,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1.0f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status indicator dot
        Icon(
            imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Circle,
            contentDescription = null,
            tint = if (isCompleted) GreenOk else Orange,
            modifier = Modifier.size(16.dp)
        )

        // Requirement text
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
            if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )

        // Action button (only shown if not completed and action is available)
        if (!isCompleted && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(stringResource(R.string.grant_root_permission))
            }
        }
    }
}
