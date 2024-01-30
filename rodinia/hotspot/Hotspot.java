package uk.ac.manchester.tornado.examples.rodinia.hotspot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Scanner;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.*;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.examples.rodinia.bfs.BFS;


public class Hotspot {

    public static double get_time() {
        return (double)(System.nanoTime()) / 1000000000;
    }

    static final int BLOCK_SIZE = 16;
    static final int BLOCK_SIZE_C = 16;
    static final int BLOCK_SIZE_R = 16;
    static final int STR_SIZE = 256;

    /* maximum power density possible (say 300W for a 10mm x 10mm chip)	*/
    static final double MAX_PD = 3.0e6;
    /* required precision in degrees	*/
    static final double PRECISION = 0.001;
    static final double SPEC_HEAT_SI = 1.75e6;
    static final int K_SI = 100;
    /* capacitance fitting factor	*/
    static final double FACTOR_CHIP = 0.5;
    //#define NUM_THREAD 4
    /* chip parameters	*/
    static final double t_chip = 0.0005;
    static final double chip_height = 0.016;
    static final double chip_width = 0.016;
    static VectorInt paras = new VectorInt(3);

    /* ambient temperature, assuming no package at all	*/
    static final double amb_temp = 80.0;

    /* Single iteration of the transient solver in the grid model.
     * advances the solution of the discretized difference equations
     * by one time step
     */
    public static void single_iteration(VectorDouble result, VectorDouble temp, VectorDouble power, VectorInt paras, Double4 args) {
        double delta = 0.0;
        //int r, c;
        //int chunk;
        //int num_chunk = paras.getX() * paras.getY() / (BLOCK_SIZE_R * BLOCK_SIZE_C);
        //int chunks_in_row = paras.getY() / BLOCK_SIZE_C;
        //int chunks_in_col = paras.getX() / BLOCK_SIZE_R;

        for (@Parallel int chunk = 0; chunk < paras.get(0) * paras.get(1) / (16 * 16); ++chunk) {
            //int r_start = 16 * (chunk / (paras.get(0) / 16));
            //int c_start = 16 * (chunk % (paras.get(1) / 16));
            int r_end = 16 * (chunk / (paras.get(0) / 16)) + 16 > paras.get(0) ? paras.get(0) : 16 * (chunk / (paras.get(0) / 16)) + 16;
            int c_end = 16 * (chunk % (paras.get(1) / 16)) + 16 > paras.get(1) ? paras.get(1) : 16 * (chunk % (paras.get(1) / 16)) + 16;

            if (16 * (chunk / (paras.get(0) / 16)) == 0 || 16 * (chunk % (paras.get(1) / 16)) == 0 || r_end == paras.get(0) || c_end == paras.get(1)) {
                for (int r = 16 * (chunk / (paras.get(0) / 16)); r < 16 * (chunk / (paras.get(0) / 16)) + 16; ++r) {
                    for (int c = 16 * (chunk % (paras.get(1) / 16)); c < 16 * (chunk % (paras.get(1) / 16)) + 16; ++c) {
                        /* Corner 1 */
                        if ((r == 0) && (c == 0)) {
                            delta = (args.getW()) * (power.get(0) + (temp.get(1) - temp.get(0)) * args.getX() + (temp.get(paras.get(1)) - temp.get(0)) * args.getY() + (amb_temp - temp.get(0)) * args.getZ());
                        }
                        /* Corner 2 */
                        else if ((r == 0) && (c == paras.get(1) - 1)) {
                            delta = (args.getW()) * (power.get(c) + (temp.get(c - 1) - temp.get(c)) * args.getX() + (temp.get(c + paras.get(1)) - temp.get(c)) * args.getY() + (amb_temp - temp.get(c)) * args.getZ());
                        }
                        /* Corner 3 */
                        else if ((r == paras.get(0) - 1) && (c == paras.get(1) - 1)) {
                            delta = (args.getW()) * (power.get(r * paras.get(1) + c) + (temp.get(r * paras.get(1) + c - 1) - temp.get(r * paras.get(1) + c)) * args.getX() + (temp.get((r - 1) * paras.get(1) + c) - temp.get(r * paras.get(1) + c)) * args.getY() + (amb_temp - temp.get(r * paras.get(1) + c)) * args.getZ());
                        }
                        /* Corner 4	*/
                        else if ((r == paras.get(0) - 1) && (c == 0)) {
                            delta = (args.getW()) * (power.get(r * paras.get(1)) + (temp.get(r * paras.get(1) + 1) - temp.get(r * paras.get(1))) * args.getX() + (temp.get((r - 1) * paras.get(1)) - temp.get(r * paras.get(1))) * args.getY() + (amb_temp - temp.get(r * paras.get(1))) * args.getZ());
                        }
                        /* Edge 1 */
                        else if (r == 0) {
                            delta = (args.getW()) * (power.get(c) + (temp.get(c + 1) + temp.get(c - 1) - 2.0 * temp.get(c)) * args.getX() + (temp.get(paras.get(1) + c) - temp.get(c)) * args.getY() + (amb_temp - temp.get(c)) * args.getZ());
                        }
                        /* Edge 2 */
                        else if (c == paras.get(1) - 1) {
                            delta = (args.getW()) * (power.get(r * paras.get(1) + c) + (temp.get((r + 1) * paras.get(1) + c) + temp.get((r - 1) * paras.get(1) + c) - 2.0 * temp.get(r * paras.get(1) + c)) * args.getY() + (temp.get(r * paras.get(1) + c - 1) - temp.get(r * paras.get(1) + c)) * args.getX() + (amb_temp - temp.get(r * paras.get(1) + c)) * args.getZ());
                        }
                        /* Edge 3 */
                        else if (r == paras.get(0) - 1) {
                            delta = (args.getW()) * (power.get(r * paras.get(1) + c) + (temp.get(r * paras.get(1) + c + 1) + temp.get(r * paras.get(1) + c - 1) - 2.0 * temp.get(r * paras.get(1) + c)) * args.getX() + (temp.get((r - 1) * paras.get(1) + c) - temp.get(r * paras.get(1) + c)) * args.getY() + (amb_temp - temp.get(r * paras.get(1) + c)) * args.getZ());
                        }
                        /* Edge 4 */
                        else if (c == 0) {
                            delta = (args.getW()) * (power.get(r * paras.get(1)) + (temp.get((r + 1) * paras.get(1)) + temp.get((r - 1) * paras.get(1)) - 2.0 * temp.get(r * paras.get(1))) * args.getY() + (temp.get(r * paras.get(1) + 1) - temp.get(r * paras.get(1))) * args.getX() + (amb_temp - temp.get(r * paras.get(1))) * args.getZ());
                        }
                        result.set(r * paras.get(1) + c, temp.get(r * paras.get(1) + c) + delta);
                    }
                }
                continue;
            }

            for (int r = 16 * (chunk / (paras.get(0) / 16)); r < 16 * (chunk / (paras.get(0) / 16)) + 16; ++r) {
                for (int c = 16 * (chunk % (paras.get(1) / 16)); c < 16 * (chunk % (paras.get(1) / 16)) + 16; ++c) {
                    result.set(r * paras.get(1) + c, temp.get(r * paras.get(1) + c) + (args.getW() * (power.get(r * paras.get(1) + c) + (temp.get((r + 1) * paras.get(1) + c) + temp.get((r - 1) * paras.get(1) + c) - 2.0 * temp.get(r * paras.get(1) + c)) * args.getY() + (temp.get(r * paras.get(1) + c + 1) + temp.get(r * paras.get(1) + c - 1) - 2.0 * temp.get(r * paras.get(1) + c)) * args.getX() + (amb_temp - temp.get(r * paras.get(1) + c)) * args.getZ())));
                }
            }
        }
        //System.out.println("lgy single" + Arrays.toString(result));
    }

    /* Transient solver driver routine: simply converts the heat
     * transfer differential equations to difference equations
     * and solves the difference equations by iterating
     */
    public static void compute_tran_temp(VectorDouble result, VectorDouble temp, VectorDouble power, VectorInt paras) {
        int i = 0;

        double grid_height = chip_height / paras.get(0);
        double grid_width = chip_width / paras.get(1);

        double Cap = FACTOR_CHIP * SPEC_HEAT_SI * t_chip * grid_width * grid_height;
        double Rx = grid_width / (2.0 * K_SI * t_chip * grid_height);
        double Ry = grid_height / (2.0 * K_SI * t_chip * grid_width);
        double Rz = t_chip / (K_SI * grid_height * grid_width);

        double max_slope = MAX_PD / (FACTOR_CHIP * t_chip * SPEC_HEAT_SI);
        double step = PRECISION / max_slope / 1000.0;

        double Rx_1 = 1.0 / Rx;
        double Ry_1 = 1.0 / Ry;
        double Rz_1 = 1.0 / Rz;
        double Cap_1 = step / Cap;

        Double4 args = new Double4();
        args.setW(Cap_1);
        args.setX(Rx_1);
        args.setY(Ry_1);
        args.setZ(Rz_1);

        System.out.printf("total iterations: %d s\tstep size: %g s\n", paras.get(2), step);
        System.out.printf("Rx: %g\tRy: %g\tRz: %g\tCap: %g\n", Rx, Ry, Rz, Cap);

        //int array_size = paras.getX() * paras.getY();

        //{
            VectorDouble r = result;
            VectorDouble t = temp;
            TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
            for ( i = 0; i < paras.get(2); i++) {
                System.out.printf("iteration %d\n", i++);
                // single_iteration(r, t, power, paras, args);
                TaskGraph taskGraph1 = new TaskGraph("s1")
                        .transferToDevice(DataTransferMode.EVERY_EXECUTION, t, power, paras, args)
                        .task("t1", Hotspot::single_iteration, r, t, power, paras, args)
                        .transferToHost(DataTransferMode.EVERY_EXECUTION, r);
                ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
                TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                        .withDevice(device);
                executor1.execute();
                VectorDouble tmp = t;
                t = r;
                r = tmp;
            }
            //System.out.println("compute r" + Arrays.toString(r));
            //System.out.println("compute t" + Arrays.toString(t));
            //result = r;
        //}
        System.out.printf("iteration %d\n", i++);
    }

    public static void writeOutput(VectorDouble vect, VectorInt paras, String file) {
        int i,j, index=0;
        try {
            PrintWriter writer = new PrintWriter(file);
            for (i = 0; i < paras.get(0); i++) {
                for (j=0; j< paras.get(1); j++){
                    writer.printf("%d\t%f\n", index, vect.get(i*paras.get(1)+j));
                    index++;
                }
            }
            writer.close();
            System.out.println("Result stored in " + file);
        } catch (FileNotFoundException e) {
            System.out.println("Error writing to the output file");
        }
    }

    public static void readInput(VectorDouble vect, VectorInt paras, String file) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(file));
            for (int i=0; i<paras.get(0)*paras.get(1); i++){
                if (!scanner.hasNextDouble()){
                    System.out.println("not enough lines in file");
                    System.exit(1);
                }
                else{
                    vect.set(i, scanner.nextDouble());
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args){
        int grid_rows, grid_cols, sim_time, i;
        String tfile, pfile, ofile;
        VectorDouble temp;
        VectorDouble power;
        VectorDouble result;
        grid_rows = Integer.parseInt(args[0]);
        grid_cols = Integer.parseInt(args[1]);
        sim_time =  Integer.parseInt(args[2]);
        paras.set(0, grid_rows);
        paras.set(1, grid_cols);
        paras.set(2, sim_time);
        temp = new VectorDouble(grid_rows*grid_cols);
        power = new VectorDouble(grid_rows*grid_cols);
        result = new VectorDouble(grid_rows*grid_cols);
        tfile = args[3];
        pfile = args[4];
        ofile = args[5];
        readInput(temp, paras, tfile);
        readInput(power, paras, pfile);
        //System.out.println("lgy temp" + Arrays.toString(temp));
        //System.out.println("lgy power" + Arrays.toString(power));
        compute_tran_temp(result, temp, power, paras);
        //System.out.println("lgy" +  Arrays.toString(result));
        writeOutput((1&sim_time)==1 ? result : temp, paras, ofile);
        //writeOutput(result, grid_rows, grid_cols, ofile);
    }



    static void usage() {
        System.err.println("Usage: Hotspot <grid_rows> <grid_cols> <sim_time> <temp_file> <power_file> <output_file>");
        System.exit(1);
    }
}