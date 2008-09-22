package ijloader;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.Opener;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.text.TextWindow;

import java.awt.Frame;
import java.io.*;

public class IJLoader {

    final private static IJLoaderOutputStream specialOut = new IJLoaderOutputStream(
            System.out);

    private static class IJLoaderOutputStream extends PrintStream {

        private boolean outputEmitted;

        public IJLoaderOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int i) {
            if (i != 13) {
                super.write(i);
                outputEmitted = true;
                super.flush();
            }
        }

        public boolean getOutputEmitted() {
            return outputEmitted;
        }

        public void resetOutputEmitted() {
            outputEmitted = false;
        }
    }

    public static void main(String args[]) {
        // err
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
                System.err.write(i);
            }
        }));

        try {
            while (true) {
                innerLoop(System.in, specialOut);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void debugPrint(String msg) {
        System.err.println("[IJLoader] " + msg);
    }

    private static void innerLoop(InputStream in,
            IJLoaderOutputStream specialOut) throws IOException {
        debugPrint("Reading image...");

        specialOut.println("BEGIN");

        DataInputStream iStream = new DataInputStream(in);

        int imgLen = iStream.readInt();

        byte pixBuffer[] = new byte[imgLen];
        iStream.readFully(pixBuffer, 0, pixBuffer.length);

        debugPrint("Image read: " + pixBuffer.length + " bytes");

        // save as temp
        File tmp = File.createTempFile("ijloader", ".img");
        tmp.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            fos.write(pixBuffer);
        } finally {
            fos.close();
        }

        new Opener().open(tmp.getPath());

        // tmp.delete();

        debugPrint("Reading macro...");

        int macroLen = iStream.readInt();
        byte macroBuffer[] = new byte[macroLen];
        iStream.readFully(macroBuffer);

        String macroText = new String(macroBuffer, 0, macroBuffer.length,
                "ISO8859_1");

        debugPrint("Macro read.");
        debugPrint(macroText);
        debugPrint("Running macro...");

        specialOut.resetOutputEmitted();
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

        if (!specialOut.getOutputEmitted()) {
            debugPrint("No output received from filter");
            specialOut.println("RESULT");
            specialOut.println(3);
            specialOut.println("0.0");
        }

        debugPrint("Macro executed");

        ResultsTable rTable = ResultsTable.getResultsTable();
        if (rTable != null) {
            rTable.reset();
        }

        debugPrint("going to close all windows");
        // WindowManager.setTempCurrentImage(null);
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

    public static void writeDiamondAttribute(String name, String val) {
        debugPrint("writeDiamondAttribute: " + name + " -> " + val);
        specialOut.println("ATTR");

        specialOut.println("K");
        specialOut.println(name.length());
        specialOut.println(name);

        specialOut.println("V");
        specialOut.println(val.length());
        specialOut.println(val);
    }

    public static void writeResult(String val) {
        debugPrint("result: " + val);

        specialOut.println("RESULT");
        specialOut.println(val.length());
        specialOut.println(val);
    }
}
