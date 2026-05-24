package com.omnio.tv.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.omnio.tv.R
import com.omnio.tv.core.uishared.OmnioColors
import com.omnio.tv.domain.model.AuthState
import com.omnio.tv.ui.components.OmnioDialog

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SignInRequiredDialog(
    onSignInWithQr: () -> Unit,
    onSignInWithEmail: () -> Unit,
    viewModel: SignInRequiredViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    if (authState !is AuthState.SignedOut) return

    OmnioDialog(
        onDismiss = {},
        title = stringResource(R.string.sign_in_required_title),
        subtitle = stringResource(R.string.sign_in_required_subtitle),
        width = 560.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSignInWithQr,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.Secondary,
                    focusedContainerColor = OmnioColors.SecondaryVariant,
                    contentColor = OmnioColors.OnSecondary,
                    focusedContentColor = OmnioColors.OnSecondaryVariant
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.sign_in_required_button_qr),
                    modifier = Modifier.padding(vertical = 6.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onSignInWithEmail,
                colors = ButtonDefaults.colors(
                    containerColor = OmnioColors.BackgroundCard,
                    focusedContainerColor = OmnioColors.FocusBackground,
                    contentColor = OmnioColors.TextPrimary,
                    focusedContentColor = OmnioColors.TextPrimary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.sign_in_required_button_email),
                    modifier = Modifier.padding(vertical = 6.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
