import java.util.Random;

public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static int[] data;
    static int[] wall; // int[][] wall;
    static int[] result;
    static final int seed = 9;
    long cycles;
    static int[] src;
    static int[] dst;
    static int[] temp;
    static int[] min = new int[1];

    public static void init(String[] args) {
        if (args.length == 2) {
            cols = Integer.parseInt(args[0]);
            rows = Integer.parseInt(args[1]);
        } else {
            System.out.println("Usage: Pathfinder width num_of_steps > out\n");
            System.exit(1);
        }
        data = new int[rows * cols];
        wall = new int[rows * cols];
        result = new int[cols];

        Random rand = new Random(seed);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                wall[i * cols + j] = rand.nextInt(10);
            }
        }
        //        initTornado(result, wall);
        for (int j = 0; j < result.length; j++) {
            result[j] = wall[j];
        }
        for (int i = 0; i < wall.length / result.length; i++) {
            for (int j = 0; j < result.length; j++) {
                System.out.print(wall[i * result.length + j] + " ");
            }
            System.out.println();
        }
    }

    public static void updateMin(int[] src, int[] dst, int[] vt, int[] min, int[] wall) {
        for (int n = 0; n < src.length; n++) {
            min[0] = src[n];
            if (n > 0) {
                min[0] = min[0] < src[n - 1] ? min[0] : src[n - 1];
                //              min.set(0, TornadoMath.min(min.get(0), src.get(n - 1)));
                //              min.set(0, Math.min(min.get(0), src.get(n - 1)));
            }
            if (n < src.length - 1) {
                min[0] = min[0] < src[n + 1] ? min[0] : src[n + 1];
                //              min.set(0, TornadoMath.min(min.get(0), src.get(n + 1)));
                //              min.set(0, Math.min(min.get(0), src.get(n + 1)));
            }
            dst[n] = wall[(vt[0] + 1) * src.length + n] + min[0];
        }
    }

    public static void run(String[] args) {
        init(args);
        dst = result;
        src = new int[cols];
        temp = src;
        int[] vt = new int[1];

        long startTime = System.nanoTime();
        for (int t = 0; t < wall.length / src.length - 1; t++) {
            int[] temp = src;
            src = dst;
            dst = temp;
            vt[0] = t;
            updateMin(src, dst, vt, min, wall);
        }
        long endTime = System.nanoTime();
        for (int i = 0; i < cols; i++) {
            data[i] = wall[i];
        }
        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);
        for (int i = 0; i < cols; i++) {
            System.out.print(data[i] + " ");
        }
        System.out.println();
        for (int i = 0; i < cols; i++) {
            System.out.print(dst[i] + " ");
        }
    }

    public static void main(String[] args) {
        run(args);
    }
}
