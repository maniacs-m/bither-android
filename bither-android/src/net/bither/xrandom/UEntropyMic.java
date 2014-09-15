/*
 *
 *  * Copyright 2014 http://Bither.net
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package net.bither.xrandom;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.HandlerThread;

import net.bither.bitherj.utils.LogUtil;

import java.util.Arrays;

/**
 * Created by songchenwen on 14-9-11.
 */
public class UEntropyMic implements IUEntropySource {

    private static final int ChannelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    private static final int AudioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SamplePerSec = 8000;
    private static final int MaxBufferSize = 6400;

    private int bufferSizeBytes;

    private HandlerThread micThread;
    private Handler micHandler;
    private AudioRecord audioRecord;

    private UEntropyCollector collector;

    public UEntropyMic(UEntropyCollector collector) {
        this.collector = collector;
    }

    private final Runnable openRunnable = new Runnable() {
        @Override
        public void run() {
            int minBufferSize = AudioRecord.getMinBufferSize(SamplePerSec, ChannelConfiguration,
                    AudioEncoding);
            if (minBufferSize > MaxBufferSize) {
                bufferSizeBytes = minBufferSize;
            } else {
                bufferSizeBytes = (MaxBufferSize / minBufferSize) * minBufferSize;
            }
            audioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC,
                    SamplePerSec, ChannelConfiguration, AudioEncoding, bufferSizeBytes);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                micHandler.post(readRunnable);
            } else {
                onPause();
                collector.onError(new IllegalStateException("startRecording() called on an " +
                        "uninitialized AudioRecord."), UEntropyMic.this);
            }
        }
    };

    private final Runnable readRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord
                    .RECORDSTATE_RECORDING) {
                byte[] data = new byte[bufferSizeBytes];
                int outLength = audioRecord.read(data, 0, bufferSizeBytes);
                collector.onNewData(Arrays.copyOf(data, outLength),
                        UEntropyCollector.UEntropySource.Mic);
            }
            micHandler.post(readRunnable);
        }
    };

    private final Runnable closeRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord
                    .RECORDSTATE_RECORDING) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                LogUtil.i(UEntropyMic.class.getSimpleName(), "Mic released");
            }
            micHandler.removeCallbacksAndMessages(null);
            micThread.quit();
        }
    };

    @Override
    public void onResume() {
        if (micThread != null && micThread.isAlive()) {
            return;
        }
        micThread = new HandlerThread("UEntropyMicThread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        micThread.start();
        micHandler = new Handler(micThread.getLooper());
        micHandler.post(openRunnable);
    }

    @Override
    public void onPause() {
        if (micThread != null && micThread.isAlive()) {
            micHandler.removeCallbacksAndMessages(null);
            micHandler.post(closeRunnable);
        }
    }

    @Override
    public UEntropyCollector.UEntropySource type() {
        return UEntropyCollector.UEntropySource.Mic;
    }
}
