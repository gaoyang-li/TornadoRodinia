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
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DInt;

public class Pathfinder {
    static int rows = 0;
    static int cols = 0;
    static VectorInt data;
    static Matrix2DInt wall;
    static VectorInt result;
    static final int seed = 9;
    long cycles;

    public static void init(String[] args) {
        if (args.length == 2) {
            cols = Integer.parseInt(args[0]);
            rows = Integer.parseInt(args[1]);
        } else {
            System.out.println("Usage: Pathfinder width num_of_steps > out");
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

    public static void hello(VectorInt temp, VectorInt src, VectorInt dst, Matrix2DInt wall) {
        for (int t = 0; t < rows - 1; t++) {
            temp = src;
            src = dst;
            dst = temp;
            for (@Parallel int n = 0; n < cols; n++){
                int min = src.get(n);
                if (n > 0){
                    min = Math.min(min, src.get(n - 1));
                }
                if (n < cols - 1){
                    min = Math.min(min, src.get(n + 1));
                }
                dst.set(n, wall.get(t + 1, n) + min);
            }
        }
    }

    public static void parallel(int t, VectorInt src, VectorInt dst, Matrix2DInt wall) {
        for (@Parallel int n = 0; n < cols; n++){
            int min = src.get(n);
            if (n > 0){
                min = Math.min(min, src.get(n - 1));
            }
            if (n < cols - 1){
                min = Math.min(min, src.get(n + 1));
            }
            dst.set(n, wall.get(t + 1, n) + min);
        }
    }

    public static void run(String[] args) {
        init(args);
        VectorInt dst = result;
        VectorInt src = new VectorInt(cols);
        VectorInt temp = new VectorInt(cols);

        long startTime = System.nanoTime();
        long graphtime = 0;
        int t = 0;

//        for (t = 0; t < rows - 1; t++) {
//            temp = src;
//            src = dst;
//            dst = temp;
//            long graphStartTime = System.nanoTime();
//            long graphEndTime = System.nanoTime();
//            graphtime = graphtime + (graphEndTime - graphStartTime);
//            TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
//            TaskGraph taskGraph1 = new TaskGraph("s1")
//                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, src, dst, wall)
//                    .task("t1", Pathfinder::parallel, t, src, dst, wall)
//                    .transferToHost(DataTransferMode.EVERY_EXECUTION, src, dst, wall);
//            ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
//            TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
//                    .withDevice(device);
//            executor1.execute();
//        }
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, temp, src, dst, wall)
                .task("t1", Pathfinder::hello, temp, src, dst, wall)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, temp, src, dst, wall);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        executor1.execute();
        //hello(temp, src, dst, wall);
//        System.out.println("temp:" + temp.toString());
//        System.out.println("src:" + src.toString());
//        System.out.println("dst:" + dst.toString());
        dst = src;
        long endTime = System.nanoTime();

        System.out.println("Compute time: " + (double)(endTime - startTime - graphtime) / 1000000000);
        for (int i = 0; i < cols; i++) {
            System.out.print(data.get(i) + " ");
        }
        System.out.println();
        for (int i = 0; i < cols; i++) {
            System.out.print(dst.get(i) + " ");
        }
        System.out.println();


        System.out.println("lgy:");
        System.out.println("wall:");
        for (int i = 0; i < rows; i++){
            for (int j = 0; j < cols; j++){
                System.out.print(wall.get(i, j) + " ");
            }
            System.out.println();
        }
        System.out.println("data:");
        for (int j = 0; j < rows*cols; j++){
            System.out.print(data.get(j) + " ");
        }
        System.out.println();
        System.out.println("result:");
        for (int j = 0; j < cols; j++){
            System.out.print(result.get(j) + " ");
        }
        System.out.println();
        System.out.println("dst:");
        for (int j = 0; j < cols; j++){
            System.out.print(dst.get(j) + " ");
        }
    }

    public static void main(String[] args) {
        run(args);
    }
}