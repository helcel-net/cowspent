package net.helcel.cowspent.android.helper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlin.math.*
import androidx.core.graphics.toColorInt


private fun mLCHtoRBG(l: Float, c: Float, h: Float) : Int{
    val hRad = h * PI / 180
    val a = c * cos(hRad)
    val b = c * sin(hRad)
    return ColorUtils.LABToColor(l.toDouble(), a, b)
}


@Composable
fun ColorPicker(
    initialColor: Int,
    onColorChanged: (Int) -> Unit
) {
    // LCH state
    val initialLch = remember(initialColor) {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(initialColor, lab)
        val l = lab[0].toFloat()
        val c = sqrt(lab[1].pow(2) + lab[2].pow(2)).toFloat()
        val h = (atan2(lab[2], lab[1]) * 180 / PI).toFloat().let { if (it < 0) it + 360f else it }
        floatArrayOf(l, c, h)
    }

    var lightness by remember { mutableFloatStateOf(initialLch[0]) }
    var chroma by remember { mutableFloatStateOf(initialLch[1]) }
    var hue by remember { mutableFloatStateOf(initialLch[2]) }

    val currentColorInt = remember(lightness, chroma, hue) {
        val color = mLCHtoRBG(lightness,chroma,hue)
        onColorChanged(color)
        color
    }
    
    val currentColor = Color(currentColorInt)

    // HEX state
    var hexText by remember { mutableStateOf("%06X".format(0xFFFFFF and currentColorInt)) }
    var isHexValid by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    // Sync HEX with LCH
    LaunchedEffect(currentColorInt) {
        val newHex = "%06X".format(0xFFFFFF and currentColorInt)
        if (hexText.uppercase() != newHex) {
            hexText = newHex
            isHexValid = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Selected Color",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                BasicTextField(
                    value = hexText,
                    onValueChange = { newText ->
                        val filtered = newText.removePrefix("#").filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6)
                        hexText = filtered
                        if (filtered.length == 6) {
                            try {
                                val parsedColor = "#$filtered".toColorInt()
                                val lab = DoubleArray(3)
                                ColorUtils.colorToLAB(parsedColor, lab)
                                lightness = lab[0].toFloat()
                                chroma = sqrt(lab[1].pow(2) + lab[2].pow(2)).toFloat()
                                hue = (atan2(lab[2], lab[1]) * 180 / PI).toFloat().let { if (it < 0) it + 360f else it }
                                isHexValid = true
                            } catch (_: Exception) {
                                isHexValid = false
                            }
                        } else {
                            isHexValid = false
                        }
                    },
                    modifier = Modifier
                        .width(104.dp)
                        .heightIn(min = 36.dp), // 1. Bypasses the default 56.dp constraint
                    textStyle = MaterialTheme.typography.body1.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colors.onSurface // Essential for BasicTextField visibility
                    ),
                    visualTransformation = { text ->
                        TransformedText(
                            AnnotatedString("#" + text.text),
                            object : OffsetMapping {
                                override fun originalToTransformed(offset: Int): Int = offset + 1
                                override fun transformedToOriginal(offset: Int): Int = if (offset < 1) 0 else offset - 1
                            }
                        )
                    },
                    singleLine = true,
                    interactionSource = interactionSource
                ) { innerTextField ->
                    @OptIn(ExperimentalMaterialApi::class)
                    TextFieldDefaults.TextFieldDecorationBox(
                        value = hexText,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = { text ->
                            TransformedText(
                                AnnotatedString("#" + text.text),
                                object : OffsetMapping {
                                    override fun originalToTransformed(offset: Int): Int =
                                        offset + 1

                                    override fun transformedToOriginal(offset: Int): Int =
                                        if (offset < 1) 0 else offset - 1
                                }
                            )
                        },
                        interactionSource = interactionSource,
                        isError = !isHexValid, // 2. Tells decoration box to draw the red error indicator line
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color.Transparent,
                            focusedIndicatorColor = currentColor,
                            unfocusedIndicatorColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                        ),
                        // 3. Reduces the vertical and horizontal internal padding
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val hueBrush = remember {
            Brush.horizontalGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
            )
        }
        LchSlider(
            label = "H",
            value = hue,
            range = 0f..360f,
            onValueChange = { hue = it },
            brush = hueBrush
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        LchSlider(
            label = "C",
            value = chroma,
            range = 0f..150f,
            onValueChange = { chroma = it },
            brush = Brush.horizontalGradient(listOf(
                Color(mLCHtoRBG(lightness,0f, hue)),
                Color(mLCHtoRBG(lightness,150f,hue))))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // LCH Sliders
        LchSlider(
            label = "L",
            value = lightness,
            range = 0f..100f,
            onValueChange = { lightness = it },
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.White))
        )
    }
}

@Composable
fun LchSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    brush: Brush
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.subtitle2)
        Spacer(Modifier.width(Dp(8f)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .shadow(2.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newValue = (offset.x / size.width).coerceIn(
                            0f,
                            1f
                        ) * (range.endInclusive - range.start) + range.start
                        onValueChange(newValue)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val newValue = (change.position.x / size.width).coerceIn(
                            0f,
                            1f
                        ) * (range.endInclusive - range.start) + range.start
                        onValueChange(newValue)
                    }
                }
        ) {
            // Handle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fraction = (value - range.start) / (range.endInclusive - range.start)
                val x = fraction * size.width
                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = Offset(x, size.height / 2),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.2f),
                    radius = 13.dp.toPx(),
                    center = Offset(x, size.height / 2),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ColorPickerPreview() {
    MaterialTheme {
        ColorPicker(initialColor = android.graphics.Color.BLUE, onColorChanged = {})
    }
}