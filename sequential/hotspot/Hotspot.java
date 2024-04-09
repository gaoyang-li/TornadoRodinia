import java.io.PrintWriter;
import java.io.File;
import java.util.Scanner;

public class Hotspot {

    public static float get_time() {
        return (float)(System.nanoTime()) / 1000000000;
    }

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

    /* Single iteration of the transient solver in the grid model.
     * advances the solution of the discretized difference equations 
     * by one time step
     */
    public static void single_iteration(float[] result, float[] temp, float[] power, int row, int col, float Cap_1, float Rx_1, float Ry_1, float Rz_1, float step) {
        float delta = 0.0f;
        int r,
        c;
        int chunk;
        int num_chunk = row * col / (BLOCK_SIZE_R * BLOCK_SIZE_C);
        int chunks_in_row = col / BLOCK_SIZE_C;
        int chunks_in_col = row / BLOCK_SIZE_R;

        for (chunk = 0; chunk < num_chunk; ++chunk) {
            int r_start = BLOCK_SIZE_R * (chunk / chunks_in_col);
            int c_start = BLOCK_SIZE_C * (chunk % chunks_in_row);
            int r_end = r_start + BLOCK_SIZE_R > row ? row : r_start + BLOCK_SIZE_R;
            int c_end = c_start + BLOCK_SIZE_C > col ? col : c_start + BLOCK_SIZE_C;

            if (r_start == 0 || c_start == 0 || r_end == row || c_end == col) {
                for (r = r_start; r < r_start + BLOCK_SIZE_R; ++r) {
                    for (c = c_start; c < c_start + BLOCK_SIZE_C; ++c) {
                        /* Corner 1 */
                        if ((r == 0) && (c == 0)) {
                            delta = (Cap_1) * (power[0] + (temp[1] - temp[0]) * Rx_1 + (temp[col] - temp[0]) * Ry_1 + (amb_temp - temp[0]) * Rz_1);
                        }
                        /* Corner 2 */
                        else if ((r == 0) && (c == col - 1)) {
                            delta = (Cap_1) * (power[c] + (temp[c - 1] - temp[c]) * Rx_1 + (temp[c + col] - temp[c]) * Ry_1 + (amb_temp - temp[c]) * Rz_1);
                        }
                        /* Corner 3 */
                        else if ((r == row - 1) && (c == col - 1)) {
                            delta = (Cap_1) * (power[r * col + c] + (temp[r * col + c - 1] - temp[r * col + c]) * Rx_1 + (temp[(r - 1) * col + c] - temp[r * col + c]) * Ry_1 + (amb_temp - temp[r * col + c]) * Rz_1);
                        }
                        /* Corner 4	*/
                        else if ((r == row - 1) && (c == 0)) {
                            delta = (Cap_1) * (power[r * col] + (temp[r * col + 1] - temp[r * col]) * Rx_1 + (temp[(r - 1) * col] - temp[r * col]) * Ry_1 + (amb_temp - temp[r * col]) * Rz_1);
                        }
                        /* Edge 1 */
                        else if (r == 0) {
                            delta = (float) ((Cap_1) * (power[c] + (temp[c + 1] + temp[c - 1] - 2.0 * temp[c]) * Rx_1 + (temp[col + c] - temp[c]) * Ry_1 + (amb_temp - temp[c]) * Rz_1));
                        }
                        /* Edge 2 */
                        else if (c == col - 1) {
                            delta = (float) ((Cap_1) * (power[r * col + c] + (temp[(r + 1) * col + c] + temp[(r - 1) * col + c] - 2.0 * temp[r * col + c]) * Ry_1 + (temp[r * col + c - 1] - temp[r * col + c]) * Rx_1 + (amb_temp - temp[r * col + c]) * Rz_1));
                        }
                        /* Edge 3 */
                        else if (r == row - 1) {
                            delta = (float) ((Cap_1) * (power[r * col + c] + (temp[r * col + c + 1] + temp[r * col + c - 1] - 2.0 * temp[r * col + c]) * Rx_1 + (temp[(r - 1) * col + c] - temp[r * col + c]) * Ry_1 + (amb_temp - temp[r * col + c]) * Rz_1));
                        }
                        /* Edge 4 */
                        else if (c == 0) {
                            delta = (float) ((Cap_1) * (power[r * col] + (temp[(r + 1) * col] + temp[(r - 1) * col] - 2.0 * temp[r * col]) * Ry_1 + (temp[r * col + 1] - temp[r * col]) * Rx_1 + (amb_temp - temp[r * col]) * Rz_1));
                        }
                        result[r * col + c] = temp[r * col + c] + delta;
                    }
                }
                continue;
            }

            for (r = r_start; r < r_start + BLOCK_SIZE_R; ++r) {
                for (c = c_start; c < c_start + BLOCK_SIZE_C; ++c) {
                    result[r * col + c] = (float) (temp[r * col + c] + (Cap_1 * (power[r * col + c] + (temp[(r + 1) * col + c] + temp[(r - 1) * col + c] - 2.0 * temp[r * col + c]) * Ry_1 + (temp[r * col + c + 1] + temp[r * col + c - 1] - 2.0 * temp[r * col + c]) * Rx_1 + (amb_temp - temp[r * col + c]) * Rz_1)));
                }
            }
        }
    }

    /* Transient solver driver routine: simply converts the heat 
     * transfer differential equations to difference equations 
     * and solves the difference equations by iterating
     */
    public static void compute_tran_temp(float[] result, int num_iterations, float[] temp, float[] power, int row, int col) {
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
            float[] r = result;
            float[] t = temp;
            for (i = 0; i < num_iterations; i++) {
                System.out.printf("iteration %d\n", i++);
                single_iteration(r, t, power, row, col, Cap_1, Rx_1, Ry_1, Rz_1, step);
                float[] tmp = t;
                t = r;
                r = tmp;
            }
            result = r;
        }
        System.out.printf("iteration %d\n", i++);
    }

    public static void writeoutput(float[] vect, int grid_rows, int grid_cols, String file) {
        int i,j, index=0;
        try {
            PrintWriter writer = new PrintWriter(file);
            for (i = 0; i < grid_rows; i++) {
                for (j=0; j<grid_cols; j++){
                    writer.printf("%d\t%g\n", index, vect[i*grid_cols+j]);
                    index++;
                }
            }
            writer.close();
            System.out.println("Result stored in " + file);
        } catch (Exception e) {
            System.out.println("Error writing to the output file");
        }
    }

    public static void read_input(float[] vect, int grid_rows, int grid_cols, String file) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(file));
            for (int i=0; i<grid_rows*grid_cols; i++){
                if (!scanner.hasNextDouble()){
                    System.out.println("not enough lines in file");
                    System.exit(1);
                }
                else{
                    vect[i] = scanner.nextFloat();
                }
            }
            scanner.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args){
        int grid_rows, grid_cols, sim_time, i;
        String tfile, pfile, ofile;
        float[] temp;
        float[] power;
        float[] result;
        grid_rows = Integer.parseInt(args[0]);
        grid_cols = Integer.parseInt(args[1]);
        sim_time =  Integer.parseInt(args[2]);
        temp = new float[grid_rows*grid_cols];
        power = new float[grid_rows*grid_cols];
        result = new float[grid_rows*grid_cols];
        tfile = args[3];
        pfile = args[4];
        ofile = args[5];
        read_input(temp, grid_rows, grid_cols, tfile);
        read_input(power, grid_rows, grid_cols, pfile);
        System.out.println("Start computing the transient temperature");
        double start_time = System.nanoTime();
        compute_tran_temp(result, sim_time, temp, power, grid_rows, grid_cols);
        double end_time = System.nanoTime();
        System.out.println("Ending simulation");
        System.out.printf("Total time: %f seconds\n", ((end_time - start_time) / 1_000_000_000.0));
        writeoutput((1&sim_time)==1 ? result : temp, grid_rows, grid_cols, ofile);
    }

    static void usage() {
        System.err.println("Usage: java Hotspot <grid_rows> <grid_cols> <sim_time> <temp_file> <power_file> <output_file>");
        System.exit(1);
    }
}

