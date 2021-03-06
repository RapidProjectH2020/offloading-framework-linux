package eu.project.rapid.gvirtus4j;

import java.io.*;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class Util {
    private static final String TAG = "RapidUtils";
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private final static Map<Character, Integer> hexToDec = new HashMap<Character, Integer>();

    static {
        hexToDec.put('A', 10);
        hexToDec.put('B', 11);
        hexToDec.put('C', 12);
        hexToDec.put('D', 13);
        hexToDec.put('E', 14);
        hexToDec.put('F', 15);
    }

    public static String bytesToHex(byte[] bytes) {

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).trim();
    }

    public static byte[] hexToBytes(String hexString) {
        byte[] bytes = new byte[hexString.length() / 2];

        for (int i = 0; i < hexString.length(); i += 2) {
            char c1 = hexString.charAt(i);
            char c2 = hexString.charAt(i + 1);

            int n1 = Character.getNumericValue(c1);
            if (n1 < 0) {
                n1 = hexToDec.get(c1);
            }

            int n2 = Character.getNumericValue(c2);
            if (n2 < 0) {
                n2 = hexToDec.get(c2);
            }

            bytes[i / 2] = (byte) (n1 * 16 + n2);
        }

        return bytes;
    }

    public static byte[] longToByteArray(long value) {
        return new byte[]{
                (byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24),
                (byte) (value >> 32), (byte) (value >> 40), (byte) (value >> 48), (byte) (value >> 56)};
    }

    public static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    public static class Dim3 {
        public int x;
        public int y;
        public int z;

        public Dim3(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Sizeof {
        public static final int BYTE = Byte.SIZE / 8;
        public static final int CHAR = Character.SIZE / 8;
        public static final int SHORT = Short.SIZE / 8;
        public static final int INT = Integer.SIZE / 8;
        public static final int FLOAT = Float.SIZE / 8;
        public static final int LONG = Long.SIZE / 8;
        public static final int DOUBLE = Double.SIZE / 8;
    }

    public static class ExitCode {

        public static int exit_code = 1;

        public static int getExit_code() {
            return exit_code;
        }

        public static void setExit_code(int exitCode) {
            exit_code = exitCode;
        }
    }


    public static byte[] objectToByteArray(Object o) throws IOException {
        byte[] bytes = null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            bytes = bos.toByteArray();
        } finally {
            if (oos != null) {
                oos.close();
            }
            if (bos != null) {
                bos.close();
            }
        }
        return bytes;
    }

    public static Object byteArrayToObject(byte[] bytes)
            throws StreamCorruptedException, IOException, ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bis = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bis);
            obj = ois.readObject();
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (ois != null) {
                ois.close();
            }
        }
        return obj;
    }


    /**
     * @param s The string to be hash-ed.
     * @return The byte array hash digested result.
     */
    public static byte[] sha256HashByteArray(String s) {
        byte[] hash = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            hash = md.digest(s.getBytes());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Algorithm not found while hashing string: " + e);
            // e.printStackTrace();
        }

        return hash;
    }

    public static String sha256HashHex(String s) {
        return bytesToHex(sha256HashByteArray(s));
    }

    /**
     * This utility method will be used to write an object on a file. The object can be a Set, a Map,
     * etc.<br>
     * The method creates a lock file (if it doesn't exist) and tries to get a <b>blocking lock</b> on
     * the lock file. After writing the object the lock is released, so that other processes that want
     * to read the file can access it by getting the lock.
     *
     * @param filePath The full path of the file where to write the object. If the file exists it will
     *                 first be deleted and then created from scratch.
     * @param obj      The object to write on the file.
     * @throws IOException
     */
    public static void writeObjectToFile(String filePath, Object obj) throws IOException {
        // Get a lock on the lockFile so that concurrent DFEs don't mess with each other by
        // reading/writing the d2dSetFile.
        File lockFile = new File(filePath + ".lock");
        // Create a FileChannel that can read and write that file.
        // This will create the file if it doesn't exit.
        RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
        FileChannel f = file.getChannel();

        // Try to get an exclusive lock on the file.
        // FileLock lock = f.tryLock();
        FileLock lock = f.lock();

        // Now we have the lock, so we can write on the file
        File outFile = new File(filePath);
        if (outFile.exists()) {
            outFile.delete();
        }
        outFile.createNewFile();
        FileOutputStream fout = new FileOutputStream(outFile);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(obj);
        oos.close();

        // Now we release the lock and close the lockFile
        lock.release();
        file.close();
    }

    /**
     * Reads the previously serialized object from the <code>filename</code>.<br>
     * This method will try to get a <b>non blocking lock</b> on a lock file.
     *
     * @param filePath The full path of the file from where to read the object.
     * @return The serialized object previously written using the method
     * <code>writeObjectToFile</code>
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object readObjectFromFile(String filePath)
            throws IOException, ClassNotFoundException {
        Object obj = null;

        // First try to get the lock on a lock file
        File lockFile = new File(filePath + ".lock");
        if (!lockFile.exists()) {
            // It means that no other process has written an object before.
            return null;
        }

        // Create a FileChannel that can read and write that file.
        // This will create the file if it doesn't exit.
        RandomAccessFile file = new RandomAccessFile(lockFile, "rw");
        FileChannel f = file.getChannel();

        // Try to get an exclusive lock on the file.
        // FileLock lock = f.tryLock();
        FileLock lock = f.lock();

        // Now we have the lock, so we can read from the file
        File inFile = new File(filePath);
        FileInputStream fis = new FileInputStream(inFile);
        ObjectInputStream ois = new ObjectInputStream(fis);
        obj = ois.readObject();
        ois.close();

        // Now we release the lock and close the lockFile
        lock.release();
        file.close();
        return obj;
    }

    /**
     * Get IP address from first non-localhost interface
     *
     * @return address or null
     */
    public static InetAddress getIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    // Sokol: FIXME remove the hard coded "wlan" check
                    if (!addr.isLoopbackAddress() && addr.toString().contains("wlan")) {
                        return addr;
                    }
                    // On emulator
                    if (!addr.isLoopbackAddress() && addr.toString().contains("eth0")) {
                        return addr;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception while getting IP address: " + e);
        }
        return null;
    }

    public static InetAddress getBroadcast(InetAddress myIpAddress) {

        NetworkInterface temp;
        InetAddress iAddr = null;
        try {
            temp = NetworkInterface.getByInetAddress(myIpAddress);
            List<InterfaceAddress> addresses = temp.getInterfaceAddresses();

            for (InterfaceAddress inetAddress : addresses) {
                iAddr = inetAddress.getBroadcast();
            }
            System.out.println("iAddr=" + iAddr);
            return iAddr;

        } catch (SocketException e) {

            e.printStackTrace();
            System.out.println("getBroadcast" + e.getMessage());
        }
        return null;
    }


    /**
     * Execute a shell command on an Android device
     *
     * @param TAG
     * @param cmd
     * @param asRoot
     * @return
     */
    public static int executeAndroidShellCommand(String TAG, String cmd, boolean asRoot) {
        Process p = null;
        DataOutputStream outs = null;
        int shellComandExitValue = 0;

        try {
            long startTime = System.currentTimeMillis();

            if (asRoot) {
                p = Runtime.getRuntime().exec("su");
                outs = new DataOutputStream(p.getOutputStream());
                outs.writeBytes(cmd + "\n");
                outs.writeBytes("exit\n");
                outs.close();
            } else {
                p = Runtime.getRuntime().exec(cmd);
                outs = new DataOutputStream(p.getOutputStream());
                outs.writeBytes("exit\n");
                outs.close();
            }

            shellComandExitValue = p.waitFor();
            System.out.println("Executed cmd: " + cmd + " in " + (System.currentTimeMillis() - startTime)
                    + " ms (exitValue: " + shellComandExitValue + ")");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            // destroyProcess(p);
            try {
                if (outs != null)
                    outs.close();
                // p.destroy();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return shellComandExitValue;
    }


    /**
     * Run a script in Android.
     */
    public static final class ScriptRunner extends Thread {
        private final File file;
        private final String script;
        private final StringBuilder res;
        private final boolean asroot;
        public int exitcode = -1;
        private Process exec;
        private static final String TAG = "ScriptRunner";

        /**
         * Creates a new script runner.
         *
         * @param file   temporary script file
         * @param script script to run
         * @param res    response output
         * @param asroot if true, executes the script as root
         */
        public ScriptRunner(File file, String script, StringBuilder res, boolean asroot) {
            this.file = file;
            this.script = script;
            this.res = res;
            this.asroot = asroot;
        }

        @Override
        public void run() {
            try {
                System.out.println("Running script: " + script);

                file.createNewFile();
                final String abspath = file.getAbsolutePath();
                // make sure we have execution permission on the script file
                Runtime.getRuntime().exec("chmod 777 " + abspath).waitFor();
                // Write the script to be executed
                final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
                if (new File("/system/bin/sh").exists()) {
                    out.write("#!/system/bin/sh\n");
                }
                out.write(script);
                if (!script.endsWith("\n"))
                    out.write("\n");
                out.write("exit\n");
                out.flush();
                out.close();
                if (this.asroot) {
                    // Create the "su" request to run the script
                    exec = Runtime.getRuntime().exec("su -c " + abspath);
                } else {
                    // Create the "sh" request to run the script
                    exec = Runtime.getRuntime().exec("sh " + abspath);
                }
                final InputStream stdout = exec.getInputStream();
                final InputStream stderr = exec.getErrorStream();
                final byte buf[] = new byte[8192];
                int read = 0;
                while (true) {
                    final Process localexec = exec;
                    if (localexec == null)
                        break;
                    try {
                        // get the process exit code - will raise IllegalThreadStateException if still running
                        this.exitcode = localexec.exitValue();
                    } catch (IllegalThreadStateException ex) {
                        // The process is still running
                    }
                    // Read stdout
                    if (stdout.available() > 0) {
                        read = stdout.read(buf);
                        if (res != null)
                            res.append(new String(buf, 0, read));
                    }
                    // Read stderr
                    if (stderr.available() > 0) {
                        read = stderr.read(buf);
                        if (res != null)
                            res.append(new String(buf, 0, read));
                    }
                    if (this.exitcode != -1) {
                        // finished
                        break;
                    }
                    // Sleep for the next round
                    Thread.sleep(50);
                }
            } catch (InterruptedException ex) {
                System.out.println("InterruptedException ");
                ex.printStackTrace();
                if (res != null)
                    res.append("\nOperation timed-out");
            } catch (Exception ex) {
                System.out.println("Exception");
                ex.printStackTrace();
                if (res != null)
                    res.append("\n" + ex);
            } finally {
                destroy();
            }
        }

        /**
         * Destroy this script runner
         */
        public synchronized void destroy() {
            if (exec != null)
                exec.destroy();
            exec = null;
        }
    }

    public static long copy(InputStream from, OutputStream to) throws IOException {
        // checkNotNull(from);
        // checkNotNull(to);
        byte[] buf = createBuffer();
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    static byte[] createBuffer() {
        return new byte[8192];
    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        // Presize the ByteArrayOutputStream since we know how large it will need
        // to be, unless that value is less than the default ByteArrayOutputStream
        // size (32).
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, in.available()));
        copy(in, out);
        return out.toByteArray();
    }

    public static String readResourceFileAsString(ClassLoader cl, String fileName) throws IOException {
        StringBuilder buf = new StringBuilder();

        InputStream is = cl.getResourceAsStream(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String temp;
        while ((temp = br.readLine()) != null) {
            buf.append(temp);
        }
        br.close();

        return buf.toString();
    }

    public static void checkResources(ClassLoader cl) {
        String filename = "/resources/libs/libnative-lib.jnilib";
        System.out.println("\nLooking for " + filename);
        InputStream is = cl.getResourceAsStream(filename);
        if (is == null) {
            System.out.println(filename + " does not exist");
        }

        filename = "resources/libs/libnative-lib.jnilib";
        System.out.println("\nLooking for " + filename);
        is = cl.getResourceAsStream(filename);
        if (is == null) {
            System.out.println(filename + " does not exist");
        }

        filename = "/libs/libnative-lib.jnilib";
        System.out.println("\nLooking for " + filename);
        is = cl.getResourceAsStream(filename);
        if (is == null) {
            System.out.println(filename + " does not exist");
        }

        filename = "libs/libnative-lib.jnilib";
        System.out.println("\nLooking for " + filename);
        is = cl.getResourceAsStream(filename);
        if (is == null) {
            System.out.println(filename + " does not exist");
        }

        filename = "cuda-kernels/matrixMul_kernel64.ptx";
        System.out.println("\nLooking for " + filename);
        is = cl.getResourceAsStream(filename);
        if (is == null) {
            System.out.println(filename + " does not exist");
        }
    }

    static void loadNativLibFromResources(ClassLoader cl, String path) throws IOException {

        // Obtain filename from path
        String[] parts = path.split("/");
        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;
        System.out.println("Loading library: " + filename);

        // Split filename to prexif and suffix (extension)
        String prefix = "";
        String suffix = null;
        if (filename != null) {
            parts = filename.split("\\.", 2);
            prefix = parts[0];
            suffix = (parts.length > 1) ? "."+parts[parts.length - 1] : null;
        }

        // Check if the filename is okay
        if (filename == null || prefix.length() < 3) {
            throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
        }

        System.out.println("Creating temp file: " + prefix + suffix);
        // Prepare temporary file
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();
        System.out.println("Creating temp file: " + temp.getAbsolutePath());

        if (!temp.exists()) {
            throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
        }

        // Prepare buffer for data copying
        byte[] buffer = new byte[1024];
        int readBytes;

        // Open and check input stream
        InputStream is = cl.getResourceAsStream(path);
        if (is == null) {
            throw new FileNotFoundException("File " + path + " was not found inside JAR.");
        }

        // Open output stream and copy data between source file in JAR and the temporary file
        OutputStream os = new FileOutputStream(temp);
        try {
            while ((readBytes = is.read(buffer)) != -1) {
                os.write(buffer, 0, readBytes);
            }
        } finally {
            // If read/write fails, close streams safely before throwing an exception
            os.close();
            is.close();
        }

        // Finally, load the library
        System.load(temp.getAbsolutePath());
    }

}
