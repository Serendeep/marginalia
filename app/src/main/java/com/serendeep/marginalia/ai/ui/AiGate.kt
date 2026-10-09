package com.serendeep.marginalia.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** True once a provider is connected; AI surfaces stay hidden until then, except the sidebar's connect row. */
@Composable
fun rememberAiReady(viewModel: AiSettingsViewModel = hiltViewModel()): Boolean {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    return aiReady(config, status)
}
