package uk.ac.manchester.tornado.examples.rodinia.Pathfinder;

import java.util.Random;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.collections.types.Int2;
import uk.ac.manchester.tornado.api.collections.types.Int3;
import uk.ac.manchester.tornado.api.collections.types.Int4;
import uk.ac.manchester.tornado.api.collections.types.Int8;
import uk.ac.manchester.tornado.api.collections.types.VectorInt;
import uk.ac.manchester.tornado.api.collections.types.VectorInt2;
import uk.ac.manchester.tornado.api.collections.types.VectorInt3;
import uk.ac.manchester.tornado.api.collections.types.VectorInt4;
import uk.ac.manchester.tornado.api.collections.types.VectorInt8;

public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static VectorInt data;
    static VectorInt wall;// int[][] wall;
    static VectorInt result;
    static final int seed = 9;
    long cycles;
    static VectorInt src;
    static VectorInt dst;
    static VectorInt temp;
    static VectorInt min = new VectorInt(1);

    public static void initTornado(VectorInt result, VectorInt wall) {
        for (@Parallel int j = 0; j < result.size(); j++) {
            result.set(j, wall.get(j));
        }
        for (@Parallel int i = 0; i < wall.size()/result.size(); i++) {
            for (@Parallel int j = 0; j < result.size(); j++) {
                System.out.print(wall.get(i*result.size()+j) + " ");
            }
            System.out.println();
        }
    }
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
        for (@Parallel int i = 0; i < rows; i++) {
            for (@Parallel int j = 0; j < cols; j++) {
                wall.set(i*cols+j, rand.nextInt(10));
            }
        }
//        initTornado(result, wall);
        for (int j = 0; j < result.size(); j++) {
            result.set(j, wall.get(j));
        }
        for (int i = 0; i < wall.size()/result.size(); i++) {
            for (int j = 0; j < result.size(); j++) {
                System.out.print(wall.get(i*result.size()+j) + " ");
            }
            System.out.println();
        }
//        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
//        TaskGraph taskGraph1 = new TaskGraph("s1")
//                .transferToDevice(DataTransferMode.EVERY_EXECUTION, result, wall)
//                .task("t1", Pathfinder::initTornado, result, wall)
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, result, wall);
//        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
//        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                .withDevice(device);
//        executor1.execute();
    }

//    public static void runTornado(VectorInt src, VectorInt dst, VectorInt wall){
//        for (int t = 0; t < wall.size()/src.size() - 1; t++) {
//            VectorInt temp = src;
//            src = dst;
//            dst = temp;
//            for (int n = 0; n < src.size(); n++) {
//                min = src.get(n);
//                if (n > 0)
//                    min = Math.min(min, src.get(n - 1));
//                if (n < src.size() - 1)
//                    min = Math.min(min, src.get(n + 1));
//                dst.set(n, wall.get((t+1)*src.size()+n) + min);
//            }
//        }
//    }

    public static void updateMin(VectorInt src, VectorInt dst, VectorInt vt, VectorInt min){
        for (int n = 0; n < src.size(); n++) {
            min.set(0, src.get(n));
            if (n > 0)
                min.set(0, Math.min(min.get(0), src.get(n - 1)));
            if (n < src.size() - 1)
                min.set(0, Math.min(min.get(0), src.get(n + 1)));
            dst.set(n, wall.get((vt.get(0)+1)*src.size()+n) + min.get(0));
        }
    }

    public static void run(String[] args) {
        init(args);
        dst = result;
        src = new VectorInt(cols);
        temp = src;
        long startTime = System.nanoTime();
        VectorInt vt = new VectorInt(1);
//        runTornado(src, dst, wall);
        for (int t = 0; t < wall.size()/src.size() - 1; t++) {
            VectorInt temp = src;
            src = dst;
            dst = temp;
            vt.set(0, t);
//            TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
//            TaskGraph taskGraph2 = new TaskGraph("s2")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, src, dst, vt, min, wall)
//                    .task("t2", Pathfinder::updateMin, src, dst, vt, min, wall)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, src, dst, vt, min, wall);
//            ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
//            TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
//                    .withDevice(device);
//            executor2.execute();
            updateMin(src, dst, vt, min);
//            for (int n = 0; n < src.size(); n++) {
//                min = src.get(n);
//                if (n > 0)
//                    min = Math.min(min, src.get(n - 1));
//                if (n < src.size() - 1)
//                    min = Math.min(min, src.get(n + 1));
//                dst.set(n, wall.get((t+1)*src.size()+n) + min);
//            }
        }
        long endTime = System.nanoTime();
        for (int i = 0; i < cols; i++) {
            data.set(i, wall.get(i));
        }
        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);
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