package com.example.myapplication

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataInputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.nextInt


class MainActivity : ComponentActivity() {
    private fun ensurePermissionAllowed() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            println("PERMISSION TO RECORD AUDIO DENIED.  REQUESTING.")
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
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

                    GreetingImage()
                }
            }
        }
    }
}

fun hzToFftIndex(Hz: Int, size : Int, samplingRate: Int): Int {
    return (Hz * size / (44100 * 2)).coerceIn(0, 255);
    // return Math.min(Math.max(Hz * size / (samplingRate / 1000), 0), 255)
}

fun dB(x: Double) : Double {
    if (x == 0.0)
        return 0.0;
    else
        return 10.0 * log10(x);
}
var frequencyMap : MutableList<Pair<Int, Double>> = mutableListOf();
fun getVolumeFrequency(Hz: Int) : Float {
    var beginHz : Pair<Int, Double> = Pair<Int, Double>(0, 0.0);
    var endHz : Pair<Int, Double> = Pair<Int, Double>(0, 0.0);
    for (i in 0 until frequencyMap.size) {
        val fre = frequencyMap[i];
        if (fre.first > Hz) {
            beginHz = frequencyMap[i - 1];
            endHz = frequencyMap[i];
            break;
        }
    }
    if (endHz.first - beginHz.first == 0)
        return 0f;
    return lerp(
        beginHz.second.toFloat(),
        endHz.second.toFloat(),
        (Hz - beginHz.first).toFloat() / (endHz.first - beginHz.first).toFloat()) /// Random.nextDouble(1.0, 1.5).toFloat());
}

fun transformFftMagnitude(fftBytes : List<Byte>, prevFFTM: DoubleArray, visualizer: Visualizer) : DoubleArray {
    if (fftBytes.size <= 0)
        return doubleArrayOf();
    val smoothing = 0.8;
    val fftM : DoubleArray = DoubleArray(fftBytes.size / 2 - 1);
    frequencyMap.clear();
    val samplingRate = visualizer.samplingRate;
    val captureSize = visualizer.captureSize;
    println();
    print("Data: ");
    for (k in 0 until fftM.size) {
        val i = (k + 1) * 2
        var real = fftBytes[i].toDouble();
        var img = fftBytes[i + 1].toDouble();
//        var maxVal : Float = 0.0f;
//        maxVal = max(abs(fftBytes[i].toFloat()), abs(fftBytes[i].toFloat()));
//        if (maxVal != 0.0f){
//            real /= maxVal;
//            img /= maxVal;
//        }
        fftM[k] = dB((hypot(real, img))) *  (1.0 + (k.toFloat() / fftM.size.toFloat()));
        print("${fftM[k]} ");
        fftM[k] = (smoothing) * prevFFTM[k] + ((1 - smoothing) * fftM[k]);
    }
    println();
    for (i in 0 until fftM.size) {
        var average = fftM[i];
        if (i > 0)
            average += fftM[i - 1];
        if (i < fftM.size - 1)
            average += fftM[i + 1];
        average /= 3;
        fftM[i] = average;
        val fre = i * (samplingRate / 1000) / captureSize;
        frequencyMap.add(Pair<Int, Double>(fre, fftM[i]));
    }
    return fftM;
}

@Composable
fun GreetingImage(modifier: Modifier = Modifier) {
    var prevTime = remember { System.currentTimeMillis() };
    val context = LocalContext.current;
    var fftBytes by remember {
        mutableStateOf(emptyList<Byte>());
    };
    val mediaPlayer : MediaPlayer = remember {
        println("media");
        val mediaPlayer = MediaPlayer.create(context, R.raw.dont);
        mediaPlayer.isLooping = true;
        mediaPlayer.seekTo(55000);
        mediaPlayer.start();
        mediaPlayer;
    }
    val visualizer : Visualizer = remember {
        val visualizer : Visualizer = Visualizer(mediaPlayer.audioSessionId);
        visualizer.setCaptureSize(1024);
        visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
//        visualizer.setDataCaptureListener(
//            object : Visualizer.OnDataCaptureListener {
//                override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, sampleRate: Int) {
//                    if (data != null) {
//                        fftBytes = data.toList();
//                    }
//                }
//
//                override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, sampleRate: Int) = Unit;
//            },
//            Visualizer.getMaxCaptureRate(),
//            false,
//            true);
        visualizer.setEnabled(true);
        visualizer;
    }
    var prevFFTM : DoubleArray = remember {
        DoubleArray(visualizer.captureSize / 2)
    };
    var fftM by remember {
        mutableStateOf(DoubleArray(0))
    }
    // var counter by remember { mutableStateOf(0) };
    // INIT
    LaunchedEffect(Unit) {
        println("INIT");
        launch {
            while (true) {
                // counter++;
                val test : ByteArray = ByteArray(visualizer.captureSize);
                visualizer.getFft(test);
                fftBytes = test.toList();

                fftM = transformFftMagnitude(fftBytes, prevFFTM, visualizer);
                if (fftM.isNotEmpty())
                    prevFFTM = fftM;
                fftM = fftM.copyOfRange(hzToFftIndex(0, fftBytes.size, visualizer.samplingRate),
                    hzToFftIndex(22050, fftBytes.size, visualizer.samplingRate));
                println("DC: ${fftBytes[0]}");
                //println(fftBytes);
                delay(16);
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // FPS COUNTER
        val deltaTime : Float = 1 / ((System.currentTimeMillis() - prevTime) / 1000f);
        println("Delta Time : $deltaTime")
        prevTime = System.currentTimeMillis();

        val screenCenter: Size = size / 2f;
        val DISTANCE = 3f;
        val LINEHEIGHT = 300f;
        val COUNT = fftM.size - 1;
        var minVal = 0.0f;
        var maxVal = 0.0f;
        for (fftData in fftM) {
            minVal = min(minVal, fftData.toFloat());
            maxVal = max(maxVal, fftData.toFloat());
        }
        val range = maxVal - minVal;
        val scaleFactor = range + 0.00001f;

        for (i in 0..COUNT) {
            val xOffset: Float = DISTANCE * i - DISTANCE * COUNT / 2;
            var barHeight: Float = 0f;
            if (i < fftM.size) {
                barHeight = fftM[i].toFloat() / 200;
            }
            barHeight = LINEHEIGHT * ((fftM[i].toFloat() - minVal) / scaleFactor);
            drawLine(
                Color.Red,
                start = Offset(screenCenter.width + xOffset, screenCenter.height),
                end = Offset(
                    screenCenter.width + xOffset,
                    screenCenter.height - barHeight
                ),
                strokeWidth = DISTANCE - 2,
            )
        }
        var hertz = 0f;
        var addHz = 2f;
        for (i in 0..COUNT) {
            val xOffset: Float = DISTANCE * i - DISTANCE * COUNT / 2;
            var barHeight = 0f;
            barHeight = LINEHEIGHT * ((getVolumeFrequency(hertz.roundToInt()) - minVal) / scaleFactor);
            drawLine(
                Color.Blue,
                start = Offset(screenCenter.width + xOffset, screenCenter.height - 200),
                end = Offset(
                    screenCenter.width + xOffset,
                    screenCenter.height - 200 - barHeight
                ),
                strokeWidth = DISTANCE - 2,
            );
            hertz += addHz;
            addHz += 0.6f;
        }
    }

}
