// make ptx
package uk.ac.manchester.tornado.examples.rodinia.hotspot;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;

public class Hotspot {

    public static double get_time() {
        return (double) (System.nanoTime()) / 1000000000;
    }

    static VectorFloat result;
    static VectorFloat temp;
    static VectorFloat power;
    static final int BLOCK_SIZE = 16;
    static final int BLOCK_SIZE_C = 16;
    static final int BLOCK_SIZE_R = 16;
    static final int STR_SIZE = 256;

    /* maximum power density possible (say 300W for a 10mm x 10mm chip)	*/
    static final float MAX_PD = 3.0e6f;
    /* required precision in degrees	*/
    static final float PRECISION = 0.001f;
    static final float SPEC_HEAT_SI = 1.75e6f;
    static final int K_SI = 100;
    /* capacitance fitting factor	*/
    static final float FACTOR_CHIP = 0.5f;
    //#define NUM_THREAD 4
    /* chip parameters	*/
    static final float t_chip = 0.0005f;
    static final float chip_height = 0.016f;
    static final float chip_width = 0.016f;

    /* ambient temperature, assuming no package at all	*/
    static final float amb_temp = 80.0f;

    public static void parallel(VectorFloat temp, VectorFloat power, VectorFloat result, VectorFloat delta, int num_chunk, int chunks_in_row, int chunks_in_col, int row, int col, float Cap_1, float Rx_1, float Ry_1, float Rz_1) {
        for (@Parallel int chunk = 0; chunk < num_chunk; ++chunk) {
            int r_start = BLOCK_SIZE_R * (chunk / chunks_in_col);
            int c_start = BLOCK_SIZE_C * (chunk % chunks_in_row);
            int r_end = r_start + BLOCK_SIZE_R > row ? row : r_start + BLOCK_SIZE_R;
            int c_end = c_start + BLOCK_SIZE_C > col ? col : c_start + BLOCK_SIZE_C;

            if (r_start == 0 || c_start == 0 || r_end == row || c_end == col) {
                for (int r = r_start; r < r_start + BLOCK_SIZE_R; ++r) {
                    for (int c = c_start; c < c_start + BLOCK_SIZE_C; ++c) {
                        /* Corner 1 */
                        if ((r == 0) && (c == 0)) {
                            delta.set(0, (Cap_1) * (power.get(0) + (temp.get(1) - temp.get(0)) * Rx_1 + (temp.get(col) - temp.get(0)) * Ry_1 + (amb_temp - temp.get(0)) * Rz_1));

                        }
                        /* Corner 2 */
                        else if ((r == 0) && (c == col - 1)) {
                            delta.set(0, (Cap_1) * (power.get(c) + (temp.get(c - 1) - temp.get(c)) * Rx_1 + (temp.get(c + col) - temp.get(c)) * Ry_1 + (amb_temp - temp.get(c)) * Rz_1));

                        }
                        /* Corner 3 */
                        else if ((r == row - 1) && (c == col - 1)) {
                            delta.set(0, (Cap_1) * (power.get(r * col + c) + (temp.get(r * col + c - 1) - temp.get(r * col + c)) * Rx_1 + (temp.get((r - 1) * col + c) - temp.get(r * col + c)) * Ry_1 + (amb_temp - temp.get(r * col + c)) * Rz_1));

                        }
                        /* Corner 4	*/
                        else if ((r == row - 1) && (c == 0)) {
                            delta.set(0, (Cap_1) * (power.get(r * col) + (temp.get(r * col + 1) - temp.get(r * col)) * Rx_1 + (temp.get((r - 1) * col) - temp.get(r * col)) * Ry_1 + (amb_temp - temp.get(r * col)) * Rz_1));

                        }
                        /* Edge 1 */
                        else if ((r == 0)) {
                            delta.set(0, (float) ((Cap_1) * (power.get(c) + (temp.get(c + 1) + temp.get(c - 1) - 2.0 * temp.get(c)) * Rx_1 + (temp.get(col + c) - temp.get(c)) * Ry_1 + (amb_temp - temp.get(c)) * Rz_1)));

                        }
                        /* Edge 2 */
                        else if ((c == col - 1)) {
                            delta.set(0, (float) ((Cap_1) * (power.get(r * col + c) + (temp.get((r + 1) * col + c) + temp.get((r - 1) * col + c) - 2.0 * temp.get(r * col + c)) * Ry_1 + (temp.get(r * col + c - 1) - temp.get(r * col + c)) * Rx_1 + (amb_temp - temp.get(r * col + c)) * Rz_1)));

                        }
                        /* Edge 3 */
                        else if ((r == row - 1)) {
                            delta.set(0, (float) ((Cap_1) * (power.get(r * col + c) + (temp.get(r * col + c + 1) + temp.get(r * col + c - 1) - 2.0 * temp.get(r * col + c)) * Rx_1 + (temp.get((r - 1) * col + c) - temp.get(r * col + c)) * Ry_1 + (amb_temp - temp.get(r * col + c)) * Rz_1)));

                        }
                        /* Edge 4 */
                        else if ((c == 0)) {
                            delta.set(0, (float) ((Cap_1) * (power.get(r * col) + (temp.get((r + 1) * col) + temp.get((r - 1) * col) - 2.0 * temp.get(r * col)) * Ry_1 + (temp.get(r * col + 1) - temp.get(r * col)) * Rx_1 + (amb_temp - temp.get(r * col)) * Rz_1)));

                        }
                        result.set(r * col + c, temp.get(r * col + c) + delta.get(0));
                    }
                }
                continue;
            }

            for (int r = r_start; r < r_start + BLOCK_SIZE_R; ++r) {
                for (int c = c_start; c < c_start + BLOCK_SIZE_C; ++c) {
                    result.set(r * col + c, (float) (temp.get(r * col + c) + (Cap_1 * (power.get(r * col + c) + (temp.get((r + 1) * col + c) + temp.get((r - 1) * col + c) - 2.0 * temp.get(r * col + c)) * Ry_1 + (temp.get(r * col + c + 1) + temp.get(r * col + c - 1) - 2.0 * temp.get(r * col + c)) * Rx_1 + (amb_temp - temp.get(r * col + c)) * Rz_1))));
                }
            }
        }
    }

    /* Single iteration of the transient solver in the grid model.
     * advances the solution of the discretized difference equations
     * by one time step
     */
    public static void single_iteration(VectorFloat result, VectorFloat temp, VectorFloat power, int row, int col, float Cap_1, float Rx_1, float Ry_1, float Rz_1, float step) {
        VectorFloat delta = new VectorFloat(1); //float delta = 0.0;
        delta.set(0, 0.0f);
        //int r, c;
        //int chunk;
        int num_chunk = row * col / (BLOCK_SIZE_R * BLOCK_SIZE_C);
        int chunks_in_row = col / BLOCK_SIZE_C;
        int chunks_in_col = row / BLOCK_SIZE_R;
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, temp, power, result, delta, num_chunk, chunks_in_row, chunks_in_col, row, col, Cap_1, Rx_1, Ry_1, Rz_1)
                .task("t1", Hotspot::parallel, temp, power, result, delta, num_chunk, chunks_in_row, chunks_in_col, row, col, Cap_1, Rx_1, Ry_1, Rz_1)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, temp, result);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        executor1.execute();
        //parallel(temp,  power, result, delta,  num_chunk,  chunks_in_row, chunks_in_col , row, col,  Cap_1,  Rx_1,  Ry_1,  Rz_1);
        VectorFloat tmp = new VectorFloat(temp.size());
        for (int i = 0; i < temp.size(); i++) {
            temp.set(i, result.get(i));
        }
        for (int i = 0; i < temp.size(); i++) {
            result.set(i, tmp.get(i));
        }
    }

    /* Transient solver driver routine: simply converts the heat
     * transfer differential equations to difference equations
     * and solves the difference equations by iterating
     */
    public static void compute_tran_temp(VectorFloat result, int num_iterations, VectorFloat temp, VectorFloat power, int row, int col) {
        int i = 0;
        float grid_height = chip_height / row;
        float grid_width = chip_width / col;
        float Cap = FACTOR_CHIP * SPEC_HEAT_SI * t_chip * grid_width * grid_height;
        float Rx = (float) (grid_width / (2.0 * K_SI * t_chip * grid_height));
        float Ry = (float) (grid_height / (2.0 * K_SI * t_chip * grid_width));
        float Rz = t_chip / (K_SI * grid_height * grid_width);
        float max_slope = MAX_PD / (FACTOR_CHIP * t_chip * SPEC_HEAT_SI);
        float step = (float) (PRECISION / max_slope / 1000.0);
        float Rx_1 = (float) (1.0 / Rx);
        float Ry_1 = (float) (1.0 / Ry);
        float Rz_1 = (float) (1.0 / Rz);
        float Cap_1 = step / Cap;
        System.out.printf("total iterations: %d s\tstep size: %g s\n", num_iterations, step);
        System.out.printf("Rx: %g\tRy: %g\tRz: %g\tCap: %g\n", Rx, Ry, Rz, Cap);
        int array_size = row * col;
        {
            for (i = 0; i < num_iterations; i++) {
                System.out.printf("iteration %d\n", i++);
                single_iteration(result, temp, power, row, col, Cap_1, Rx_1, Ry_1, Rz_1, step);
            }
        }
        System.out.printf("iteration %d\n", i++);
    }

    public static void writeOutput(VectorFloat vect, int grid_rows, int grid_cols, String file) {
        int i, j, index = 0;
        try {
            PrintWriter writer = new PrintWriter(file);
            for (i = 0; i < grid_rows; i++) {
                for (j = 0; j < grid_cols; j++) {
                    writer.printf("%d\t%g\n", index, vect.get(i * grid_cols + j));
                    index++;
                }
            }
            writer.close();
            System.out.println("Result stored in " + file);
        } catch (Exception e) {
            System.out.println("Error writing to the output file");
        }
    }

    public static void readInput(VectorFloat vect, int grid_rows, int grid_cols, String file) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(file));
            for (int i = 0; i < grid_rows * grid_cols; i++) {
                if (!scanner.hasNextDouble()) {
                    System.out.println("not enough lines in file");
                    System.exit(1);
                } else {
                    vect.set(i, scanner.nextFloat());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void usage() {
        System.err.println("Usage: Hotspot <grid_rows> <grid_cols> <sim_time> <temp_file> <power_file> <output_file>");
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length != 6) {
            usage();
        }
        int grid_rows, grid_cols, sim_time, i;
        String tfile, pfile, ofile;
        grid_rows = Integer.parseInt(args[0]);
        grid_cols = Integer.parseInt(args[1]);
        sim_time = Integer.parseInt(args[2]);
        temp = new VectorFloat(grid_rows * grid_cols);
        power = new VectorFloat(grid_rows * grid_cols);
        result = new VectorFloat(grid_rows * grid_cols);
        tfile = args[3];
        pfile = args[4];
        ofile = args[5];
        readInput(temp, grid_rows, grid_cols, tfile);
        readInput(power, grid_rows, grid_cols, pfile);
        double start = get_time();
        compute_tran_temp(result, sim_time, temp, power, grid_rows, grid_cols);
        double end = get_time();
        System.out.println("Ending simulation");
        System.out.printf("Total time: %.3f seconds\n", (end-start));
        writeOutput((1 & sim_time) == 1 ? result : temp, grid_rows, grid_cols, ofile);
    }

}