import java.util.Random;

public class Srad {

    public static void usage(String[] args) {
        System.out.println("Usage: java Srad <rows> <cols> <y1> <y2> <x1> <x2> <lamda> <no. of iter>");
        System.out.printf("\t<rows>   - number of rows\n");
        System.out.printf("\t<cols>    - number of cols\n");
        System.out.printf("\t<y1> 	 - y1 value of the speckle\n");
        System.out.printf("\t<y2>      - y2 value of the speckle\n");
        System.out.printf("\t<x1>       - x1 value of the speckle\n");
        System.out.printf("\t<x2>       - x2 value of the speckle\n");
        System.out.printf("\t<lamda>   - lambda (0,1)\n");
        System.out.printf("\t<no. of iter>   - number of iterations\n");
        System.exit(1);
    }

    public static void random_matrix(float[] I, int rows, int cols) {
        Random random = new Random(7); // Seed with 7
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                I[i * cols + j] = random.nextFloat();
            }
        }
    }

    public static void main(String[] args) {
        int size_I, size_R, iter, k, r1 = 0, r2 = 0, c1 = 0, c2 = 0, rows = 0, cols = 0, niter = 10;
        float sum, sum2, tmp, meanROI, varROI, Jc, G2, L, num, den, qsqr, cN, cS, cW, cE, D, lambda = 0, q0sqr = 0;
        int[] iN, iS, jE, jW;
        float[] I, J, dN, dS, dW, dE, c;
        boolean printFlag = true;
        if (args.length == 8) {
            rows = Integer.parseInt(args[0]); //number of rows in the domain
            cols = Integer.parseInt(args[1]); //number of cols in the domain
            if ((rows % 16 != 0) || (cols % 16 != 0)) {
                System.out.printf("rows and cols must be multiples of 16\n");
                System.exit(1);
            }
            r1 = Integer.parseInt(args[2]); //y1 position of the speckle
            r2 = Integer.parseInt(args[3]); //y2 position of the speckle
            c1 = Integer.parseInt(args[4]); //x1 position of the speckle
            c2 = Integer.parseInt(args[5]); //x2 position of the speckle
            lambda = Float.parseFloat(args[6]); //Lambda value
            niter = Integer.parseInt(args[7]); //number of iterations
        } else {
            usage(args);
        }

        size_I = cols * rows;
        size_R = (r2 - r1 + 1) * (c2 - c1 + 1);

        I = new float[size_I];
        J = new float[size_I];
        c = new float[size_I];

        iN = new int[rows];
        iS = new int[rows];
        jW = new int[cols];
        jE = new int[cols];

        dN = new float[size_I];
        dS = new float[size_I];
        dW = new float[size_I];
        dE = new float[size_I];

        for (int i = 0; i < rows; i++) {
            iN[i] = i - 1;
            iS[i] = i + 1;
        }
        for (int j = 0; j < cols; j++) {
            jW[j] = j - 1;
            jE[j] = j + 1;
        }
        iN[0] = 0;
        iS[rows - 1] = rows - 1;
        jW[0] = 0;
        jE[cols - 1] = cols - 1;

        System.out.printf("Randomizing the input matrix\n");

        random_matrix(I, rows, cols);

        for (k = 0; k < size_I; k++) {
            J[k] = (float) Math.exp(I[k]);
        }

        System.out.printf("Start the SRAD main loop\n");

        long t1 = System.nanoTime();
        for (iter = 0; iter < niter; iter++) {
            sum = 0;
            sum2 = 0;
            for (int i = r1; i <= r2; i++) {
                for (int j = c1; j <= c2; j++) {
                    tmp = J[i * cols + j];
                    sum += tmp;
                    sum2 += tmp * tmp;
                }
            }
            meanROI = sum / size_R;
            varROI = (sum2 / size_R) - meanROI * meanROI;
            q0sqr = varROI / (meanROI * meanROI);

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    k = i * cols + j;
                    Jc = J[k];
                    // directional derivates
                    dN[k] = J[iN[i] * cols + j] - Jc;
                    dS[k] = J[iS[i] * cols + j] - Jc;
                    dW[k] = J[i * cols + jW[j]] - Jc;
                    dE[k] = J[i * cols + jE[j]] - Jc;
                    G2 = (dN[k] * dN[k] + dS[k] * dS[k] +
                        dW[k] * dW[k] + dE[k] * dE[k]) / (Jc * Jc);

                    L = (dN[k] + dS[k] + dW[k] + dE[k]) / Jc;
                    num = (float) ((0.5 * G2) - ((1.0 / 16.0) * (L * L)));
                    den = (float) (1 + (.25 * L));
                    qsqr = num / (den * den);
                    // diffusion coefficent (equ 33)
                    den = (qsqr - q0sqr) / (q0sqr * (1 + q0sqr));
                    c[k] = (float) (1.0 / (1.0 + den));
                    // saturate diffusion coefficent
                    if (c[k] < 0) {
                        c[k] = 0;
                    } else if (c[k] > 1) {
                        c[k] = 1;
                    }
                }
            }
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    // current index
                    k = i * cols + j;
                    // diffusion coefficent
                    cN = c[k];
                    cS = c[iS[i] * cols + j];
                    cW = c[k];
                    cE = c[i * cols + jE[j]];
                    // divergence (equ 58)
                    D = cN * dN[k] + cS * dS[k] + cW * dW[k] + cE * dE[k];
                    // image update (equ 61)
                    J[k] = (float) (J[k] + 0.25 * lambda * D);
                }
            }
        }
        long t2 = System.nanoTime();
        System.out.println("Execution Time: " + ((t2-t1)/1_000_000_000.0)  + " seconds");

        if (printFlag == true){
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    System.out.printf("%.5f ", J[i * cols + j]);
                }
                System.out.printf("\n");
            }
        }
        System.out.printf("Computation Done\n");
    }
}

