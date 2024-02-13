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
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble;

public class Hotspot3D {
    static final int STR_SIZE = 256;
    static final double MAX_PD = 3.0e6;
    /* required precision in degrees	*/
    static final double PRECISION = 0.001;
    static final double SPEC_HEAT_SI = 1.75e6;
    static final int K_SI = 100;
    /* capacitance fitting factor	*/
    static final double FACTOR_CHIP = 0.5;

    /* chip parameters	*/
    static double t_chip = 0.0005;
    static double chip_height = 0.016;
    static double chip_width = 0.016;
    /* ambient temperature, assuming no package at all	*/
    static double amb_temp = 80.0;

    public static void readinput(double[] vect, int grid_rows, int grid_cols, int layers, String file) {
        try (Scanner scanner = new Scanner(new File(file))) {
            for (int i = 0; i <= grid_rows - 1; i++) {
                for (int j = 0; j <= grid_cols - 1; j++) {
                    for (int k = 0; k <= layers - 1; k++) {
                        vect[i * grid_cols + j + k * grid_rows * grid_cols] = scanner.nextDouble();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }
    }

    public static void writeoutput(double[] vect, int grid_rows, int grid_cols, int layers, String file) {
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

    public static void computeTempCPU(double[] pIn, double[] tIn, double[] tOut,
                                      int nx, int ny, int nz, double Cap,
                                      double Rx, double Ry, double Rz,
                                      double dt, int numiter) {
        double ce, cw, cn, cs, ct, cb, cc;
        double stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;
        cc = 1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct);
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
            double[] temp = tIn;
            tIn = tOut;
            tOut = temp;
            i++;
        }
        while (i < numiter);
    }

    public static double accuracy(double[] arr1, double[] arr2, int len) {
        double err = 0.0;
        int i;
        for (i = 0; i < len; i++) {
            err += (arr1[i] - arr2[i]) * (arr1[i] - arr2[i]);
        }
        return Math.sqrt(err / len);
    }

    public static void parallel(int nx, int ny, int nz,
                                double cc, double cw, double ce, double cs, double cb, double ct, double cn,
                                double dt, double Cap,
                                double[] tOut_t, double[] tIn_t, double[] pIn){
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

    public static void computeTempTornado(double[] pIn, double[] tIn, double[] tOut,
                                      int nx, int ny, int nz, double Cap,
                                      double Rx, double Ry, double Rz,
                                      double dt, int numiter) {

        double ce, cw, cn, cs, ct, cb, cc;

        double stepDivCap = dt / Cap;
        ce = cw = stepDivCap / Rx;
        cn = cs = stepDivCap / Ry;
        ct = cb = stepDivCap / Rz;

        cc = 1.0 - (2.0 * ce + 2.0 * cn + 3.0 * ct);

        {
            int count = 0;
            double[] tIn_t = tIn;
            double[] tOut_t = tOut;

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
                double[] t = tIn_t;
                tIn_t = tOut_t;
                tOut_t = t;
                count++;
            } while (count < numiter);
        }
    }

    public static void usage() {
        System.out.printf("Usage: <rows/cols> <layers> <iterations> <powerFile> <tempFile> <outputFile>\n");
        System.out.printf("\t<rows/cols>  - number of rows/cols in the grid (positive integer)\n");
        System.out.printf("\t<layers>  - number of layers in the grid (positive integer)\n");
        System.out.printf("\t<iteration> - number of iterations\n");
        System.out.printf("\t<powerFile>  - name of the file containing the initial power values of each cell\n");
        System.out.printf("\t<tempFile>  - name of the file containing the initial temperature values of each cell\n");
        System.out.printf("\t<outputFile - output file\n");
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

        double dx = chip_height / numRows;
        double dy = chip_width / numCols;
        double dz = t_chip / layers;

        double Cap = FACTOR_CHIP * SPEC_HEAT_SI * t_chip * dx * dy;
        double Rx = dy / (2.0 * K_SI * t_chip * dx);
        double Ry = dx / (2.0 * K_SI * t_chip * dy);
        double Rz = dz / (K_SI * dx * dy);

        // cout << Rx << " " << Ry << " " << Rz << endl;
        double max_slope = MAX_PD / (FACTOR_CHIP * t_chip * SPEC_HEAT_SI);
        double dt = PRECISION / max_slope;

        double[] powerIn, tempOut, tempIn, tempCopy; // *pCopy;
        //    double *d_powerIn, *d_tempIn, *d_tempOut;
        int size = numCols * numRows * layers;

        powerIn = new double[size];
        tempCopy = new double[size];
        tempIn = new double[size];
        tempOut = new double[size];
        //pCopy = (double*)calloc(size,sizeof(double));
        double[] answer = new double[size];

        // outCopy = (double*)calloc(size, sizeof(double));
        readinput(powerIn, numRows, numCols, layers, pfile);
        readinput(tempIn, numRows, numCols, layers, tfile);

        System.arraycopy(tempIn, 0, tempCopy, 0, size);

        //struct timeval start, stop;
        double time;
        long startTime = System.nanoTime();
        computeTempTornado(powerIn, tempIn, tempOut, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);
        long endTime = System.nanoTime();
        time = (endTime - startTime) / 1000000000;
        computeTempCPU(powerIn, tempCopy, answer, numCols, numRows, layers, Cap, Rx, Ry, Rz, dt, iterations);

        double acc = accuracy(tempOut, answer, numRows * numCols * layers);
        System.out.printf("Time: %.3f (s)\n", time);
        System.out.printf("Accuracy: %e\n", acc);
        writeoutput(tempOut, numRows, numCols, layers, ofile);
    }

}