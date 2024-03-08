package uk.ac.manchester.tornado.examples.rodinia.pathfinder;

import java.util.Random;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DInt;

public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static VectorInt data;
    static Matrix2DInt wall;
    static VectorInt result;
    static final int seed = 9;
    static VectorInt dst;
    static VectorInt src;
    static VectorInt temp;

    public static void init(String[] args) {
        if (args.length == 2) {
            cols = Integer.parseInt(args[0]);
            rows = Integer.parseInt(args[1]);
        } else {
            System.out.println("Usage: Pathfinder  <width>  <num_of_steps>  >  out");
            System.exit(1);
        }
        data = new VectorInt(rows * cols);
        wall = new Matrix2DInt(rows, cols);
        result = new VectorInt(cols);

        Random rand = new Random(seed);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                wall.set(i, j, rand.nextInt(10));
            }
        }

        int count = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data.set(count, wall.get(i, j));
                count++;
            }
        }
        for (int j = 0; j < cols; j++) {
            result.set(j, wall.get(0, j));
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                System.out.print(wall.get(i, j) + " ");
            }
            System.out.println();
        }
    }

    public static void parallel(int t, VectorInt src, VectorInt dst, Matrix2DInt wall){
        for (@Parallel int n = 0; n < cols; n++){
            int min = src.get(n);
            if (n > 0){
                min = TornadoMath.min(min, src.get(n - 1));
            }
            if (n < cols - 1){
                min = TornadoMath.min(min, src.get(n + 1));
            }
            dst.set(n, wall.get(t + 1, n) + min);
        }
    }

    public static void run(String[] args) {
        init(args);
        dst = result;
        src = new VectorInt(cols);
        temp = new VectorInt(cols);

        long startTime = System.nanoTime();
        for (int t = 0; t < rows - 1; t++) {
            temp = src;
            src = dst;
            dst = temp;
            TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
            TaskGraph taskGraph1 = new TaskGraph("s1")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, t, src, dst, wall)
                    .task("t1", Pathfinder::parallel, t, src, dst, wall)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, dst);
            ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
            TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);
            executor1.execute();
        }
        long endTime = System.nanoTime();

        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);
        for (int i = 0; i < cols; i++) {
            System.out.print(data.get(i) + " ");
        }
        System.out.println();
        for (int i = 0; i < cols; i++) {
            System.out.print(dst.get(i) + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        run(args);
    }
}