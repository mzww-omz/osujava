package dev.osujava.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.audio.OggInputStream;
import com.badlogic.gdx.backends.lwjgl3.audio.Wav;
import com.badlogic.gdx.files.FileHandle;
import javazoom.jl.decoder.*;
import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Locale;

/** Uses the desktop's existing codecs; no device, network, external process or whole-song PCM. */
abstract class MenuPcmDecoder implements AutoCloseable {
    int channels, sampleRate;
    abstract int read(byte[] buffer) throws Exception;
    @Override public abstract void close() throws Exception;
    boolean bigEndian() { return false; }
    static MenuPcmDecoder open(Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        MenuPcmDecoder result;
        if (name.endsWith(".mp3")) result = new Mp3(path);
        else if (name.endsWith(".ogg")) result = new Ogg(path);
        else if (name.endsWith(".wav")) result = new Wave(path);
        else throw new IOException("Unsupported menu analysis format");
        if (result.channels < 1 || result.channels > 8 || result.sampleRate < 8000 || result.sampleRate > 192000) {
            result.close(); throw new IOException("Invalid PCM format");
        }
        return result;
    }
    private static final class Mp3 extends MenuPcmDecoder {
        private final Bitstream stream;
        private final MP3Decoder decoder = new MP3Decoder();
        private final OutputBuffer output;
        private Header header;
        Mp3(Path path) throws Exception {
            stream = new Bitstream(Files.newInputStream(path));
            try {
                header = stream.readFrame();
                if (header == null) throw new IOException("Empty MP3");
                channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2; sampleRate = header.getSampleRate();
                output = new OutputBuffer(channels, false); decoder.setOutputBuffer(output);
            } catch (Exception e) { stream.close(); throw e; }
        }
        @Override int read(byte[] buffer) throws Exception {
            if (header == null) header = stream.readFrame();
            if (header == null) return -1;
            decoder.decodeFrame(header, stream); stream.closeFrame(); header = null;
            int size = output.reset(); System.arraycopy(output.getBuffer(), 0, buffer, 0, size); return size;
        }
        @Override public void close() throws Exception { stream.close(); }
    }
    private static final class Ogg extends MenuPcmDecoder {
        private final OggInputStream stream;
        Ogg(Path path) throws Exception {
            InputStream file = Files.newInputStream(path);
            try { stream = new OggInputStream(file); channels = stream.getChannels(); sampleRate = stream.getSampleRate(); }
            catch (RuntimeException e) { file.close(); throw e; }
        }
        @Override int read(byte[] buffer) { return stream.read(buffer); }
        @Override boolean bigEndian() { return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN; }
        @Override public void close() { stream.close(); }
    }
    private static final class Wave extends MenuPcmDecoder {
        private final Wav.WavInputStream stream;
        Wave(Path path) throws Exception {
            stream = new Wav.WavInputStream(new FileHandle(path.toFile()));
            if (stream.bitDepth != 16 || stream.type != 1) { stream.close(); throw new IOException("Analysis requires PCM16 WAV"); }
            channels = stream.channels; sampleRate = stream.sampleRate;
        }
        @Override int read(byte[] buffer) throws IOException { return stream.read(buffer); }
        @Override public void close() throws IOException { stream.close(); }
    }
}
