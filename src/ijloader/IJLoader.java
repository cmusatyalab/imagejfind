package ijloader;

import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import ij.IJ;
import ij.macro.Interpreter;

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

        newOut = new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
                if (i != 13) {
                    savedOut.write(i);
                    System.err.write(i);
                    outputEmitted = true;
                    savedOut.flush();
                    System.err.flush();
                    System.err.println("write called, i = " + i);
                }
                System.err.println("write called...");
            }
        });

        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
            }
        }));

        try {
            int width, height, macroLen, pixBuffer[];
            byte macroBuffer[];
            String macroText;
            while (true) {
                System.err.println("Reading image...");

                width = iStream.readInt();
                height = iStream.readInt();

                pixBuffer = new int[width * height];
                iStream.readFully(pixBuffer, 0, pixBuffer.length);

                System.err.println("Image read.");

                ImagePlus imp = new ImagePlus("diamond", new ColorProcessor(
                        width, height, pixBuffer));

                WindowManager.setTempCurrentImage(imp);

                System.err.println("Reading macro...");

                macroLen = iStream.readInt();
                macroBuffer = new byte[macroLen];
                iStream.readFully(macroBuffer);

                macroText = new String(macroBuffer, 0, macroBuffer.length,
                        "ISO8859_1");

                System.err.println("Macro read.");
                System.err.println(macroText);
                System.err.println("Running macro...");

                outputEmitted = false;
                IJ.runMacro(macroText);

                if (!outputEmitted) {
                    System.err.println("No output received from filter");
                    newOut.println("RESULT");
                    newOut.println(3);
                    newOut.println("0.0");
                }

                System.err.println("Macro executed");

                ResultsTable rTable = ResultsTable.getResultsTable();
                if (rTable != null) {
                    rTable.reset();
                }

                WindowManager.setTempCurrentImage(null);
                WindowManager.closeAllWindows();

                ImagePlus lastImage;
                do {
                    lastImage = Interpreter.getLastBatchModeImage();
                    if (lastImage != null) {
                        Interpreter.removeBatchModeImage(lastImage);
                    }
                } while (lastImage != null);

                System.err.print("Window count: ");
                System.err.println(WindowManager.getWindowCount());
                System.err.print("Image count: ");
                System.err.println(WindowManager.getImageCount());

            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Received IO Exception from reading stdin!", e);
        }
    }

    public static void writeDiamondAttribute(String name, String val) {
        System.err.println("writeDiamondAttribute: " + name + " -> " + val);
        
        PrintStream out = getOutputStream();
        out.println("ATTR_NAME");
        out.println(name.length());
        out.println(name);

        out.println("ATTR_VAL");
        out.println(val.length());
        out.println(val);
    }
    
    public static PrintStream getOutputStream() {
        return savedOut;
    }

    private static PrintStream savedOut = System.out;

    private static PrintStream newOut;

    private static boolean outputEmitted;

}
