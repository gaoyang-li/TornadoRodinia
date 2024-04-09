import java.io.PrintWriter;
import java.util.Random;

public class Needle {
    final static int BLOCK_SIZE = 16;
    final static int LIMIT = -999;
    static int[][] blosum62 = {
        { 4, -1, -2, -2,  0, -1, -1,  0, -2, -1, -1, -1, -1, -2, -1,  1,  0, -3, -2,  0, -2, -1,  0, -4},
        {-1,  5,  0, -2, -3,  1,  0, -2,  0, -3, -2,  2, -1, -3, -2, -1, -1, -3, -2, -3, -1,  0, -1, -4},
        {-2,  0,  6,  1, -3,  0,  0,  0,  1, -3, -3,  0, -2, -3, -2,  1,  0, -4, -2, -3,  3,  0, -1, -4},
        {-2, -2,  1,  6, -3,  0,  2, -1, -1, -3, -4, -1, -3, -3, -1,  0, -1, -4, -3, -3,  4,  1, -1, -4},
        { 0, -3, -3, -3,  9, -3, -4, -3, -3, -1, -1, -3, -1, -2, -3, -1, -1, -2, -2, -1, -3, -3, -2, -4},
        {-1,  1,  0,  0, -3,  5,  2, -2,  0, -3, -2,  1,  0, -3, -1,  0, -1, -2, -1, -2,  0,  3, -1, -4},
        {-1,  0,  0,  2, -4,  2,  5, -2,  0, -3, -3,  1, -2, -3, -1,  0, -1, -3, -2, -2,  1,  4, -1, -4},
        { 0, -2,  0, -1, -3, -2, -2,  6, -2, -4, -4, -2, -3, -3, -2,  0, -2, -2, -3, -3, -1, -2, -1, -4},
        {-2,  0,  1, -1, -3,  0,  0, -2,  8, -3, -3, -1, -2, -1, -2, -1, -2, -2,  2, -3,  0,  0, -1, -4},
        {-1, -3, -3, -3, -1, -3, -3, -4, -3,  4,  2, -3,  1,  0, -3, -2, -1, -3, -1,  3, -3, -3, -1, -4},
        {-1, -2, -3, -4, -1, -2, -3, -4, -3,  2,  4, -2,  2,  0, -3, -2, -1, -2, -1,  1, -4, -3, -1, -4},
        {-1,  2,  0, -1, -3,  1,  1, -2, -1, -3, -2,  5, -1, -3, -1,  0, -1, -3, -2, -2,  0,  1, -1, -4},
        {-1, -1, -2, -3, -1,  0, -2, -3, -2,  1,  2, -1,  5,  0, -2, -1, -1, -1, -1,  1, -3, -1, -1, -4},
        {-2, -3, -3, -3, -2, -3, -3, -3, -1,  0,  0, -3,  0,  6, -4, -2, -2,  1,  3, -1, -3, -3, -1, -4},
        {-1, -2, -2, -1, -3, -1, -1, -2, -2, -3, -3, -1, -2, -4,  7, -1, -1, -4, -3, -2, -2, -1, -2, -4},
        { 1, -1,  1,  0, -1,  0,  0,  0, -1, -2, -2,  0, -1, -2, -1,  4,  1, -3, -2, -2,  0,  0,  0, -4},
        { 0, -1,  0, -1, -1, -1, -1, -2, -2, -1, -1, -1, -1, -2, -1,  1,  5, -2, -2,  0, -1, -1,  0, -4},
        {-3, -3, -4, -4, -2, -2, -3, -2, -2, -3, -2, -3, -1,  1, -4, -3, -2, 11,  2, -3, -4, -3, -2, -4},
        {-2, -2, -2, -3, -2, -1, -2, -3,  2, -1, -1, -2, -1,  3, -3, -2, -2,  2,  7, -1, -3, -2, -1, -4},
        { 0, -3, -3, -3, -1, -2, -2, -3, -3,  3,  1, -2,  1, -1, -2, -2,  0, -3, -1,  4, -3, -2, -1, -4},
        {-2, -1,  3,  4, -3,  0,  1, -1,  0, -3, -4,  0, -3, -3, -2,  0, -1, -4, -3, -3,  4,  1, -1, -4},
        {-1,  0,  0,  1, -3,  3,  4, -2,  0, -3, -3,  1, -1, -3, -1,  0, -1, -3, -2, -2,  1,  4, -1, -4},
        { 0, -1, -1, -1, -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2,  0,  0, -2, -1, -1, -1, -1, -1, -4},
        {-4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4,  1}
    };

    public static int maximum(int a, int b, int c){
        return Math.max(a, Math.max(b, c));
    }

    public static void usage(String[] args) {
        System.out.println("Usage: java Needle <max_rows/max_cols> <penalty>\n");
        System.out.println("\t<dimension>      - x and y dimensions\n");
        System.out.println("\t<penalty>        - penalty(positive integer)\n");
        System.exit(1);
    }

    public static void nw_optimized(int[] input_itemsets, int[] output_itemsets, int[] referrence, int max_rows, int max_cols, int penalty) {
        //int transfer_size = max_rows * max_cols;
        for (int blk = 1; blk <= (max_cols - 1) / BLOCK_SIZE; blk++) {
            for (int b_index_x = 0; b_index_x < blk; ++b_index_x) {
                int b_index_y = blk - 1 - b_index_x;
                int[] input_itemsets_l = new int[(BLOCK_SIZE + 1) * (BLOCK_SIZE + 1)];
                int[] reference_l = new int[BLOCK_SIZE * BLOCK_SIZE];

                // Copy referrence to local memory
                for (int i = 0; i < BLOCK_SIZE; ++i) {
                    for (int j = 0; j < BLOCK_SIZE; ++j) {
                        reference_l[i * BLOCK_SIZE + j] = referrence[max_cols * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1];
                    }
                }

                // Copy input_itemsets to local memory
                for (int i = 0; i < BLOCK_SIZE + 1; ++i) {
                    for (int j = 0; j < BLOCK_SIZE + 1; ++j) {
                        input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = input_itemsets[max_cols * (b_index_y * BLOCK_SIZE + i) + b_index_x * BLOCK_SIZE + j];
                    }
                }

                // Compute
                for (int i = 1; i < BLOCK_SIZE + 1; ++i) {
                    for (int j = 1; j < BLOCK_SIZE + 1; ++j) {
                        input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = maximum(input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j - 1] + reference_l[(i - 1) * BLOCK_SIZE + j - 1],
                                input_itemsets_l[i * (BLOCK_SIZE + 1) + j - 1] - penalty,
                                input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j] - penalty);
                    }
                }

                // Copy results to global memory
                for (int i = 0; i < BLOCK_SIZE; ++i) {
                    for (int j = 0; j < BLOCK_SIZE; ++j) {
                        input_itemsets[max_cols * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1] = input_itemsets_l[(i + 1) * (BLOCK_SIZE + 1) + j + 1];
                    }
                }
            }
        }
        System.out.println("Processing bottom-right matrix");

        for (int blk = 2; blk <= (max_cols - 1) / BLOCK_SIZE; blk++) {
            for (int b_index_x = blk - 1; b_index_x < (max_cols - 1) / BLOCK_SIZE; ++b_index_x) {
                int b_index_y = (max_cols - 1) / BLOCK_SIZE + blk - 2 - b_index_x;
                int[] input_itemsets_l = new int[(BLOCK_SIZE + 1) * (BLOCK_SIZE + 1)];
                int[] reference_l = new int[BLOCK_SIZE * BLOCK_SIZE];

                // Copy referrence to local memory
                for (int i = 0; i < BLOCK_SIZE; ++i) {
                    for (int j = 0; j < BLOCK_SIZE; ++j) {
                        reference_l[i * BLOCK_SIZE + j] = referrence[max_cols * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1];
                    }
                }

                // Copy input_itemsets to local memory
                for (int i = 0; i < BLOCK_SIZE + 1; ++i) {
                    for (int j = 0; j < BLOCK_SIZE + 1; ++j) {
                        input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = input_itemsets[max_cols * (b_index_y * BLOCK_SIZE + i) + b_index_x * BLOCK_SIZE + j];
                    }
                }

                // Compute
                for (int i = 1; i < BLOCK_SIZE + 1; ++i) {
                    for (int j = 1; j < BLOCK_SIZE + 1; ++j) {
                        input_itemsets_l[i * (BLOCK_SIZE + 1) + j] = maximum(input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j - 1] + reference_l[(i - 1) * BLOCK_SIZE + j - 1],
                                input_itemsets_l[i * (BLOCK_SIZE + 1) + j - 1] - penalty,
                                input_itemsets_l[(i - 1) * (BLOCK_SIZE + 1) + j] - penalty);
                    }
                }

                // Copy results to global memory
                for (int i = 0; i < BLOCK_SIZE; ++i) {
                    for (int j = 0; j < BLOCK_SIZE; ++j) {
                        input_itemsets[max_cols * (b_index_y * BLOCK_SIZE + i + 1) + b_index_x * BLOCK_SIZE + j + 1] = input_itemsets_l[(i + 1) * (BLOCK_SIZE + 1) + j + 1];
                    }
                }
            }
        }
    }

    public static void runTest(String[] args){
        int max_rows = 0;
        int max_cols = 0;
        int penalty = 0;
        //int omp_num_threads = 0;
        int[] reference = new int[max_rows * max_cols];
        int[] input_itemsets = new int[max_rows * max_cols];
        int[] output_itemsets = new int[max_rows * max_cols];

        // the lengths of the two sequences should be able to divided by 16.
        // And at current stage  max_rows needs to equal max_cols
        if (args.length == 2) {
            max_rows = Integer.parseInt(args[0]);
            max_cols = Integer.parseInt(args[0]);
            penalty = Integer.parseInt(args[1]);
            //omp_num_threads = Integer.parseInt(args[2]);
        }
        else {
            usage(args);
        }
        max_rows = max_rows + 1;
        max_cols = max_cols + 1;
        reference = new int[max_rows * max_cols];
        input_itemsets = new int[max_rows * max_cols];
        output_itemsets = new int[max_rows * max_cols];

        for (int i = 0; i < max_cols; i++) {
            for (int j = 0; j < max_rows; j++) {
                input_itemsets[i * max_cols + j] = 0;
            }
        }

        System.out.println("Start Needleman-Wunsch");

        Random rand = new Random(7);
        for (int i = 1; i < max_rows; i++) { //please define your own sequence.
            input_itemsets[i * max_cols] = rand.nextInt(10);
        }
        for (int j = 1; j < max_cols; j++) { //please define your own sequence.
            input_itemsets[j] = rand.nextInt(10);
        }

        for (int i = 1; i < max_cols; i++) {
            for (int j = 1; j < max_rows; j++) {
                reference[i * max_cols + j] = blosum62[input_itemsets[i * max_cols]][input_itemsets[j]];
            }
        }

        for (int i = 1; i < max_rows; i++){
            input_itemsets[i * max_cols] = -i * penalty;
        }
        for (int j = 1; j < max_cols; j++){
            input_itemsets[j] = -j * penalty;
        }

        //Compute top-left matrix
        //System.out.println("Num of threads: " + omp_num_threads);
        System.out.println("Processing top-left matrix");

        long startTime = System.nanoTime();
        nw_optimized(input_itemsets, output_itemsets, reference, max_rows, max_cols, penalty);
        long endTime = System.nanoTime();
        System.out.println("Compute time: " + ((endTime - startTime) / 1_000_000_000.0));

        try {
            PrintWriter writer = new PrintWriter("result.txt");
            int i = max_rows - 2;
            int j = max_cols - 2;
            for (i = max_rows - 2, j = max_cols - 2; i >= 0 && j >= 0;) {
                int nw = 0;
                int n = 0;
                int w = 0;
                int traceback = 0;
                if (i == max_rows - 2 && j == max_rows - 2){
                    writer.print(input_itemsets[i * max_cols + j] + " ");
                }
                if (i == 0 && j == 0){
                    break;
                }
                if (i > 0 && j > 0) {
                    nw = input_itemsets[(i - 1) * max_cols + j - 1];
                    w = input_itemsets[i * max_cols + j - 1];
                    n = input_itemsets[(i - 1) * max_cols + j];
                } else if (i == 0) {
                    nw = n = LIMIT; 
                    w = input_itemsets[i * max_cols + j - 1];
                } else if (j == 0) {
                    nw = w = LIMIT;
                    n = input_itemsets[(i - 1) * max_cols + j];
                } else {}

                //traceback = maximum(nw, w, n);
                int new_nw = nw + reference[i * max_cols + j];
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
                    continue;
                } else if (traceback == w) {
                    j--;
                    continue;
                } else if (traceback == n) {
                    i--;
                    continue;
                }
            }
            writer.close();
        } catch (Exception e) {
            System.out.println("Error writing to result.txt");
        }
    }

    public static void main(String[] args){
        runTest(args);
    }
}


