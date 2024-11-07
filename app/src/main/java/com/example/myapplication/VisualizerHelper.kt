package com.example.myapplication

import android.media.audiofx.Visualizer
import androidx.compose.ui.util.lerp
import java.lang.Math.pow
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class VisualizerHelper(captureSize : Int = Visualizer.getCaptureSizeRange()[1], audioSessionId: Int = 0) {
    companion object{
        public fun HZToFftIndex(Hz: Int, size : Int, samplingRate: Int): Int {
            return (Hz * size / (44100 * 2)).coerceIn(0, 255);
        }

        public fun dB(x: Double) : Double {
            if (x == 0.0)
                return 0.0;
            else
                return 10.0 * log10(x);
        }


    }

    private val visualizer : Visualizer = Visualizer(audioSessionId);
    private val fftM : DoubleArray;
    private val fftBytes : ByteArray;
    private val frequencyMap : MutableList<Pair<Int, Double>> = mutableListOf();

    init {
        visualizer.setCaptureSize(captureSize);
        visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
        visualizer.setEnabled(true);

        fftBytes = ByteArray(visualizer.captureSize);
        fftM = DoubleArray(visualizer.captureSize / 2 - 1);
    }

    public fun GetFFT() : ByteArray {
        visualizer.getFft(fftBytes);
        return fftBytes;
    }

    public fun GetTransformedFFT(start : Int = 0, end : Int = 0) : DoubleArray{
        GetFFT();
        transformFftMagnitude();
        if (start <= end)
            return fftM;
        return fftM.copyOfRange(HZToFftIndex(start, visualizer.captureSize, visualizer.samplingRate),
            HZToFftIndex(end, visualizer.captureSize, visualizer.samplingRate)
        );
    }

    public fun GetFrequencyMap() : List<Pair<Int, Double>> {
        return frequencyMap;
    }

    private fun transformFftMagnitude() {
        frequencyMap.clear();
        val SMOOTHING = 0.8;
        val samplingRate = visualizer.samplingRate;
        val captureSize = visualizer.captureSize;
        for (k in 0 until fftBytes.size / 2 - 1) {
            val prevFFTM = fftM[k];
            val i = (k + 1) * 2;
            val real = fftBytes[i].toDouble();
            val img = fftBytes[i + 1].toDouble();
            fftM[k] = dB((hypot(real, img)));
            fftM[k] = fftM[k] * fftM[k] / 100;
            fftM[k] = (SMOOTHING) * prevFFTM + ((1 - SMOOTHING) * fftM[k]);
        }

        val averageNum = 2;
        for (i in 0 until fftBytes.size / 2 - 1) {
            var average = 0.0;
            var averageCount = 0;
            for (j in max(0, i - averageNum) until min(fftM.size, i + 1 + averageNum)) {
                average += fftM[i];
                averageCount++;
            }
            average /= max(averageCount, 1);
            fftM[i] = average;

            val fre = i * (samplingRate / 1000) / captureSize;
            frequencyMap.add(Pair<Int, Double>(fre, fftM[i]));
        }
    }

    public fun GetVolumeFrequency(Hz: Int) : Float {
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
            Easing((Hz - beginHz.first).toFloat() / (endHz.first - beginHz.first).toFloat(),  EasingType.OutCubic)
        )
    }

}
