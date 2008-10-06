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

        private volatile boolean outputEmitted;

        private volatile boolean resultEmitted;

        public IJLoaderOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int i) {
            outputEmitted = true;
            if (i != 13) {
                super.write(i);
                super.flush();
            }
        }

        public void writeResult(String result) {
            if (resultEmitted) {
                // throw new IllegalStateException("Result already written");
                System.err.println("warning: Result already written");
            }

            resultEmitted = true;

            println("RESULT");
            println(result.length());
            println(result);
        }

        public boolean getOutputEmitted() {
            return outputEmitted;
        }

        public void resetEmitted() {
            outputEmitted = false;
            resultEmitted = false;
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

        tmp.delete();

        debugPrint("Reading macro name...");

        int macroLen = iStream.readInt();
        byte macroBuffer[] = new byte[macroLen];
        iStream.readFully(macroBuffer);

        String macroName = new String(macroBuffer, 0, macroBuffer.length,
                "ISO8859_1").replace('_', ' ');

        debugPrint("Macro name read.");
        debugPrint(macroName);

        String evalString = "run(\"" + macroName + "\");";
        debugPrint("Running macro...");
        debugPrint(evalString);

        specialOut.resetEmitted();
        String macroResult = IJ.runMacro(evalString);
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
            specialOut.writeResult("0.0");
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
        specialOut.writeResult(val);
    }
}
