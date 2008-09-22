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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;

public class IJLoader {

    final private static IJLoaderOutputStream specialOut = new IJLoaderOutputStream(
            System.out);

    public static class IJLoaderOutputStream extends PrintStream {

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

    public static class IJClassLoader extends ClassLoader {
        Map<String, Class<?>> classes = Collections
                .synchronizedMap(new HashMap<String, Class<?>>());

        @Override
        public Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            System.err.println(" * want to load " + name);
            // String whitelist[] = { "ij.", "ijloader.IJLoader$InnerLoop" };
            String whitelist[] = {};
            boolean loadThis = false;
            for (String s : whitelist) {
                if (name.startsWith(s)) {
                    loadThis = true;
                    break;
                }
            }
            if (!loadThis) {
                return getParent().loadClass(name);
            }

            Class<?> c = classes.get(name);
            if (c == null) {
                InputStream in = new BufferedInputStream(ClassLoader
                        .getSystemResourceAsStream(name.replace('.', '/')
                                + ".class"));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int i;
                try {
                    while ((i = in.read()) != -1) {
                        out.write(i);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte b[] = out.toByteArray();
                c = defineClass(name, b, 0, b.length);
                System.err.println("*** loaded " + name + " with classloader "
                        + c.getClassLoader());
                classes.put(name, c);
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    public static class ImageInputStreamWrapper extends ImageInputStreamImpl {
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
        // in
        ImageInputStream iStream = new ImageInputStreamWrapper(System.in);
        iStream.setByteOrder(ByteOrder.BIG_ENDIAN);

        // err
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) {
                System.err.write(i);
            }
        }));

        try {
            while (true) {
                ClassLoader cl = new IJClassLoader();
                Class<?> clazz = Class.forName("ijloader.IJLoader$InnerLoop",
                        true, cl);
                Method m = clazz.getMethod("run", ImageInputStream.class,
                        IJLoaderOutputStream.class);
                m.setAccessible(true);
                m.invoke(null, iStream, specialOut);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private static class InnerLoop {
        private static void debugPrint(String msg) {
            System.err.println("[IJLoader] " + msg);
        }

        public static void run(ImageInputStream iStream,
                IJLoaderOutputStream specialOut) throws IOException {
            debugPrint("Reading image...");

            specialOut.println("BEGIN");

            int imgLen = iStream.readInt();

            byte pixBuffer[] = new byte[imgLen];
            iStream.readFully(pixBuffer, 0, pixBuffer.length);

            debugPrint("Image read: " + pixBuffer.length + " bytes");

            // save as temp
            File tmp = File.createTempFile("ijloader", ".img");
            // tmp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(pixBuffer);
            fos.close();

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

    }

    public static void writeDiamondAttribute(String name, String val) {
        // debugPrint("writeDiamondAttribute: " + name + " -> " + val);
        specialOut.println("ATTR");

        specialOut.println("K");
        specialOut.println(name.length());
        specialOut.println(name);

        specialOut.println("V");
        specialOut.println(val.length());
        specialOut.println(val);
    }

    public static void writeResult(String val) {
        // debugPrint("result: " + val);

        specialOut.println("RESULT");
        specialOut.println(val.length());
        specialOut.println(val);
    }
}
