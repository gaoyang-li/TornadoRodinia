import java.util.Random;


public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static int[] data;
    static int[][] wall;
    static int[] result;
    static final int seed = 9;
    long cycles;

    public static void init(String[] args) {
        if (args.length == 2) {
            cols = Integer.parseInt(args[0]);
            rows = Integer.parseInt(args[1]);
        } else {
            System.out.println("Usage: java Pathfinder width num_of_steps > out");
            System.exit(1);
        }
        data = new int[rows * cols];
        wall = new int[rows][cols];
        result = new int[cols];

        Random rand = new Random(seed);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                wall[i][j] = rand.nextInt(10);
            }
        }
        int count = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[count] = wall[i][j];
                count++;
            }
        }
        for (int j = 0; j < cols; j++) {
            result[j] = wall[0][j];
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                System.out.print(wall[i][j] + " ");
            }
            System.out.println();
        }
    }

    public static void hello(int[] temp, int[] src, int[] dst, int[][] wall) {
        for (int t = 0; t < rows - 1; t++) {
            temp = src;
            src = dst;
            dst = temp;
            for (int n = 0; n < cols; n++){
                int min = src[n];
                if (n > 0){
                    min = Math.min(min, src[n-1]);
                }
                if (n < cols - 1){
                    min = Math.min(min, src[n+1]);
                }
                dst[n] = wall[t+1][n] + min;
            }
        }
    }

    public static void parallel(int t, int[] src, int[] dst, int[][] wall) {
        for (int n = 0; n < cols; n++){
            int min = src[n];
            if (n > 0){
                min = Math.min(min, src[n - 1]);
            }
            if (n < cols - 1){
                min = Math.min(min, src[n + 1]);
            }
            dst[n] = wall[t + 1][n] + min;
        }
    }

    public static void run(String[] args) {
        init(args);
        int[] dst = result;
        int[] src = new int[cols];
        int[] temp = new int[cols];

        long startTime = System.nanoTime();
        long graphtime = 0;
        int t = 0;

        hello(temp, src, dst, wall);

        dst = src;
        long endTime = System.nanoTime();

        System.out.println("Compute time: " + (double)(endTime - startTime - graphtime) / 1000000000);
        for (int i = 0; i < cols; i++) {
            System.out.print(data[i] + " ");
        }
        System.out.println();
        for (int i = 0; i < cols; i++) {
            System.out.print(dst[i] + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        run(args);
    }
}