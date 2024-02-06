package uk.ac.manchester.tornado.examples.rodinia.pathfinder;

import java.util.Random;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;

public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static VectorInt data;
    static VectorInt wall; // int[][] wall;
    static VectorInt result;
    static final int seed = 9;
    long cycles;
    static VectorInt src;
    static VectorInt dst;
    static VectorInt temp;
    static VectorInt min = new VectorInt(1);

    public static void init(String[] args) {
        if (args.length == 2) {
            cols = Integer.parseInt(args[0]);
            rows = Integer.parseInt(args[1]);
        } else {
            System.out.println("Usage: Pathfinder width num_of_steps > out\n");
            System.exit(1);
        }
        data = new VectorInt(rows * cols);
        wall = new VectorInt(rows * cols);
        result = new VectorInt(cols);

        Random rand = new Random(seed);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                wall.set(i * cols + j, rand.nextInt(10));
            }
        }
        for (int j = 0; j < result.size(); j++) {
            result.set(j, wall.get(j));
        }
        for (int i = 0; i < wall.size() / result.size(); i++) {
            for (int j = 0; j < result.size(); j++) {
                System.out.print(wall.get(i * result.size() + j) + " ");
            }
            System.out.println();
        }
    }

    public static void updateMin(VectorInt src, VectorInt dst, VectorInt vt, VectorInt min, VectorInt wall) {
        for (@Parallel int n = 0; n < src.size(); n++) {
            min.set(0, src.get(n));
            if (n > 0) {
                min.set(0, min.get(0) < src.get(n-1) ? min.get(0) : src.get(n-1));
//              min.set(0, TornadoMath.min(min.get(0), src.get(n - 1)));
//              min.set(0, Math.min(min.get(0), src.get(n - 1)));
            }
            if (n < src.size() - 1) {
                min.set(0, min.get(0) < src.get(n+1) ? min.get(0) : src.get(n+1));
//              min.set(0, TornadoMath.min(min.get(0), src.get(n + 1)));
//              min.set(0, Math.min(min.get(0), src.get(n + 1)));
            }
            dst.set(n, wall.get((vt.get(0) + 1) * src.size() + n) + min.get(0));
        }
    }

    public static void run(String[] args) {
        init(args);
        dst = result;
        src = new VectorInt(cols);
        temp = src;
        VectorInt vt = new VectorInt(1);

        long startTime = System.nanoTime();
        long graphtime = 0;
        for (int t = 0; t < wall.size() / src.size() - 1; t++) {
            VectorInt temp = src;
            src = dst;
            dst = temp;
            vt.set(0, t);
            long graphStartTime = System.nanoTime();
            TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
            TaskGraph taskGraph2 = new TaskGraph("s2")
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, src, vt, min, wall)
                    .task("t2", Pathfinder::updateMin, src, dst, vt, min, wall)
                    .transferToHost(DataTransferMode.FIRST_EXECUTION, dst, min);
            ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
            TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
                    .withDevice(device);
            long graphEndTime = System.nanoTime();
            graphtime = graphtime + (graphEndTime - graphStartTime);
            executor2.execute();
            // updateMin(src, dst, vt, min);
        }
        long endTime = System.nanoTime();
        for (int i = 0; i < cols; i++) {
            data.set(i, wall.get(i));
        }
        System.out.println("Compute time: " + (double)(endTime - startTime - graphtime) / 1000000000);
        for (int i = 0; i < cols; i++) {
            System.out.print(data.get(i) + " ");
        }
        System.out.println();
        for (int i = 0; i < cols; i++) {
            System.out.print(dst.get(i) + " ");
        }
    }

    public static void main(String[] args) {
        run(args);
    }
}