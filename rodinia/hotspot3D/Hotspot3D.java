// small precision diff from the sequential
package uk.ac.manchester.tornado.examples.rodinia.hotspot3D;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;

public class Hotspot3D {
    static final int STR_SIZE = 256;
    static final float MAX_PD = 3.0e6f;
    /* required precision in degrees	*/
    static final float PRECISION = 0.001f;
    static final float SPEC_HEAT_SI = 1.75e6f;
    static final int K_SI = 100;
    /* capacitance fitting factor	*/
    static final float FACTOR_CHIP = 0.5f;

    /* chip parameters	*/
    static float t_chip = 0.0005f;
    static float chip_height = 0.016f;
    static float chip_width = 0.016f;
    /* ambient temperature, assuming no package at all	*/
    static float amb_temp = 80.0f;

    public static void readinput(VectorFloat vect, int grid_rows, int grid_cols, int layers, String file) {
        try (Scanner scanner = new Scanner(new File(file))) {
            for (int i = 0; i <= grid_rows - 1; i++) {
                for (int j = 0; j <= grid_cols - 1; j++) {
                    for (int k = 0; k <= layers - 1; k++) {
                        vect.set(i * grid_cols + j + k * grid_rows * grid_cols, scanner.nextFloat());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }
    }

    public static void writeoutput(VectorFloat vect, int grid_rows, int grid_cols, int layers, String file) {
        int index = 0;
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < grid_rows; i++) {
            for (int j = 0; j < grid_cols; j++) {
                for (int k = 0; k < layers; k++) {
                    str.append(String.format("%d\t%g\n", index, vect.get(i * grid_cols + j + k * grid_rows * grid_cols)));
                    index++;
                }
            }
        }
        try {
            PrintWriter writer = new PrintWriter(file);
            writer.print(str);
            writer.close();
        } catch (Exception e) {
            System.err.println("error writing file");
            System.exit(1);
        }
    }

    public static float accuracy(VectorFloat arr1, VectorFloat arr2, int len) {
        float err = 0.0f;
        int i;
        for (i = 0; i < len; i++) {
            err += (arr1.get(i) - arr2.get(i)) * (arr1.get(i) - arr2.get(i));
        }
        return (float) TornadoMath.sqrt(err / len);
    }

    public static void parallel(int nx, int ny, int nz,
                                float cc, float cw, float ce, float cs, float cb, float ct, float cn,
                                float dt, float Cap,
                                VectorFloat tOut_t, VectorFloat tIn_t, VectorFloat pIn) {
        for (@Parallel int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    int c, w, e, n, s, b, t;
                    c = x + y * nx + z * nx * ny;
                    w = (x == 0) ? c : c - 1;
                    e = (x == nx - 1) ? c : c + 1;
                    n = (y == 0) ? c : c - nx;
                    s = (y == ny - 1) ? c : c + nx;
                    b = (z == 0) ? c : c - nx * ny;
                    t = (z == nz - 1) ? c : c + nx * ny;
                    tOut_t.set(c, cc * tIn_t.get(c) + cw * tIn_t.get(w) + ce * tIn_t.get(e) +
                            cs * tIn_t.get(s) + cn * tIn_t.get(n) + cb * tIn_t.get(b) + ct * tIn_t.get(t) + (dt / Cap) * pIn.get(c) + ct * amb_temp);
                }
            }
        }
    }

    public static void runParallel(VectorFloat pIn, VectorFloat tIn, VectorFloat tOut,
                                      int nx, int ny, int nz, float Cap,
                                      float Rx, float Ry, float Rz,
                                      float dt, int numiter) {
        float stepDivCap = dt / Cap;
        float ce = stepDivCap / Rx;
        float cw = stepDivCap / Rx;
        float cn = stepDivCap / Ry;
        float cs = stepDivCap / Ry;
        float ct = stepDivCap / Rz;
        float cb = stepDivCap / Rz;
        float cc = (float)(1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct));

        {
            int count = 0;
            VectorFloat tIn_t = tIn;
            VectorFloat tOut_t = tOut;
            do {
                for (@Parallel int z = 0; z < nz; z++) {
                    for (int y = 0; y < ny; y++) {
                        for (int x = 0; x < nx; x++) {
                            int c, w, e, n, s, b, t;
                            c = x + y * nx + z * nx * ny;
                            w = (x == 0) ? c : c - 1;
                            e = (x == nx - 1) ? c : c + 1;
                            n = (y == 0) ? c : c - nx;
                            s = (y == ny - 1) ? c : c + nx;
                            b = (z == 0) ? c : c - nx * ny;
                            t = (z == nz - 1) ? c : c + nx * ny;
                            tOut_t.set(c, cc * tIn_t.get(c) + cw * tIn_t.get(w) + ce * tIn_t.get(e) +
                                    cs * tIn_t.get(s) + cn * tIn_t.get(n) + cb * tIn_t.get(b) + ct * tIn_t.get(t) + (dt / Cap) * pIn.get(c) + ct * amb_temp);
                        }
                    }
                }
                VectorFloat t = tIn_t;
                tIn_t = tOut_t;
                tOut_t = t;
                count++;
            } while (count < numiter);
        }
    }

    public static void computeTempCPU(VectorFloat pIn, VectorFloat tIn, VectorFloat tOut,
                                          int nx, int ny, int nz, float Cap,
                                          float Rx, float Ry, float Rz,
                                          float dt, int numiter) {

        float ce, cw, cn, cs, ct, cb, cc;
        float stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;
        cc = (float)(1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct));

        {
            int count = 0;
            VectorFloat tIn_t = tIn;
            VectorFloat tOut_t = tOut;
            do {
                parallel(nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn);
                VectorFloat t = tIn_t;
                tIn_t = tOut_t;
                tOut_t = t;
                count++;
            } while (count < numiter);
        }
    }

    public static void computeTempTornado(VectorFloat pIn, VectorFloat tIn, VectorFloat tOut,
                                          int nx, int ny, int nz, float Cap,
                                          float Rx, float Ry, float Rz,
                                          float dt, int numiter) {

        float ce, cw, cn, cs, ct, cb, cc;
        float stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;
        cc = (float)(1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct));

        {
            int count = 0;
            VectorFloat tIn_t = tIn;
            VectorFloat tOut_t = tOut;
            do {
                TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
                TaskGraph taskGraph1 = new TaskGraph("s1")
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn)
                        .task("t1", Hotspot3D::parallel, nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn)
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, tOut_t, tIn_t, pIn);
                ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
                TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);
//                        .withDevice(device);
                executor1.execute();
                VectorFloat t = tIn_t;
                tIn_t = tOut_t;
                tOut_t = t;
                count++;
            } while (count < numiter);
        }
    }

    public static void usage() {
        System.out.println("Usage: <rows/cols> <layers> <iterations> <powerFile> <tempFile> <outputFile>");
        System.out.println("\t<rows/cols>  - number of rows/cols in the grid (positive integer)");
        System.out.println("\t<layers>  - number of layers in the grid (positive integer)");
        System.out.println("\t<iteration> - number of iterations");
        System.out.println("\t<powerFile>  - name of the file containing the initial power values of each cell");
        System.out.println("\t<tempFile>  - name of the file containing the initial temperature values of each cell");
        System.out.println("\t<outputFile - output file");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length != 6) {
            usage();
        }

        String pfile, tfile, ofile;

        pfile = args[3];
        tfile = args[4];
        ofile = args[5];
        int numCols = Integer.parseInt(args[0]);
        int numRows = Integer.parseInt(args[0]);
        int layers = Integer.parseInt(args[1]);
        int iterations = Integer.parseInt(args[2]);
        /* calculating parameters*/

        float dx = chip_height / numRows;
        float dy = chip_width / numCols;
        float dz = t_chip / layers;

        float Cap = FACTOR_CHIP * SPEC_HEAT_SI * t_chip * dx * dy;
        float Rx = (float)(dy / (2.0 * K_SI * t_chip * dx));
        float Ry = (float)(dx / (2.0 * K_SI * t_chip * dy));
        float Rz = dz / (K_SI * dx * dy);

        float max_slope = MAX_PD / (FACTOR_CHIP * t_chip * SPEC_HEAT_SI);
        float dt = PRECISION / max_slope;

        VectorFloat powerIn, tempOut, tempIn, tempCopy;
        int size = numCols * numRows * layers;

        powerIn = new VectorFloat(size);
        tempCopy = new VectorFloat(size);
        tempIn = new VectorFloat(size);
        tempOut = new VectorFloat(size);
        VectorFloat answer = new VectorFloat(size);

        readinput(powerIn, numRows, numCols, layers, pfile);
        readinput(tempIn, numRows, numCols, layers, tfile);

        tempCopy = tempIn.duplicate();

        long startTime = System.nanoTime();
        computeTempTornado(powerIn, tempIn, tempOut, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);
        long endTime = System.nanoTime();
        double exeTime = (double)(endTime - startTime) / 1000000000;
        computeTempCPU(powerIn, tempCopy, answer, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);

        float acc = accuracy(tempOut, answer, numRows * numCols * layers);
        System.out.printf("Time: %.3f (s)\n", exeTime);
        System.out.printf("Accuracy: %e\n", acc);
        writeoutput(tempOut, numRows, numCols, layers, ofile);
    }

}