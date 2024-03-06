package uk.ac.manchester.tornado.examples.rodinia.srad;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;


import java.util.Random;

public class Srad {
    static int size_I, size_R, iter, k, nthreads, r1 = 0, r2 = 0, c1 = 0, c2 = 0, rows = 0, cols = 0, niter = 10;
    static float sum, sum2, tmp, meanROI, varROI, Jc, G2, L, num, den, qsqr, cN, cS, cW, cE, D, lambda = 0, q0sqr = 0;
    static VectorInt iN, iS, jE, jW;
    static VectorFloat I, J, dN, dS, dW, dE, c;
    static boolean printFlag = true;

    public static void usage(String[] args) {
        System.out.printf("Usage: %s <rows> <cols> <y1> <y2> <x1> <x2> <no. of threads><lamda> <no. of iter>\n", args[0]);
        System.out.print("\t<rows>   - number of rows\n");
        System.out.print("\t<cols>    - number of cols\n");
        System.out.print("\t<y1>    - y1 value of the speckle\n");
        System.out.print("\t<y2>      - y2 value of the speckle\n");
        System.out.print("\t<x1>       - x1 value of the speckle\n");
        System.out.print("\t<x2>       - x2 value of the speckle\n");
        System.out.print("\t<lamda>   - lambda (0,1)\n");
        System.out.print("\t<no. of iter>   - number of iterations\n");
        System.exit(1);
    }

    public static void random_matrix(VectorFloat I, int rows, int cols) {
        Random random = new Random(7);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                I.set(i * cols + j, random.nextFloat());
            }
        }
    }

    public static void parallel1(VectorInt intParas, VectorFloat doubleParas, VectorFloat J, VectorFloat dN, VectorFloat dS, VectorFloat dW, VectorFloat dE, VectorInt iN, VectorInt iS, VectorInt jW, VectorInt jE, VectorFloat c) {
        for (@Parallel int i = 0; i < intParas.get(0); i++) {
            for (int j = 0; j < intParas.get(1); j++) {
                int k = i * intParas.get(1) + j;
                float Jc = J.get(k);
                // directional derivatives
                dN.set(k, J.get(iN.get(i) * intParas.get(1) + j) - Jc);
                dS.set(k, J.get(iS.get(i) * intParas.get(1) + j) - Jc);
                dW.set(k, J.get(i * intParas.get(1) + jW.get(j)) - Jc);
                dE.set(k, J.get(i * intParas.get(1) + jE.get(j)) - Jc);
                float G2 = (dN.get(k) * dN.get(k) + dS.get(k) * dS.get(k) +
                        dW.get(k) * dW.get(k) + dE.get(k) * dE.get(k)) / (Jc * Jc);
                float L = (dN.get(k) + dS.get(k) + dW.get(k) + dE.get(k)) / Jc;
                float num = (float) ((0.5 * G2) - ((1.0 / 16.0) * (L * L)));
                float den = (float) (1 + (.25 * L));
                float qsqr = num / (den * den);
                // diffusion coefficient (equ 33)
                den = (qsqr - doubleParas.get(1)) / (doubleParas.get(1) * (1 + doubleParas.get(1)));
                c.set(k, (float) (1.0 / (1.0 + den)));
                // saturate diffusion coefficient
                if (c.get(k) < 0) {
                    c.set(k, 0);
                } else if (c.get(k) > 1) {
                    c.set(k, 1);
                }
            }
        }
    }

    public static void parallel2(VectorInt intParas, VectorFloat doubleParas, VectorFloat c, VectorInt iS, VectorFloat dN, VectorFloat dS, VectorFloat dW, VectorFloat dE, VectorInt jE, VectorFloat J) {
        for (@Parallel int i = 0; i < intParas.get(0); i++) {
            for (int j = 0; j < intParas.get(1); j++) {
                // current index
                int k = i * intParas.get(1) + j;
                // diffusion coefficient
                float cN = c.get(k);
                float cS = c.get(iS.get(i) * intParas.get(1) + j);
                float cW = c.get(k);
                float cE = c.get(i * intParas.get(1) + jE.get(j));
                // divergence (equ 58)
                float D = cN * dN.get(k) + cS * dS.get(k) + cW * dW.get(k) + cE * dE.get(k);
                // image update (equ 61)
                J.set(k, (float) (J.get(k) + 0.25 * doubleParas.get(0) * D));
            }
        }
    }


    public static void main(String[] args) {
        VectorInt intParas = new VectorInt(2);            // rows&cols
        VectorFloat doubleParas = new VectorFloat(2);   // lambda&q0sqr
        if (args.length == 8) {
            rows = Integer.parseInt(args[0]); //number of rows in the domain
            cols = Integer.parseInt(args[1]); //number of cols in the domain
            if ((rows % 16 != 0) || (cols % 16 != 0)) {
                System.out.print("rows and cols must be multiples of 16\n");
                System.exit(1);
            }
            r1 = Integer.parseInt(args[2]); //y1 position of the speckle
            r2 = Integer.parseInt(args[3]); //y2 position of the speckle
            c1 = Integer.parseInt(args[4]); //x1 position of the speckle
            c2 = Integer.parseInt(args[5]); //x2 position of the speckle
            //nthreads = Integer.parseInt(args[6]); // number of threads
            lambda = Float.parseFloat(args[6]); //Lambda value
            niter = Integer.parseInt(args[7]); //number of iterations
            intParas.set(0, rows);
            intParas.set(1, cols);
        } else {
            usage(args);
        }

        size_I = cols * rows;
        size_R = (r2 - r1 + 1) * (c2 - c1 + 1);

        I = new VectorFloat(size_I);
        J = new VectorFloat(size_I);
        c = new VectorFloat(size_I);

        iN = new VectorInt(rows);
        iS = new VectorInt(rows);
        jW = new VectorInt(cols);
        jE = new VectorInt(cols);

        dN = new VectorFloat(size_I);
        dS = new VectorFloat(size_I);
        dW = new VectorFloat(size_I);
        dE = new VectorFloat(size_I);

        for (int i = 0; i < rows; i++) {
            iN.set(i, i - 1);
            iS.set(i, i + 1);
        }
        for (int j = 0; j < cols; j++) {
            jW.set(j, j - 1);
            jE.set(j, j + 1);
        }
        iN.set(0, 0);
        iS.set(rows - 1, rows - 1);
        jW.set(0, 0);
        jE.set(cols - 1, cols - 1);

        System.out.print("Randomizing the input matrix\n");

        random_matrix(I, rows, cols);

        for (k = 0; k < size_I; k++) {
            J.set(k, (float) TornadoMath.exp(I.get(k)));
        }

        System.out.print("Start the SRAD main loop\n");

        VectorInt temp = new VectorInt(1);

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, intParas, doubleParas, J, dN, dS, dW, dE, iN, iS, jW, jE, c)
                .task("t1", Srad::parallel1, intParas, doubleParas, J, dN, dS, dW, dE, iN, iS, jW, jE, c)
                .task("t2", Srad::parallel2, intParas, doubleParas, c, iS, dN, dS, dW, dE, jE, J)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, intParas, doubleParas, J, dN, dS, dW, dE, iN, iS, jW, jE, c);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);
//                .withDevice(device);

        for (iter = 0; iter < niter; iter++) {
            sum = 0;
            sum2 = 0;
            for (int i = r1; i <= r2; i++) {
                for (int j = c1; j <= c2; j++) {
                    tmp = J.get(i * cols + j);
                    sum += tmp;
                    sum2 += tmp * tmp;
                }
            }
            meanROI = sum / size_R;
            varROI = (sum2 / size_R) - meanROI * meanROI;
            q0sqr = varROI / (meanROI * meanROI);
            doubleParas.set(0, lambda);
            doubleParas.set(1, q0sqr);
            executor1.execute(); //parallel1(intParas, doubleParas);
            //executor2.execute();   //parallel2(intParas, doubleParas);
        }
        if (printFlag == true) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    System.out.printf("%.5f ", J.get(i * cols + j));
                }
                System.out.print("\n");
            }
        }
        System.out.print("Computation Done\n");
    }
}
