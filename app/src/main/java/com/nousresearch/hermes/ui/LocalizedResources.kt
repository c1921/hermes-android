package com.nousresearch.hermes.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun Modifier.localizedContentDescription(@StringRes resource: Int, vararg arguments: Any): Modifier {
    val description = stringResource(resource, *arguments)
    return semantics { contentDescription = description }
}
