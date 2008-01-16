package ijloader;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import ij.text.TextWindow;

import java.awt.Frame;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteOrder;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;

public class IJLoader {

    private static void debugPrint(String msg) {
        System.err.println("[IJLoader] " + msg);
    }
    
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

//        IJ.debugMode = true;
        
        newOut = new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
                if (i != 13) {
                    savedOut.write(i);
                    outputEmitted = true;
                    savedOut.flush();
                }
            }
        });

        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
                System.err.write(i);
            }
        }));

        try {
            int width, height, macroLen, pixBuffer[];
            byte macroBuffer[];
            String macroText;
            while (true) {
                debugPrint("Reading image...");

                newOut.println("BEGIN");

                width = iStream.readInt();
                height = iStream.readInt();

                pixBuffer = new int[width * height];
                iStream.readFully(pixBuffer, 0, pixBuffer.length);

                debugPrint("Image read.");

                ImagePlus imp = new ImagePlus("diamond", new ColorProcessor(
                        width, height, pixBuffer));

                ImageWindow imgWin = new ImageWindow(imp);
                imgWin.setVisible(true);
//                WindowManager.setTempCurrentImage(imp);

                debugPrint("Reading macro...");

                macroLen = iStream.readInt();
                macroBuffer = new byte[macroLen];
                iStream.readFully(macroBuffer);

                macroText = new String(macroBuffer, 0, macroBuffer.length,
                        "ISO8859_1");

                debugPrint("Macro read.");
//                debugPrint(macroText);
                debugPrint("Running macro...");

                outputEmitted = false;
                String macroResult = IJ.runMacro(macroText);
                debugPrint("macroResult: " + macroResult);
                
                debugPrint(" in batch mode: " + Interpreter.isBatchMode());

                debugPrint("** LOG ");
                Frame f[] = WindowManager.getNonImageWindows();
                for (Frame frame : f) {
                    if (frame instanceof TextWindow) {
                        TextWindow tw = (TextWindow) frame;
                        debugPrint(" * " + tw.getTitle());
                        debugPrint(tw.getTextPanel().getText());
                    }
                }
                debugPrint("** END LOG ");

                if (!outputEmitted) {
                    debugPrint("No output received from filter");
                    newOut.println("RESULT");
                    newOut.println(3);
                    newOut.println("0.0");
                }

                debugPrint("Macro executed");

                ResultsTable rTable = ResultsTable.getResultsTable();
                if (rTable != null) {
                    rTable.reset();
                }

                debugPrint("going to close all windows");
                imgWin.close();
//                WindowManager.setTempCurrentImage(null);
                WindowManager.closeAllWindows();
                debugPrint(" done");

                ImagePlus lastImage;
                do {
                    lastImage = Interpreter.getLastBatchModeImage();
                    if (lastImage != null) {
                        Interpreter.removeBatchModeImage(lastImage);
                    }
                } while (lastImage != null);

                debugPrint("Window count: ");
                debugPrint(Integer.toString(WindowManager.getWindowCount()));
                debugPrint("Image count: ");
                debugPrint(Integer.toString(WindowManager.getImageCount()));
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Received IO Exception from reading stdin!", e);
        }
    }

    public static void writeDiamondAttribute(String name, String val) {
        debugPrint("writeDiamondAttribute: " + name + " -> " + val);
        newOut.println("ATTR");
        
        newOut.println("K");
        newOut.println(name.length());
        newOut.println(name);

        newOut.println("V");
        newOut.println(val.length());
        newOut.println(val);
    }
    
    public static void writeResult(String val) {
        debugPrint("result: " + val);
        
        newOut.println("RESULT");
        newOut.println(val.length());
        newOut.println(val);
    }
    
    private static PrintStream savedOut = System.out;

    private static PrintStream newOut;

    private static boolean outputEmitted;
}
