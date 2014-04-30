package com.tonyhuang.example;

import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.util.Log;

/**
 * @author Tony Huang (starlightslo@gmail.com)
 * @version Creation time: 2014/4/30 下午4:03:17
 */
public class Decoder {
	private static final String TAG = "Decoder";
	private static final boolean DEBUG = true;
	private static final long TIMEOUT = 10000;
	
	private MediaCodec mDecoder;
	private MediaFormat mMediaFormat;
	
	private BufferInfo mBufferInfo;
	private ByteBuffer[] mInputBuffers;
	private ByteBuffer[] mOutputBuffers;
	
	private MediaExtractor extractor;
	private AudioTrack mAudioTrack;
	
	private String mSource;
	private boolean isDecoding = false;
	
	public void play(String source) {
		this.mSource = source;
		setFormat();
		initDecoder();
		startDecoder();
		playback();
	}

	public void stop() {
		isDecoding = false;
	}
	
	private void playback() {
		isDecoding = true;
		
		new Thread(new Runnable() {
			long startMs;
			
			@Override
			public void run() {
				mBufferInfo = new BufferInfo();
				startMs = System.currentTimeMillis();
				boolean isEOS = false;
				while (isDecoding) {
					int inIndex = mDecoder.dequeueInputBuffer(TIMEOUT);
					if (inIndex >= 0) {
						ByteBuffer buffer = mInputBuffers[inIndex];
						if (!isEOS) {
							int size = extractor.readSampleData(buffer, 0);
							if (size < 0) {
								if (DEBUG) Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
								mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
								isEOS = true;
							} else {
								if (DEBUG) Log.d(TAG, "sampleSize: " + size);
								mDecoder.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
								
								if (!isEOS)
									nextSample();
							}
						}
					}

					processDequeueBuffer();
					
					// All decoded frames have been rendered, we can stop playing now
					if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						if (DEBUG) Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
						break;
					}
				}
				
				mDecoder.stop();
				mDecoder.release();
				extractor.release();
				mAudioTrack.release();
			}

			private void nextSample() {
				while (extractor.getSampleTime() / 1000 > System.currentTimeMillis() - startMs) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				extractor.advance();
			}

			private void processDequeueBuffer() {
				int outIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT);
				switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					if (DEBUG) Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
					mOutputBuffers = mDecoder.getOutputBuffers();
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					if (DEBUG) Log.d(TAG, "New format " + mDecoder.getOutputFormat());
					mAudioTrack.setPlaybackRate(mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					if (DEBUG) Log.d(TAG, "dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer tmpBuffer = mOutputBuffers[outIndex];
					final byte[] chunk = new byte[mBufferInfo.size];
					tmpBuffer.get(chunk); // Read the buffer all at once
					tmpBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN
					
					if (chunk.length > 0) {
						mAudioTrack.write(chunk, 0, chunk.length);
					}
					mDecoder.releaseOutputBuffer(outIndex, false /* render */);
					break;
				}
			}
		}).start();
	}

	private void startDecoder() {
		mDecoder.start();
		mInputBuffers = mDecoder.getInputBuffers();
		mOutputBuffers = mDecoder.getOutputBuffers();
	}

	private void initDecoder() {
		String mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
		mDecoder = MediaCodec.createDecoderByType(mime);
		mDecoder.configure(mMediaFormat, null, null, 0);
		mAudioTrack.play();
	}

	private void setFormat() {
		extractor = new MediaExtractor();
		extractor.setDataSource(mSource);
		for (int i = 0; i < extractor.getTrackCount(); i++) {
			mMediaFormat = extractor.getTrackFormat(i);
			String mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
			if (mime.startsWith("audio/")) {
				extractor.selectTrack(i);
				
				mAudioTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC,
					mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
					mMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
					AudioFormat.ENCODING_PCM_16BIT,
					mMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE),
					AudioTrack.MODE_STREAM
				);
				break;
			}
		}
	}
}
