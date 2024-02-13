package uk.ac.manchester.tornado.examples.rodinia.nw;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;

import java.io.*;
import java.util.Random;
import java.util.Arrays;

public class Needle {
    final static int BLOCK_SIZE = 16;
    final static int LIMIT = -999;
    static VectorInt reference;
    static VectorInt input_itemsets;
    static int[][] blosum62 = {
            {
                    4,
                    -1,
                    -2,
                    -2,
                    0,
                    -1,
                    -1,
                    0,
                    -2,
                    -1,
                    -1,
                    -1,
                    -1,
                    -2,
                    -1,
                    1,
                    0,
                    -3,
                    -2,
                    0,
                    -2,
                    -1,
                    0,
                    -4
            },
            {
                    -1,
                    5,
                    0,
                    -2,
                    -3,
                    1,
                    0,
                    -2,
                    0,
                    -3,
                    -2,
                    2,
                    -1,
                    -3,
                    -2,
                    -1,
                    -1,
                    -3,
                    -2,
                    -3,
                    -1,
                    0,
                    -1,
                    -4
            },
            {
                    -2,
                    0,
                    6,
                    1,
                    -3,
                    0,
                    0,
                    0,
                    1,
                    -3,
                    -3,
                    0,
                    -2,
                    -3,
                    -2,
                    1,
                    0,
                    -4,
                    -2,
                    -3,
                    3,
                    0,
                    -1,
                    -4
            },
            {
                    -2,
                    -2,
                    1,
                    6,
                    -3,
                    0,
                    2,
                    -1,
                    -1,
                    -3,
                    -4,
                    -1,
                    -3,
                    -3,
                    -1,
                    0,
                    -1,
                    -4,
                    -3,
                    -3,
                    4,
                    1,
                    -1,
                    -4
            },
            {
                    0,
                    -3,
                    -3,
                    -3,
                    9,
                    -3,
                    -4,
                    -3,
                    -3,
                    -1,
                    -1,
                    -3,
                    -1,
                    -2,
                    -3,
                    -1,
                    -1,
                    -2,
                    -2,
                    -1,
                    -3,
                    -3,
                    -2,
                    -4
            },
            {
                    -1,
                    1,
                    0,
                    0,
                    -3,
                    5,
                    2,
                    -2,
                    0,
                    -3,
                    -2,
                    1,
                    0,
                    -3,
                    -1,
                    0,
                    -1,
                    -2,
                    -1,
                    -2,
                    0,
                    3,
                    -1,
                    -4
            },
            {
                    -1,
                    0,
                    0,
                    2,
                    -4,
                    2,
                    5,
                    -2,
                    0,
                    -3,
                    -3,
                    1,
                    -2,
                    -3,
                    -1,
                    0,
                    -1,
                    -3,
                    -2,
                    -2,
                    1,
                    4,
                    -1,
                    -4
            },
            {
                    0,
                    -2,
                    0,
                    -1,
                    -3,
                    -2,
                    -2,
                    6,
                    -2,
                    -4,
                    -4,
                    -2,
                    -3,
                    -3,
                    -2,
                    0,
                    -2,
                    -2,
                    -3,
                    -3,
                    -1,
                    -2,
                    -1,
                    -4
            },
            {
                    -2,
                    0,
                    1,
                    -1,
                    -3,
                    0,
                    0,
                    -2,
                    8,
                    -3,
                    -3,
                    -1,
                    -2,
                    -1,
                    -2,
                    -1,
                    -2,
                    -2,
                    2,
                    -3,
                    0,
                    0,
                    -1,
                    -4
            },
            {
                    -1,
                    -3,
                    -3,
                    -3,
                    -1,
                    -3,
                    -3,
                    -4,
                    -3,
                    4,
                    2,
                    -3,
                    1,
                    0,
                    -3,
                    -2,
                    -1,
                    -3,
                    -1,
                    3,
                    -3,
                    -3,
                    -1,
                    -4
            },
            {
                    -1,
                    -2,
                    -3,
                    -4,
                    -1,
                    -2,
                    -3,
                    -4,
                    -3,
                    2,
                    4,
                    -2,
                    2,
                    0,
                    -3,
                    -2,
                    -1,
                    -2,
                    -1,
                    1,
                    -4,
                    -3,
                    -1,
                    -4
            },
            {
                    -1,
                    2,
                    0,
                    -1,
                    -3,
                    1,
                    1,
                    -2,
                    -1,
                    -3,
                    -2,
                    5,
                    -1,
                    -3,
                    -1,
                    0,
                    -1,
                    -3,
                    -2,
                    -2,
                    0,
                    1,
                    -1,
                    -4
            },
            {
                    -1,
                    -1,
                    -2,
                    -3,
                    -1,
                    0,
                    -2,
                    -3,
                    -2,
                    1,
                    2,
                    -1,
                    5,
                    0,
                    -2,
                    -1,
                    -1,
                    -1,
                    -1,
                    1,
                    -3,
                    -1,
                    -1,
                    -4
            },
            {
                    -2,
                    -3,
                    -3,
                    -3,
                    -2,
                    -3,
                    -3,
                    -3,
                    -1,
                    0,
                    0,
                    -3,
                    0,
                    6,
                    -4,
                    -2,
                    -2,
                    1,
                    3,
                    -1,
                    -3,
                    -3,
                    -1,
                    -4
            },
            {
                    -1,
                    -2,
                    -2,
                    -1,
                    -3,
                    -1,
                    -1,
                    -2,
                    -2,
                    -3,
                    -3,
                    -1,
                    -2,
                    -4,
                    7,
                    -1,
                    -1,
                    -4,
                    -3,
                    -2,
                    -2,
                    -1,
                    -2,
                    -4
            },
            {
                    1,
                    -1,
                    1,
                    0,
                    -1,
                    0,
                    0,
                    0,
                    -1,
                    -2,
                    -2,
                    0,
                    -1,
                    -2,
                    -1,
                    4,
                    1,
                    -3,
                    -2,
                    -2,
                    0,
                    0,
                    0,
                    -4
            },
            {
                    0,
                    -1,
                    0,
                    -1,
                    -1,
                    -1,
                    -1,
                    -2,
                    -2,
                    -1,
                    -1,
                    -1,
                    -1,
                    -2,
                    -1,
                    1,
                    5,
                    -2,
                    -2,
                    0,
                    -1,
                    -1,
                    0,
                    -4
            },
            {
                    -3,
                    -3,
                    -4,
                    -4,
                    -2,
                    -2,
                    -3,
                    -2,
                    -2,
                    -3,
                    -2,
                    -3,
                    -1,
                    1,
                    -4,
                    -3,
                    -2,
                    11,
                    2,
                    -3,
                    -4,
                    -3,
                    -2,
                    -4
            },
            {
                    -2,
                    -2,
                    -2,
                    -3,
                    -2,
                    -1,
                    -2,
                    -3,
                    2,
                    -1,
                    -1,
                    -2,
                    -1,
                    3,
                    -3,
                    -2,
                    -2,
                    2,
                    7,
                    -1,
                    -3,
                    -2,
                    -1,
                    -4
            },
            {
                    0,
                    -3,
                    -3,
                    -3,
                    -1,
                    -2,
                    -2,
                    -3,
                    -3,
                    3,
                    1,
                    -2,
                    1,
                    -1,
                    -2,
                    -2,
                    0,
                    -3,
                    -1,
                    4,
                    -3,
                    -2,
                    -1,
                    -4
            },
            {
                    -2,
                    -1,
                    3,
                    4,
                    -3,
                    0,
                    1,
                    -1,
                    0,
                    -3,
                    -4,
                    0,
                    -3,
                    -3,
                    -2,
                    0,
                    -1,
                    -4,
                    -3,
                    -3,
                    4,
                    1,
                    -1,
                    -4
            },
            {
                    -1,
                    0,
                    0,
                    1,
                    -3,
                    3,
                    4,
                    -2,
                    0,
                    -3,
                    -3,
                    1,
                    -1,
                    -3,
                    -1,
                    0,
                    -1,
                    -3,
                    -2,
                    -2,
                    1,
                    4,
                    -1,
                    -4
            },
            {
                    0,
                    -1,
                    -1,
                    -1,
                    -2,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -2,
                    0,
                    0,
                    -2,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -4
            },
            {
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    -4,
                    1
            }
    };

    public static void printInput() {
        System.out.print("lgy:input:");
        for (int i = 0; i < 500; i++) {
            System.out.print(input_itemsets.get(i) + " ");
        }
        System.out.println();
    }

    public static void printRef() {
        System.out.print("lgy:ref:");
        for (int i = 0; i < 500; i++) {
            System.out.print(reference.get(i) + " ");
        }
        System.out.println();
    }

    public static int maximum(int a, int b, int c) {
        return Math.max(a, Math.max(b, c));
    }

    public static void usage(String[] args) {
        System.out.println("Usage: java Needle <max_rows/max_cols> <penalty>\n");
        System.out.println("\t<dimension>      - x and y dimensions\n");
        System.out.println("\t<penalty>        - penalty(positive integer)\n");
        //System.out.println("\t<num_threads>    - no. of threads\n");
        System.exit(1);
    }

    public static void parallel1(VectorInt input_itemsets, VectorInt reference, VectorInt paras, VectorInt blk) {
        for (@Parallel int b_index_x = 0; b_index_x < blk.get(0); ++b_index_x) {
            int b_index_y = blk.get(0) - 1 - b_index_x;
            int[] input_itemsets_l = new int[(BLOCK_SIZE + 1) * (BLOCK_SIZE + 1)];
            int[] reference_l = new int[BLOCK_SIZE * BLOCK_SIZE];

            // Copy referrence to local memory
            for (int i = 0; i < BLOCK_SIZE; ++i) {
                for (int j = 0; j < BLOCK_SIZE; ++j) {
                    reference_l[i * BLOCK_SIZE + j] = reference.get(paras.get(1) * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1);
                }
            }

            // Copy input_itemsets to local memory
            for (int i = 0; i < BLOCK_SIZE + 1; ++i) {
                for (int j = 0; j < BLOCK_SIZE + 1; ++j) {
                    input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = input_itemsets.get(paras.get(1) * (b_index_y * BLOCK_SIZE + i) + b_index_x * BLOCK_SIZE + j);
                }
            }

            // Compute
            for (int i = 1; i < BLOCK_SIZE + 1; ++i) {
                for (int j = 1; j < BLOCK_SIZE + 1; ++j) {
                    input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = maximum(input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j - 1] + reference_l[(i - 1) * BLOCK_SIZE + j - 1],
                            input_itemsets_l[i * (BLOCK_SIZE + 1) + j - 1] - paras.get(2),
                            input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j] - paras.get(2));
                }
            }

            // Copy results to global memory
            for (int i = 0; i < BLOCK_SIZE; ++i) {
                for (int j = 0; j < BLOCK_SIZE; ++j) {
                    input_itemsets.set(paras.get(1) * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1, input_itemsets_l[(i + 1) * (BLOCK_SIZE + 1) + j + 1]);
                }
            }
        }
    }

    public static void parallel2(VectorInt input_itemsets, VectorInt reference, VectorInt paras, VectorInt blk) {
        for (@Parallel int b_index_x = 0; b_index_x < ((paras.get(1) - 1) / BLOCK_SIZE) - (blk.get(0) - 1); ++b_index_x) {
            int b_index_y = (paras.get(1) - 1) / BLOCK_SIZE + blk.get(0) - 2 - (b_index_x + blk.get(0) - 1);
            int[] input_itemsets_l = new int[(BLOCK_SIZE + 1) * (BLOCK_SIZE + 1)];
            int[] reference_l = new int[BLOCK_SIZE * BLOCK_SIZE];

            // Copy referrence to local memory
            for (int i = 0; i < BLOCK_SIZE; ++i) {
                for (int j = 0; j < BLOCK_SIZE; ++j) {
                    reference_l[i * BLOCK_SIZE + j] = reference.get(paras.get(1) * (b_index_y * BLOCK_SIZE + i + 1) + (b_index_x + blk.get(0) - 1) * BLOCK_SIZE + j + 1);
                }
            }

            // Copy input_itemsets to local memory
            for (int i = 0; i < BLOCK_SIZE + 1; ++i) {
                for (int j = 0; j < BLOCK_SIZE + 1; ++j) {
                    input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = input_itemsets.get(paras.get(1) * (b_index_y * BLOCK_SIZE + i) + (b_index_x + blk.get(0) - 1) * BLOCK_SIZE + j);
                }
            }

            // Compute
            for (int i = 1; i < BLOCK_SIZE + 1; ++i) {
                for (int j = 1; j < BLOCK_SIZE + 1; ++j) {
                    input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = maximum(input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j - 1] + reference_l[(i - 1) * BLOCK_SIZE + j - 1],
                            input_itemsets_l[i * (BLOCK_SIZE + 1) + j - 1] - paras.get(2),
                            input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j] - paras.get(2));
                }
            }

            // Copy results to global memory
            for (int i = 0; i < BLOCK_SIZE; ++i) {
                for (int j = 0; j < BLOCK_SIZE; ++j) {
                    input_itemsets.set(paras.get(1) * (b_index_y * BLOCK_SIZE + i + 1) + (b_index_x + blk.get(0) - 1) * BLOCK_SIZE + j + 1, input_itemsets_l[(i + 1) * (BLOCK_SIZE + 1) + j + 1]);
                }
            }
        }
    }

    public static void nw_optimized1(VectorInt input_itemsets, VectorInt reference, VectorInt paras) {
        VectorInt blkk = new VectorInt(1);
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input_itemsets, reference, paras, blkk)
                .task("t1", Needle::parallel1, input_itemsets, reference, paras, blkk)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input_itemsets, reference);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        for (int blk = 1; blk <= (paras.get(1) - 1) / BLOCK_SIZE; blk++) {
            blkk.set(0, blk);
            executor1.execute();
            //parallel1(input_itemsets, reference, paras, blkk);
        }
    }

    public static void nw_optimized2(VectorInt input_itemsets, VectorInt reference, VectorInt paras) {
        VectorInt blkk = new VectorInt(1);
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph2 = new TaskGraph("s2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input_itemsets, reference, paras, blkk)
                .task("t2", Needle::parallel2, input_itemsets, reference, paras, blkk)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input_itemsets, reference);
        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
                .withDevice(device);
        for (int blk = 2; blk <= (paras.get(1) - 1) / BLOCK_SIZE; blk++) {
            blkk.set(0, blk);
            executor2.execute();
            //parallel2(input_itemsets, reference, paras, blkk);
        }
    }

    public static void main(String[] args) {
        int max_rows = 0;
        int max_cols = 0;
        int penalty = 0;
        //int omp_num_threads = 0;

        // the lengths of the two sequences should be able to divided by 16.
        // And at current stage  max_rows needs to equal max_cols
        if (args.length == 2) {
            max_rows = Integer.parseInt(args[0]);
            max_cols = Integer.parseInt(args[0]);
            penalty = Integer.parseInt(args[1]);
            //omp_num_threads = Integer.parseInt(args[2]);
        } else {
            usage(args);
        }
        max_rows = max_rows + 1;
        max_cols = max_cols + 1;
        //        VectorInt reference = new VectorInt(max_rows * max_cols);
        //        VectorInt input_itemsets = new VectorInt(max_rows * max_cols);
        reference = new VectorInt(max_rows * max_cols);
        input_itemsets = new VectorInt(max_rows * max_cols);

        VectorInt paras = new VectorInt(3);
        paras.set(0, max_rows);
        paras.set(1, max_cols);
        paras.set(2, penalty);

        for (int i = 0; i < max_cols; i++) {
            for (int j = 0; j < max_rows; j++) {
                input_itemsets.set(i * max_cols + j, 0);
            }
        }

        System.out.println("Start Needleman-Wunsch");

        Random rand = new Random(7);
        for (int i = 1; i < max_rows; i++) { //please define your own sequence.
            input_itemsets.set(i * max_cols, rand.nextInt(10));
        }
        for (int j = 1; j < max_cols; j++) { //please define your own sequence.
            input_itemsets.set(j, rand.nextInt(10));
        }

        for (int i = 1; i < max_cols; i++) {
            for (int j = 1; j < max_rows; j++) {
                reference.set(i * max_cols + j, blosum62[input_itemsets.get(i * max_cols)][input_itemsets.get(j)]);
            }
        }

        for (int i = 1; i < max_rows; i++) {
            input_itemsets.set(i * max_cols, -i * penalty);
        }
        for (int j = 1; j < max_cols; j++) {
            input_itemsets.set(j, -j * penalty);
        }

        //Compute top-left matrix
        //System.out.println("Num of threads: " + omp_num_threads);
        System.out.println("Processing top-left matrix");
        long startTime = System.nanoTime();
        nw_optimized1(input_itemsets, reference, paras);
        nw_optimized2(input_itemsets, reference, paras);
        long endTime = System.nanoTime();
        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);

        try {
            PrintWriter writer = new PrintWriter("tornado-examples/src/main/java/uk/ac/manchester/tornado/examples/rodinia/nw/result.txt");
            int i = max_rows - 2;
            int j = max_cols - 2;
            for (i = max_rows - 2, j = max_cols - 2; i >= 0 && j >= 0;) {
                int nw = 0;
                int n = 0;
                int w = 0;
                int traceback = 0;
                if (i == max_rows - 2 && j == max_rows - 2) {
                    writer.print(input_itemsets.get(i * max_cols + j) + " ");
                }
                if (i == 0 && j == 0) {
                    break;
                }
                if (i > 0 && j > 0) {
                    nw = input_itemsets.get((i - 1) * max_cols + j - 1);
                    w = input_itemsets.get(i * max_cols + j - 1);
                    n = input_itemsets.get((i - 1) * max_cols + j);
                } else if (i == 0) {
                    nw = n = LIMIT;
                    w = input_itemsets.get(i * max_cols + j - 1);
                } else if (j == 0) {
                    nw = w = LIMIT;
                    n = input_itemsets.get((i - 1) * max_cols + j);
                } else {}

                //traceback = maximum(nw, w, n);
                int new_nw = nw + reference.get(i * max_cols + j);
                int new_w = w - penalty;
                int new_n = n - penalty;
                traceback = maximum(new_nw, new_w, new_n);
                if (traceback == new_nw)
                    traceback = nw;
                if (traceback == new_w)
                    traceback = w;
                if (traceback == new_n)
                    traceback = n;
                writer.print(traceback + " ");

                if (traceback == nw) {
                    i--;
                    j--;
                } else if (traceback == w) {
                    j--;
                } else if (traceback == n) {
                    i--;
                }
            }
            writer.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error writing to result.txt");
        }
    }

}