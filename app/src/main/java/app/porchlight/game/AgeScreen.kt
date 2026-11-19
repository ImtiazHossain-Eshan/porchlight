package app.porchlight.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** The year this phone thinks it is. */
internal fun thisYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

/**
 * The first-launch age screen: a plain year-of-birth picker with nothing chosen, nothing suggested,
 * and no reward or hint for any answer. The year stays on this phone; it decides only what kind of
 * ad may be shown (see [audienceFor]). Nothing about ads happens until it is answered.
 */
@Composable
internal fun AgeScreen(onYear: (Int) -> Unit) {
    val now = remember { thisYear() }
    val years = remember { (now downTo now - 100).toList() }
    var picked by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .swallowTaps()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(10.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(10.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "What year were you born?",
                style = Display, fontSize = 24.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "It stays on this phone.",
                color = Craft.parchmentInkSoft, fontSize = TypeScale.Body, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(1.dp, Craft.parchmentShade, RoundedCornerShape(8.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(years) { year ->
                    val chosen = year == picked
                    Box(
                        Modifier
                            .height(44.dp)
                            .then(
                                if (chosen) Modifier.plank(Wood.Oak, corner = 8.dp, elevation = 3.dp, seed = year)
                                    .border(1.5.dp, BrassRule, RoundedCornerShape(8.dp))
                                else Modifier.border(1.dp, SolidColor(Craft.parchmentShade.copy(alpha = 0.6f)), RoundedCornerShape(8.dp)),
                            )
                            .clickable(role = Role.RadioButton) { picked = year }
                            .semantics {
                                selected = chosen
                                contentDescription = "$year"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$year", color = if (chosen) Dusk.paperInk else Craft.parchmentInk, fontSize = TypeScale.Body, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Nothing to continue with until a year is picked.
            Box(Modifier.fillMaxWidth().alpha(if (picked == 0) 0.45f else 1f)) {
                PrimaryButton("Continue", Modifier.fillMaxWidth()) { if (picked != 0) onYear(picked) }
            }
        }
    }
}
