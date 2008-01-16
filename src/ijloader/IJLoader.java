package ijloader;

import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import ij.IJ;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteOrder;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;

public class IJLoader {

    private static class ImageInputStreamWrapper extends ImageInputStreamImpl {
        public ImageInputStreamWrapper(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return stream.read(b, off, len);
        }

        private final InputStream stream;
    }

    public static void main(String args[]) {
        ImageInputStream iStream = new ImageInputStreamWrapper(System.in);

        iStream.setByteOrder(ByteOrder.BIG_ENDIAN);

        savedOut = System.out;

        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
            }
        }));

        try {
            while (true) {
                int width = iStream.readInt();
                int height = iStream.readInt();

                int pixBuffer[] = new int[width * height];
                iStream.readFully(pixBuffer, 0, pixBuffer.length);

                ImagePlus imp = new ImagePlus("diamond", new ColorProcessor(
                        width, height, pixBuffer));

                WindowManager.setTempCurrentImage(imp);

                int macroLen = iStream.readInt();
                byte macroBuffer[] = new byte[macroLen];
                iStream.readFully(macroBuffer);

                String macroText = new String(macroBuffer, 0,
                        macroBuffer.length, "ISO8859_1");
                IJ.runMacro(macroText);

                ResultsTable rTable = ResultsTable.getResultsTable();
                if (rTable != null) {
                    rTable.reset();
                }

                WindowManager.closeAllWindows();

            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Received IO Exception from reading stdin!", e);
        }
    }

    public static PrintStream getOutputStream() {
        return savedOut;
    }

    private static PrintStream savedOut;

}
