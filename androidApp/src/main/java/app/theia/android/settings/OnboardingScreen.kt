package app.theia.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theia.android.R

private val OnboardingSolarGold = Color(0xFFEAAE35)
private val OnboardingDeepSky = Color(0xFF182344)
private val OnboardingCosmicViolet = Color(0xFF2D2D57)
private val OnboardingIvory = Color(0xFFE7D7B7)
private val OnboardingStone = Color(0xFFB6A58C)

/** First-run overview. The host records completion; no permissions are requested here. */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markDescription = stringResource(R.string.onboarding_mark_description)
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize(), color = OnboardingDeepSky, contentColor = OnboardingIvory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Θ",
                    color = OnboardingSolarGold,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.semantics { contentDescription = markDescription },
                )
                Text(
                    text = stringResource(R.string.onboarding_title),
                    color = OnboardingIvory,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.onboarding_intro),
                    color = OnboardingStone,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                OnboardingStep(1, stringResource(R.string.onboarding_step_one_title), stringResource(R.string.onboarding_step_one_body))
                Spacer(Modifier.height(10.dp))
                OnboardingStep(2, stringResource(R.string.onboarding_step_two_title), stringResource(R.string.onboarding_step_two_body))
                Spacer(Modifier.height(10.dp))
                OnboardingStep(3, stringResource(R.string.onboarding_step_three_title), stringResource(R.string.onboarding_step_three_body))
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnboardingSolarGold,
                        contentColor = OnboardingDeepSky,
                    ),
                ) {
                    Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_skip), color = OnboardingIvory)
                }
                Text(
                    text = stringResource(R.string.onboarding_permissions_note),
                    color = OnboardingStone,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun OnboardingStep(number: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnboardingCosmicViolet, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.onboarding_step_number, number),
            color = OnboardingSolarGold,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = OnboardingIvory, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
            Text(body, color = OnboardingStone, fontSize = 12.sp)
        }
    }
}
