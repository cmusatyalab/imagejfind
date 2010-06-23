/*
 * ImageJFind: A Diamond application for interoperating with ImageJ
 *
 * Copyright (c) 2006-2008 Carnegie Mellon University. All rights reserved.
 * Additional copyrights may be listed below.
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution in the file named LICENSE.
 *
 * Technical and financial contributors are listed in the file named
 * CREDITS.
 */

package ijloader;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.text.TextWindow;

import java.awt.Frame;
import java.io.*;

public class IJLoader {

    final private static IJLoaderOutputStream specialOut = new IJLoaderOutputStream(
            System.out);

    private static class IJLoaderOutputStream extends PrintStream {

        private volatile boolean resultEmitted;

        public IJLoaderOutputStream(OutputStream out) {
            super(out);
        }

        public void writeResult(String result) {
            debugPrint("IJLoaderOutputStream writeResult: " + result);
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
            return resultEmitted;
        }

        public void resetEmitted() {
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

        IJ.open(tmp.getPath());

        tmp.delete();

        debugPrint("Reading macro name...");

        int macroLen = iStream.readInt();
        byte macroBuffer[] = new byte[macroLen];
        iStream.readFully(macroBuffer);

        String macroName = new String(macroBuffer, "UTF-8");

        debugPrint("Macro name read.");
        debugPrint(macroName);

        debugPrint("Running macro...");

        specialOut.resetEmitted();

        Interpreter.batchMode = true;
        String macroResult = IJ.runMacroFile(macroName);
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
