package com.example.myapplication

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.radialGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.util.lerp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin


class MainActivity : ComponentActivity() {
    private fun ensurePermissionAllowed() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            println("PERMISSION TO RECORD AUDIO DENIED.  REQUESTING.")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }
        else {
            println("PERMISSION TO RECORD AUDIO GRANTED.")
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            println("PERMISSION TO RECORD AUDIO DENIED.  REQUESTING.")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.MODIFY_AUDIO_SETTINGS), 2)
        }
        else {
            println("PERMISSION TO RECORD AUDIO GRANTED.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissionAllowed();
        enableEdgeToEdge();

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    VisualizerCircle()
                }
            }
        }
    }
}

@Composable
fun VisualizerCircleRGB(
    modifier : Modifier = Modifier,
    radius : Float = 300f,
    barDistance : Float = 20f,
    lineHeight: Float = 600f,
    minHertz : Float = 20f,
    maxHertz : Float = 15000f,
) {
    var prevTime = remember { System.currentTimeMillis() };
    val visualizerHelper : VisualizerHelper = remember {
        VisualizerHelper();
    }
    var fft by remember {
        mutableStateOf(doubleArrayOf())
    }
    // INIT sa
    LaunchedEffect(Unit) {
        println("INIT");
        launch {
            while (true) {
                fft = visualizerHelper.GetTransformedFFT(0, 22050).copyOf();
                println(fft.toList());
                delay(16);
            }
        }
    }
    Canvas(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // FPS COUNTER

        val deltaTime : Float = 1 / ((System.currentTimeMillis() - prevTime) / 1000f);
        println("Delta Time : $deltaTime")
        prevTime = System.currentTimeMillis();

        val center: Size = size / 2f;
        val COUNT = fft.size - 1;
        var minVal = 0.0f;
        var maxVal = 0.0f;
        for (fftData in fft) {
            minVal = min(minVal, fftData.toFloat());
            maxVal = max(maxVal, fftData.toFloat());
        }
        val range = maxVal - minVal;
        val scaleFactor = range + 0.00001f;

        var hertz = minHertz;
        var barHeight = 0f;
        // Setup path
        val pathRight = Path();
        val pathLeft = Path();
        val beginOffset = Offset(
            cos(Math.PI / 2).toFloat() * radius + center.width,
            sin(Math.PI / 2).toFloat() * radius + center.height
        )
        pathRight.moveTo(beginOffset.x, beginOffset.y)
        pathLeft.moveTo(beginOffset.x,beginOffset.y)
        for (i in 0..COUNT) {
            val xOffset: Float = barDistance * i - barDistance * COUNT / 2;
            val angle = i.toFloat() / COUNT.toFloat() * 1.0f * Math.PI;
            barHeight = (barHeight + lineHeight *
                    ((visualizerHelper.GetVolumeFrequency(hertz.roundToInt()) - minVal) / scaleFactor
                             * lerp(0.3f, maxVal, Easing( i.toFloat() / COUNT.toFloat(), EasingType.OutQuad)))
                    / 5) / 2;
            // Right
            val direction = Offset(
                cos(angle - Math.PI / 2).toFloat(),
                sin(angle - Math.PI / 2).toFloat()
            );
            val middle = direction * radius + Offset(center.width, center.height)// + Offset(0f, -200f);
            val nextPoint = (middle + direction * barHeight);
            pathRight.lineTo(
                nextPoint.x,
                nextPoint.y
            )
            // Left
            val directionMirrored = Offset(
                x = cos(-angle - Math.PI / 2).toFloat(),
                y = sin(-angle - Math.PI / 2).toFloat()
            );
            val middleMirrored = directionMirrored * radius + Offset(center.width, center.height)// + Offset(0f, -200f);
            val nextPointMirrored = (middleMirrored + directionMirrored * barHeight);
            pathLeft.lineTo(
                nextPointMirrored.x,
                nextPointMirrored.y,
            )
            hertz = lerp(minHertz, maxHertz, Easing((i + 1).toFloat() / COUNT.toFloat(), EasingType.InQuad));
        }
        pathRight.lineTo(
            beginOffset.x,
            beginOffset.y
        )
        pathLeft.lineTo(
            beginOffset.x,
            beginOffset.y
        )
        val brush = radialGradient(
            0.0f to Color(0xFF020024),
            0.4f to Color(0xFF090979),
            1.0f to Color(0xFF00d4ff),
            center = Offset(center.width, center.height),
            radius = radius + lineHeight / 10f
        );
        val resultPath = Path.combine(PathOperation.Union, pathLeft, pathRight);
        val blendMode = BlendMode.Lighten; // BlendMode.Multiply for white background, BlendMode.Lighten for black background
        rotate(degrees = 0.5f) {
            drawPath(
                resultPath,
                Color.Red,
                blendMode = blendMode
            )
        }
        rotate(degrees = -0.5f) {
            drawPath(
                resultPath,
                Color.Green,
                blendMode = blendMode
            )
        }
        rotate(degrees = 0f) {
            drawPath(
                resultPath,
                Color.Blue,
                blendMode = blendMode
            )
        }
    }


}
@Composable
fun VisualizerCircle(
    modifier : Modifier = Modifier,
    radius : Float = 300f,
    barDistance : Float = 100f,
    lineHeight: Float = 600f,
    minHertz : Float = 20f,
    maxHertz : Float = 15000f,
) {
    var prevTime = remember { System.currentTimeMillis() };
    val visualizerHelper: VisualizerHelper = remember {
        VisualizerHelper();
    }
    var fft by remember {
        mutableStateOf(doubleArrayOf())
    }
    // INIT sa
    LaunchedEffect(Unit) {
        println("INIT");
        launch {
            while (true) {
                fft = visualizerHelper.GetTransformedFFT(0, 22050).copyOf();
                println(fft.toList());
                delay(16);
            }
        }
    }
    Canvas(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // FPS COUNTER


        val deltaTime: Float = 1 / ((System.currentTimeMillis() - prevTime) / 1000f);
        println("Delta Time : $deltaTime")
        prevTime = System.currentTimeMillis();

        val center: Size = size / 2f;

        val COUNT = fft.size - 1;
        var minVal = 0.0f;
        var maxVal = 0.0f;
        for (fftData in fft) {
            minVal = min(minVal, fftData.toFloat());
            maxVal = max(maxVal, fftData.toFloat());
        }
        val range = maxVal - minVal;
        val scaleFactor = range + 0.00001f;

        val brush = radialGradient(
            0.0f to Color(0xFF020024),
            0.5f to Color(0xFF090979),
            1.0f to Color(0xFF00d4ff),
            center = Offset(center.width, center.height),
            radius = radius + lineHeight / 5
        );

        var hertz = minHertz;
        var barHeight = 0f;
        // Setup path
        val pathRight = Path();
        val pathLeft = Path();
        val beginOffset = Offset(
            cos(Math.PI / 2).toFloat() * radius + center.width,
            sin(Math.PI / 2).toFloat() * radius + center.height
        )
        pathRight.moveTo(beginOffset.x, beginOffset.y)
        pathLeft.moveTo(beginOffset.x, beginOffset.y)
        for (i in 0..COUNT) {
            val xOffset: Float = barDistance * i - barDistance * COUNT / 2;
            val angle = i.toFloat() / COUNT.toFloat() * 1.0f * Math.PI;
            barHeight = (barHeight + lineHeight *
                    ((visualizerHelper.GetVolumeFrequency(hertz.roundToInt()) - minVal) / scaleFactor
                            * lerp(
                        0.3f,
                        maxVal,
                        Easing(i.toFloat() / COUNT.toFloat(), EasingType.OutQuad)
                    ))
                    / 5) / 2;
            // Right
            val direction = Offset(
                cos(angle - Math.PI / 2).toFloat(),
                sin(angle - Math.PI / 2).toFloat()
            );
            val middle =
                direction * radius + Offset(center.width, center.height)// + Offset(0f, -200f);
            val nextPoint = (middle + direction * barHeight);
            pathRight.lineTo(
                nextPoint.x,
                nextPoint.y
            )
            // Left
            val directionMirrored = Offset(
                x = cos(-angle - Math.PI / 2).toFloat(),
                y = sin(-angle - Math.PI / 2).toFloat()
            );
            val middleMirrored = directionMirrored * radius + Offset(
                center.width,
                center.height
            )// + Offset(0f, -200f);
            val nextPointMirrored = (middleMirrored + directionMirrored * barHeight);
            pathLeft.lineTo(
                nextPointMirrored.x,
                nextPointMirrored.y,
            )
            hertz = lerp(
                minHertz,
                maxHertz,
                Easing((i + 1).toFloat() / COUNT.toFloat(), EasingType.InQuad)
            );
        }
        pathRight.lineTo(
            beginOffset.x,
            beginOffset.y
        )
        pathLeft.lineTo(
            beginOffset.x,
            beginOffset.y
        )
        val resultPath = Path.combine(PathOperation.Union, pathLeft, pathRight);
        drawPath(
            resultPath,
            brush
        )
    }
}
