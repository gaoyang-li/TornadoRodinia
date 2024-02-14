package uk.ac.manchester.tornado.examples.rodinia.hotspot3D;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

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

    public static void readinput(float[] vect, int grid_rows, int grid_cols, int layers, String file) {
        try (Scanner scanner = new Scanner(new File(file))) {
            for (int i = 0; i <= grid_rows - 1; i++) {
                for (int j = 0; j <= grid_cols - 1; j++) {
                    for (int k = 0; k <= layers - 1; k++) {
                        vect[i * grid_cols + j + k * grid_rows * grid_cols] = scanner.nextFloat();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }
    }

    public static void writeoutput(float[] vect, int grid_rows, int grid_cols, int layers, String file) {
        int index = 0;
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < grid_rows; i++) {
            for (int j = 0; j < grid_cols; j++) {
                for (int k = 0; k < layers; k++) {
                    str.append(String.format("%d\t%g\n", index, vect[i * grid_cols + j + k * grid_rows * grid_cols]));
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

    public static void computeTempCPU(float[] pIn, float[] tIn, float[] tOut,
                                      int nx, int ny, int nz, float Cap,
                                      float Rx, float Ry, float Rz,
                                      float dt, int numiter) {
        float ce, cw, cn, cs, ct, cb, cc;
        float stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;
        cc = (float) (1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct));
        int c, w, e, n, s, b, t;
        int x, y, z;
        int i = 0;
        do {
            for (z = 0; z < nz; z++) {
                for (y = 0; y < ny; y++) {
                    for (x = 0; x < nx; x++) {
                        c = x + y * nx + z * nx * ny;
                        w = (x == 0) ? c : c - 1;
                        e = (x == nx - 1) ? c : c + 1;
                        n = (y == 0) ? c : c - nx;
                        s = (y == ny - 1) ? c : c + nx;
                        b = (z == 0) ? c : c - nx * ny;
                        t = (z == nz - 1) ? c : c + nx * ny;

                        tOut[c] = tIn[c] * cc + tIn[n] * cn + tIn[s] * cs + tIn[e] * ce + tIn[w] * cw + tIn[t] * ct + tIn[b] * cb + (dt / Cap) * pIn[c] + ct * amb_temp;
                    }
                }
            }
            float[] temp = tIn;
            tIn = tOut;
            tOut = temp;
            i++;
        }
        while (i < numiter);
    }

    public static float accuracy(float[] arr1, float[] arr2, int len) {
        float err = 0.0f;
        int i;
        for (i = 0; i < len; i++) {
            err += (arr1[i] - arr2[i]) * (arr1[i] - arr2[i]);
        }
        return (float) Math.sqrt(err / len);
    }

    public static void parallel(int nx, int ny, int nz,
                                float cc, float cw, float ce, float cs, float cb, float ct, float cn,
                                float dt, float Cap,
                                float[] tOut_t, float[] tIn_t, float[] pIn){
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                int x;
                for (x = 0; x < nx; x++) {
                    int c, w, e, n, s, b, t;
                    c = x + y * nx + z * nx * ny;
                    w = (x == 0) ? c : c - 1;
                    e = (x == nx - 1) ? c : c + 1;
                    n = (y == 0) ? c : c - nx;
                    s = (y == ny - 1) ? c : c + nx;
                    b = (z == 0) ? c : c - nx * ny;
                    t = (z == nz - 1) ? c : c + nx * ny;
                    tOut_t[c] = cc * tIn_t[c] + cw * tIn_t[w] + ce * tIn_t[e] +
                            cs * tIn_t[s] + cn * tIn_t[n] + cb * tIn_t[b] + ct * tIn_t[t] + (dt / Cap) * pIn[c] + ct * amb_temp;
                }
            }
        }
    }

    public static void computeTempTornado(float[] pIn, float[] tIn, float[] tOut,
                                      int nx, int ny, int nz, float Cap,
                                      float Rx, float Ry, float Rz,
                                      float dt, int numiter) {

        float ce, cw, cn, cs, ct, cb, cc;

        float stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;

        cc = (float) (1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct));

        {
            int count = 0;
            float[] tIn_t = tIn;
            float[] tOut_t = tOut;

            do {
                TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
                TaskGraph taskGraph1 = new TaskGraph("s1")
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn)
                        .task("t1", Hotspot3D::parallel, nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn)
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, tOut_t, tIn_t, pIn);
                ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
                TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                        .withDevice(device);
                executor1.execute();
                //parallel(nx, ny, nz, cc, cw, ce, cs, cb, ct, cn, dt, Cap, tOut_t, tIn_t, pIn);
                float[] t = tIn_t;
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

        String pfile, tfile, ofile; // *testFile;

        pfile = args[3];
        tfile = args[4];
        ofile = args[5];
        //testFile = args[7];
        int numCols = Integer.parseInt(args[0]);
        int numRows = Integer.parseInt(args[0]);
        int layers = Integer.parseInt(args[1]);
        int iterations = Integer.parseInt(args[2]);
        /* calculating parameters*/

        float dx = chip_height / numRows;
        float dy = chip_width / numCols;
        float dz = t_chip / layers;

        float Cap = FACTOR_CHIP * SPEC_HEAT_SI * t_chip * dx * dy;
        float Rx = (float) (dy / (2.0 * K_SI * t_chip * dx));
        float Ry = (float) (dx / (2.0 * K_SI * t_chip * dy));
        float Rz = dz / (K_SI * dx * dy);

        // cout << Rx << " " << Ry << " " << Rz << endl;
        float max_slope = MAX_PD / (FACTOR_CHIP * t_chip * SPEC_HEAT_SI);
        float dt = PRECISION / max_slope;

        float[] powerIn, tempOut, tempIn, tempCopy; // *pCopy;
        //    float *d_powerIn, *d_tempIn, *d_tempOut;
        int size = numCols * numRows * layers;

        powerIn = new float[size];
        tempCopy = new float[size];
        tempIn = new float[size];
        tempOut = new float[size];
        //pCopy = (float*)calloc(size,sizeof(float));
        float[] answer = new float[size];

        // outCopy = (float*)calloc(size, sizeof(float));
        readinput(powerIn, numRows, numCols, layers, pfile);
        readinput(tempIn, numRows, numCols, layers, tfile);

        System.arraycopy(tempIn, 0, tempCopy, 0, size);

        //struct timeval start, stop;
        long startTime = System.nanoTime();
        computeTempTornado(powerIn, tempIn, tempOut, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);
        long endTime = System.nanoTime();
        double exeTime = (double) (endTime - startTime) /1000000000;
        computeTempCPU(powerIn, tempCopy, answer, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);

        float acc = accuracy(tempOut, answer, numRows * numCols * layers);
        System.out.printf("Time: %.3f (s)\n", exeTime);
        System.out.printf("Accuracy: %e\n", acc);
        writeoutput(tempOut, numRows, numCols, layers, ofile);
    }

}