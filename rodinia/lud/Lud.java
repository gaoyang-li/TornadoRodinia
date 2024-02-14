package uk.ac.manchester.tornado.examples.rodinia.lud;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble;
import java.io.File;
import java.util.Scanner;

public class Lud {

    static int matrix_dim = 0;
    static int do_verify = 1;

    public static void lud_diagonal(DoubleArray a, int size, int offset) {
        int i, j, k;
        for (i = 0; i < 16; i++) {
            for (j = i; j < 16; j++) {
                for (k = 0; k < i; k++) {
                    a.set(offset * size + i * size + j + offset, a.get(offset * size + i * size + j + offset) - a.get(offset * size + i * size + k + offset) * a.get(offset * size + k * size + j + offset));
                }
            }
            double temp = 1 / a.get(offset * size + i * size + i + offset);
            for (j = i + 1; j < 16; j++) {
                for (k = 0; k < i; k++) {
                    a.set(offset * size + j * size + i + offset, a.get(offset * size + j * size + i + offset) - a.get(offset * size + j * size + k + offset) * a.get(offset * size + k * size + i + offset));
                }
                a.set(offset * size + j * size + i + offset, a.get(offset * size + j * size + i + offset) * temp);
            }
        }
    }

    // implements block LU factorization
    public static void lud(DoubleArray a, int size) {

        //int offset;
        //System.out.println("running OMP on host");
        for (int offset = 0; offset < size - 16; offset += 16) {
            // lu factorization of left-top corner block diagonal matrix
            lud_diagonal(a, size, offset);

            int size_inter = size - offset - 16;
            int chunks_in_inter_row = size_inter / 16;

            // calculate perimeter block matrices
            for (@Parallel int chunk_idx = 0; chunk_idx < chunks_in_inter_row; chunk_idx++) {
                int i, j, k, i_global, j_global, i_here, j_here;
                double sum;
                VectorDouble temp = new VectorDouble(16 * 16);
                for (i = 0; i < 16; i++) {
                    for (j = 0; j < 16; j++) {
                        temp.set(i * 16 + j, a.get(size * (i + offset) + offset + j));
                    }
                }
                i_global = offset;
                j_global = offset;
                // processing top perimeter
                j_global += 16 * (chunk_idx + 1);
                for (j = 0; j < 16; j++) {
                    for (i = 0; i < 16; i++) {
                        sum = 0;
                        for (k = 0; k < i; k++) {
                            sum += temp.get(16 * i + k) * a.get((i_global + k) * size + (j_global + j));
                        }
                        i_here = i_global + i;
                        j_here = j_global + j;
                        a.set(i_here * size + j_here, a.get(i_here * size + j_here) - sum);
                    }
                }
                // processing left perimeter
                j_global = offset;
                i_global += 16 * (chunk_idx + 1);
                for (i = 0; i < 16; i++) {
                    for (j = 0; j < 16; j++) {
                        sum = 0;
                        for (k = 0; k < j; k++) {
                            sum += a.get((i_global + i) * size + (j_global + k)) * temp.get(16 * k + j);
                        }
                        i_here = i_global + i;
                        j_here = j_global + j;
                        a.set(size * i_here + j_here, (a.get(size * i_here + j_here) - sum) / a.get(size * (offset + j) + offset + j));
                    }
                }
            }
            // update interior block matrices
            int chunks_per_inter = chunks_in_inter_row * chunks_in_inter_row;
            for (@Parallel int chunk_idx = 0; chunk_idx < chunks_per_inter; chunk_idx++) {
                int i, j, k, i_global, j_global;
                VectorDouble temp_top = new VectorDouble(16 * 16);
                VectorDouble temp_left = new VectorDouble(16 * 16);
                VectorDouble sum = new VectorDouble(16);

                i_global = offset + 16 * (1 + chunk_idx / chunks_in_inter_row);
                j_global = offset + 16 * (1 + chunk_idx % chunks_in_inter_row);

                for (i = 0; i < 16; i++) {
                    for (j = 0; j < 16; j++) {
                        temp_top.set(i * 16 + j, a.get(size * (i + offset) + j + j_global));
                        temp_left.set(i * 16 + j, a.get(size * (i + i_global) + offset + j));
                    }
                }

                for (i = 0; i < 16; i++) {
                    for (k = 0; k < 16; k++) {
                        for (j = 0; j < 16; j++) {
                            sum.set(j, sum.get(j) + temp_left.get(16 * i + k) * temp_top.get(16 * k + j));
                        }
                    }
                    for (j = 0; j < 16; j++) {
                        a.set((i + i_global) * size + (j + j_global), a.get((i + i_global) * size + (j + j_global)) - sum.get(j));
                        sum.set(j, 0);
                    }
                }
            }
        }

        lud_diagonal(a, size, size-16);
    }

    public static void matrix_duplicate(DoubleArray src, VectorDouble dst) {
        for (int i = 0; i < matrix_dim * matrix_dim; i++) {
            dst.set(i, src.get(i));
        }
    }

    public static void lud_verify(VectorDouble m, DoubleArray lu) {
        VectorDouble tmp = new VectorDouble(matrix_dim * matrix_dim);
        for (int i = 0; i < matrix_dim; i++) {
            for (int j = 0; j < matrix_dim; j++) {
                double sum = 0;
                double l, u;
                for (int k = 0; k <= Math.min(i, j); k++) {
                    if (i == k) {
                        l = 1;
                    } else {
                        l = lu.get(i * matrix_dim + k);
                    }
                    u = lu.get(k * matrix_dim + j);
                    sum += l * u;
                }
                tmp.set(i * matrix_dim + j, sum);
            }
        }
        for (int i = 0; i < matrix_dim; i++) {
            for (int j = 0; j < matrix_dim; j++) {
                if (Math.abs(m.get(i * matrix_dim + j) - tmp.get(i * matrix_dim + j)) > 0.0001) {
                    System.out.printf("dismatch at (%d, %d): (o)%f (n)%f\n", i, j, m.get(i * matrix_dim + j), tmp.get(i * matrix_dim + j));
                }
            }
        }
    }

    public static void create_matrix(DoubleArray mp, int size) {
        double lambda = -0.001;
        VectorDouble coe = new VectorDouble(2 * size - 1);
        double coe_i = 0.0;

        for (int i = 0; i < size; i++) {
            coe_i = 10 * Math.exp(lambda * i);
            int j = size - 1 + i;
            coe.set(j, coe_i);
            j = size - 1 - i;
            coe.set(j, coe_i);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                mp.set(i * size + j, coe.get(size - 1 - i + j));
            }
        }
    }

    public static void create_matrix_from_file(DoubleArray mp, String input_file) {
        try {
            Scanner scanner = new Scanner(new File(input_file));
            scanner.nextInt();
            for (int i = 0; i < matrix_dim * matrix_dim; i++) {
                mp.set(i, scanner.nextDouble());
            }
            scanner.close();
        } catch (Exception e) {
            System.err.println("failed to create matrix from file");
            System.exit(1);
        }
    }

    public static void print_matrix(DoubleArray m) {
        for (int i = 0; i < matrix_dim; i++) {
            for (int j = 0; j < matrix_dim; j++)
                System.out.printf("%f ", m.get(i * matrix_dim + j));
            System.out.println();
        }
    }

    public static void main(String[] args) {

        String input_file = null;

        if (args[0].equals("i")) {
            input_file = args[1];
        } else if (args[0].equals("s")) {
            matrix_dim = Integer.parseInt(args[1]);
            if (matrix_dim % 16 != 0){
                System.err.println("matrix dimension should be a multiple of 16");
                System.exit(1);
            }
            System.out.printf("Generate input matrix internally, size =%d\n", matrix_dim);
        } else {
            System.err.println("Usage: java Lud [s matrix_size|i input_file]");
            System.exit(1);
        }

        DoubleArray m;
        VectorDouble mm;

        if (input_file != null) {
            try {
                Scanner scanner = new Scanner(new File(input_file));
                System.out.printf("Reading matrix from file %s\n", input_file);
                matrix_dim = scanner.nextInt();
                scanner.close();
            } catch (Exception e) {
                System.err.println("failed to open file");
                System.exit(1);
            }
            m = new DoubleArray(matrix_dim * matrix_dim);
            mm = new VectorDouble(matrix_dim * matrix_dim);
            create_matrix_from_file(m, input_file);
        } else {
            m = new DoubleArray(matrix_dim * matrix_dim);
            mm = new VectorDouble(matrix_dim * matrix_dim);
            create_matrix(m, matrix_dim);
        }

        if (do_verify == 1) {
            System.out.println("Before LUD");
            print_matrix(m);
            matrix_duplicate(m, mm);
        }

        long startTime = System.nanoTime();
        System.out.println("running on host");
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, m, matrix_dim)
                .task("t1", Lud::lud, m, matrix_dim)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, m);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        executor1.execute();
        //lud(m, matrix_dim);
        long endTime = System.nanoTime();
        System.out.println("Time consumed(s): " + (double)(endTime - startTime) / 1000000000);

        if (do_verify == 1) {
            System.out.println("After LUD");
            print_matrix(m);
            System.out.println(">>>Verify<<<");
            lud_verify(mm, m);
        }
    }

}