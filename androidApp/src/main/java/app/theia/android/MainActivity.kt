package app.theia.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.theia.domain.model.CameraBodyId
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.FilmStockId
import app.theia.domain.model.LensId

private val SolarGold = Color(0xFFEAAE35)
private val DeepSky = Color(0xFF182344)
private val CosmicViolet = Color(0xFF2D2D57)
private val Ivory = Color(0xFFE7D7B7)
private val NearBlack = Color(0xFF121522)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TheiaSmokeTestScreen() }
    }
}

@Composable
private fun TheiaSmokeTestScreen() {
    val recipe = remember {
        CaptureRecipe(
            bodyId = CameraBodyId("body-default"),
            lensId = LensId("lens-default"),
            filmStockId = FilmStockId("film-default"),
        )
    }
    var message by remember { mutableStateOf(R.string.shared_domain_loaded) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NearBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSky)
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Θ",
                    color = SolarGold,
                    fontSize = 72.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = "Théia",
                    color = Ivory,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.android_development_build),
                    color = SolarGold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(36.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicViolet, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = stringResource(R.string.shared_foundation),
                        color = Ivory,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.capture_recipe),
                        color = SolarGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = recipe.id.value,
                        color = Ivory,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(message),
                        color = Ivory,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { message = R.string.recipe_validated },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SolarGold,
                            contentColor = NearBlack,
                        ),
                    ) {
                        Text(stringResource(R.string.test_shared_recipe))
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.camera_not_implemented),
                    color = Ivory.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
