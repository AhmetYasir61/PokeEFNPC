package com.pokewing.pokeefnpc.voice;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The small amount of audio plumbing between Simple Voice Chat and a speech
 * program.
 *
 * <p>Simple Voice Chat works in <b>48 kHz, mono, signed 16-bit</b> PCM, in 20 ms
 * frames of 960 samples. Speech recognition and speech synthesis programs
 * generally do not — whisper wants 16 kHz, and most synthesisers answer at 22.05
 * kHz. Everything in this class exists to move between those, plus the minimal
 * RIFF header work needed to hand a program a file it will accept and to read
 * back what it returns.
 */
public final class AudioTools {

    /** The rate Simple Voice Chat speaks in. */
    public static final int VOICE_RATE = 48_000;
    /** The rate speech recognition models are trained at. */
    public static final int SPEECH_RATE = 16_000;
    /** Samples in one 20 ms frame at {@link #VOICE_RATE}. */
    public static final int FRAME_SIZE = 960;

    private AudioTools() {
    }

    // ------------------------------------------------------------- resampling

    /**
     * Linear resample. Not the highest quality possible, but speech recognition
     * is unbothered by it and it costs nothing — which matters, because this runs
     * on every utterance on a server thread pool that is deliberately starved of
     * priority.
     */
    public static short[] resample(short[] input, int fromRate, int toRate) {
        if (fromRate == toRate || input.length == 0) {
            return input;
        }
        int outputLength = (int) ((long) input.length * toRate / fromRate);
        short[] output = new short[Math.max(1, outputLength)];
        double step = (double) input.length / output.length;
        for (int i = 0; i < output.length; i++) {
            double position = i * step;
            int index = (int) position;
            double fraction = position - index;
            short a = input[Math.min(index, input.length - 1)];
            short b = input[Math.min(index + 1, input.length - 1)];
            output[i] = (short) Math.round(a + (b - a) * fraction);
        }
        return output;
    }

    /** Averages interleaved stereo down to mono, or passes mono through. */
    public static short[] toMono(short[] input, int channels) {
        if (channels <= 1) {
            return input;
        }
        short[] output = new short[input.length / channels];
        for (int i = 0; i < output.length; i++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += input[i * channels + channel];
            }
            output[i] = (short) (sum / channels);
        }
        return output;
    }

    // ------------------------------------------------------------- WAV

    /** Wraps mono 16-bit samples in a RIFF header. */
    public static byte[] toWav(short[] samples, int sampleRate) {
        int dataBytes = samples.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataBytes);
        buffer.put("WAVE".getBytes());

        buffer.put("fmt ".getBytes());
        buffer.putInt(16);            // PCM header length
        buffer.putShort((short) 1);   // format: PCM
        buffer.putShort((short) 1);   // channels: mono
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2); // byte rate
        buffer.putShort((short) 2);   // block align
        buffer.putShort((short) 16);  // bits per sample

        buffer.put("data".getBytes());
        buffer.putInt(dataBytes);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    /** A decoded WAV: mono 16-bit samples plus the rate they were recorded at. */
    public record Wav(short[] samples, int sampleRate) {
    }

    /**
     * Reads a WAV back from a speech synthesiser.
     *
     * <p>Walks the chunk list rather than assuming a 44-byte header, because
     * plenty of synthesisers emit a LIST or fact chunk before the data and a
     * fixed offset would read the metadata as audio. Anything that is not 16-bit
     * PCM is rejected outright rather than being played as noise.
     */
    public static Wav readWav(byte[] wav) {
        if (wav.length < 44) {
            throw new IllegalArgumentException("not a WAV: too short (" + wav.length + " bytes)");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (!"RIFF".equals(readTag(buffer)) ) {
            throw new IllegalArgumentException("not a WAV: missing RIFF");
        }
        buffer.getInt();
        if (!"WAVE".equals(readTag(buffer))) {
            throw new IllegalArgumentException("not a WAV: missing WAVE");
        }

        int sampleRate = SPEECH_RATE;
        int channels = 1;
        int bits = 16;

        while (buffer.remaining() >= 8) {
            String tag = readTag(buffer);
            int size = buffer.getInt();
            if (size < 0 || size > buffer.remaining()) {
                size = buffer.remaining();
            }
            if ("fmt ".equals(tag)) {
                int start = buffer.position();
                buffer.getShort();                 // encoding
                channels = buffer.getShort();
                sampleRate = buffer.getInt();
                buffer.getInt();                   // byte rate
                buffer.getShort();                 // block align
                bits = buffer.getShort();
                buffer.position(start + size);
            } else if ("data".equals(tag)) {
                if (bits != 16) {
                    throw new IllegalArgumentException("unsupported WAV: " + bits + "-bit");
                }
                short[] samples = new short[size / 2];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = buffer.getShort();
                }
                return new Wav(toMono(samples, channels), sampleRate);
            } else {
                buffer.position(buffer.position() + size);
                // Chunks are word-aligned; an odd size carries a pad byte.
                if (size % 2 == 1 && buffer.remaining() > 0) {
                    buffer.position(buffer.position() + 1);
                }
            }
        }
        throw new IllegalArgumentException("not a WAV: no data chunk");
    }

    private static String readTag(ByteBuffer buffer) {
        byte[] tag = new byte[4];
        buffer.get(tag);
        return new String(tag);
    }

    // ------------------------------------------------------------- analysis

    /**
     * Root-mean-square loudness of a frame, normalised to [0,1]. Used to tell
     * speech from silence so an utterance can be ended without the speaker having
     * to press anything.
     */
    public static float loudness(short[] samples) {
        if (samples.length == 0) {
            return 0.0F;
        }
        double sum = 0.0D;
        for (short sample : samples) {
            double normalised = sample / 32768.0D;
            sum += normalised * normalised;
        }
        return (float) Math.sqrt(sum / samples.length);
    }

    /** Concatenates buffered frames into one utterance. */
    public static short[] concat(Iterable<short[]> frames, int totalSamples) {
        short[] output = new short[totalSamples];
        int offset = 0;
        for (short[] frame : frames) {
            int length = Math.min(frame.length, output.length - offset);
            if (length <= 0) {
                break;
            }
            System.arraycopy(frame, 0, output, offset, length);
            offset += length;
        }
        return output;
    }

    /**
     * Splits audio into the 960-sample frames Simple Voice Chat's encoder wants,
     * zero-padding the last one so a short reply is not truncated mid-word.
     */
    public static byte[] framesToPayload(short[] samples) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(samples.length * 2);
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.clear();
            buffer.putShort(sample);
            out.write(buffer.array(), 0, 2);
        }
        return out.toByteArray();
    }

    /** Pads to a whole number of {@link #FRAME_SIZE} frames. */
    public static short[] padToFrames(short[] samples) {
        int remainder = samples.length % FRAME_SIZE;
        if (remainder == 0) {
            return samples;
        }
        short[] padded = new short[samples.length + (FRAME_SIZE - remainder)];
        System.arraycopy(samples, 0, padded, 0, samples.length);
        return padded;
    }

    /**
     * Scales a reply's volume. Synthesised speech is usually much quieter than a
     * player's microphone, and a villager you cannot hear over your own footsteps
     * is worse than no villager.
     */
    public static short[] amplify(short[] samples, float gain) {
        if (gain == 1.0F) {
            return samples;
        }
        for (int i = 0; i < samples.length; i++) {
            int scaled = Math.round(samples[i] * gain);
            // Clamped rather than allowed to wrap, which would turn a loud
            // syllable into a burst of static.
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
        }
        return samples;
    }
}
