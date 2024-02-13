// make ptx
package uk.ac.manchester.tornado.examples.rodinia.kmeans;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DDouble;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Kmeans {
    final static int RANDOM_MAX = Integer.MAX_VALUE;
    final static double FLT_MAX = 3.40282347e+38;
    static int numAttributes = 0;
    static int numObjects = 0;
    static int nclusters = 5;
    static double threshold = 0.001;

    public static void find_nearest_point(double[] pt, int nfeatures, Matrix2DDouble pts, int npts, IntArray index) {
        double min_dist = FLT_MAX;
        /* find the cluster center id with min distance to pt */
        for (int i = 0; i < npts; i++) {
            double dist = 0;
            for (int j = 0; j < nfeatures; j++) {
                dist += (pt[j] - pts.get(i, j)) * (pt[j] - pts.get(i, j));
            }
            // dist = euclid_dist_2(pt, pts[i], nfeatures); /* no need square root */
            if (dist < min_dist) {
                min_dist = dist;
                index.set(0, i);
            }
        }
    }

    public static void update(Matrix2DDouble new_centers, Matrix2DDouble feature, IntArray index, int nfeatures, int i){
        for (int k = 0; k < nfeatures; k++){
            //new_centers[index[0]][j] = new_centers[index[0]][j] + feature[i][j];
            new_centers.set(index.get(0), k, new_centers.get(index.get(0), k) + feature.get(i, k));
        }
    }


    public static void parallel(Matrix2DDouble feature, int nfeatures, Matrix2DDouble tmp_cluster_centres, int nclusters, IntArray index, IntArray membership, IntArray new_centers_len, Matrix2DDouble new_centers, @Reduce DoubleArray delta, DoubleArray one){
        for (@Parallel int i = 0; i < feature.getNumRows(); i++) {
            /* find the index of nestest cluster centers */
            //find_nearest_point(feature[i], nfeatures, tmp_cluster_centres, nclusters, index);
            double min_dist = FLT_MAX;
            /* find the cluster center id with min distance to pt */
            for (int ii = 0; ii < nclusters; ii++) {
                double dist = 0;
                for (int j = 0; j < nfeatures; j++) {
                    dist += (feature.get(i, j) - tmp_cluster_centres.get(ii, j)) * (feature.get(i, j) - tmp_cluster_centres.get(ii, j));
                }
                // dist = euclid_dist_2(pt, pts[i], nfeatures); /* no need square root */
                if (dist < min_dist) {
                    min_dist = dist;
                    index.set(0, ii);
                }
            }
            /* if membership changes, increase delta by 1 */
            if (membership.get(i) != index.get(0)) {
                //delta.set(0, delta.get(0) + 1.0);//delta += 1.0;
                delta.set(0, delta.get(0) + one.get(0));
            }
            /* assign the membership to object i */
            membership.set(i, index.get(0));
            /* update new cluster centers : sum of objects located within */
            new_centers_len.set(index.get(0), new_centers_len.get(index.get(0)) + 1);
            //update(new_centers, feature, index, nfeatures, i);
            for (int k = 0; k < nfeatures; k++){
                new_centers.set(index.get(0), k, new_centers.get(index.get(0), k) + feature.get(i, k));
            }
        }
    }

    /*----< euclid_dist_2() >----------------------------------------------------*/
    /* multi-dimensional spatial Euclid distance square */
    // public static double euclid_dist_2(double[] pt1,
    //     double[] pt2,
    //     int numdims) {
    //     double ans = 0;

    //     for (int i = 0; i < numdims; i++) {
    //         ans += (pt1[i] - pt2[i]) * (pt1[i] - pt2[i]);
    //     }

    //     return ans;
    // }

    /*----< kmeans_clustering() >---------------------------------------------*/
    public static void kmeans_clustering(Matrix2DDouble feature, int nfeatures, int npoints, int nclusters, double threshold, IntArray membership, Matrix2DDouble tmp_cluster_centres){
        int i, j, n = 0;
        IntArray index = new IntArray(1);
        int loop = 0;
        IntArray new_centers_len;
        DoubleArray delta = new DoubleArray(1);
        Matrix2DDouble new_centers;

        /* allocate space for returning variable clusters[] */
        //tmp_cluster_centres = new double[nclusters][nfeatures];

        /* randomly pick cluster centers */
        for (i = 0; i < nclusters; i++) {
            for (j = 0; j < nfeatures; j++) {
                //tmp_cluster_centres[i][j] = feature[n][j];
                tmp_cluster_centres.set(i, j, feature.get(n, j));
            }
            n++;
        }
        for (i = 0; i < npoints; i++) {
            membership.set(i, -1);
        }
        /* need to initialize new_centers_len and new_centers[0] to all 0 */
        new_centers_len = new IntArray(nclusters);
        new_centers = new Matrix2DDouble(nclusters, nfeatures);//new_centers = new double[nclusters][nfeatures];

        DoubleArray one = new DoubleArray(1);
        one.set(0, 1.0);
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph2 = new TaskGraph("s2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, feature, nfeatures, tmp_cluster_centres, nclusters, index, membership, new_centers_len,  new_centers, delta, one)
                .task("t2", Kmeans::parallel, feature, nfeatures, tmp_cluster_centres, nclusters, index, membership, new_centers_len,  new_centers, delta, one)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, feature, tmp_cluster_centres, index, membership, new_centers_len, new_centers, delta);
        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
                .withDevice(device);

        do {
            delta.set(0, 0.0);
            //parallel(feature, nfeatures, tmp_cluster_centres, nclusters, index, membership, new_centers_len,  new_centers, delta);
            executor2.execute();

            /* replace old cluster centers with new_centers */
            for (i = 0; i < nclusters; i++) {
                for (j = 0; j < nfeatures; j++) {
                    if (new_centers_len.get(i) > 0){
                        //tmp_cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
                        tmp_cluster_centres.set(i, j, new_centers.get(i, j) / new_centers_len.get(i));
                    }
                    //new_centers[i][j] = 0.0; /* set back to 0 */
                    new_centers.set(i, j, 0.0);
                }
                new_centers_len.set(i, 0); /* set back to 0 */
            }

            //delta /= npoints;
        } while (delta.get(0) > threshold);

        //return clusters;
    }

    /*---< cluster() >-----------------------------------------------------------*/
    public static void cluster(int numObjects, /* number of input objects */
                               int numAttributes, /* size of attribute of each object */
                               Matrix2DDouble attributes, /* [numObjects][numAttributes] */
                               int num_nclusters,
                               double threshold, /* in:   */
                               Matrix2DDouble cluster_centres /* out: [best_nclusters][numAttributes] */
    ) {
        IntArray membership;
        //Matrix2DDouble tmp_cluster_centres = new double[nclusters][numAttributes];
        Matrix2DDouble tmp_cluster_centres = new Matrix2DDouble(nclusters, numAttributes);

        membership = new IntArray(numObjects);

        nclusters = num_nclusters;

        kmeans_clustering(attributes, numAttributes, numObjects, nclusters, threshold, membership, tmp_cluster_centres);

        for (int i = 0; i < tmp_cluster_centres.getNumRows(); i++) {
            for (int j = 0; j < cluster_centres.getNumColumns(); j++) {
                //cluster_centres[i][j] = tmp_cluster_centres[i][j];
                cluster_centres.set(i, j, tmp_cluster_centres.get(i, j));
            }
        }
    }

    public static void main(String[] args) {
        // Parse command-line arguments
        if (args.length < 3) {
            System.out.println("Usage: java Kmeans <input_file_name> <num_of_clusters> <threshold_value>");
            System.exit(1);
        }

//        Matrix2DDouble m = new Matrix2DDouble(2, 3);
//        m.set(1, 2, 8.88);
//        if (true){
//            System.out.println("lgy:" + m.get(0,0));
//            System.out.println("lgy:" + m.get(1,2));
//            System.out.println("lgy:" + m.getNumRows());
//            System.exit(1);
//        }

        numAttributes = 0;
        numObjects = 0;
        String filename = args[0];
        nclusters = Integer.parseInt(args[1]);
        threshold = Double.parseDouble(args[2]);

        /* from the input file, get the numAttributes and numObjects ------------*/
        try {
            Scanner scanner = new Scanner(new File(filename));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                numObjects++;
            }
            scanner.close();
        } catch (Exception e) {
            System.out.println("error in opening the input file");
        }

        try {
            Scanner scanner = new Scanner(new File(filename));
            String line = scanner.nextLine();
            String[] nums = line.split(" ");
            numAttributes = nums.length - 1;
            scanner.close();
        } catch (Exception e) {
            System.out.println("error in opening the input file");
        }

        /* allocate space for attributes[] and read attributes of all objects */
        double[] buf = new double[numObjects * numAttributes];
        Matrix2DDouble attributes = new Matrix2DDouble(numObjects, numAttributes);
        try {
            Scanner scanner = new Scanner(new File(filename));
            int i = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] nums = line.split(" ");
                for (int j = 1; j < nums.length; j++) {
                    buf[i] = Double.parseDouble(nums[j]);
                    i++;
                }
            }
            scanner.close();
        } catch (Exception e) {
            System.out.println("error in opening the input file");
        }
        System.out.println("I/O completed");

        int tempIndex = 0;
        for (int i = 0; i < numObjects; i++) {
            for (int j = 0; j < numAttributes; j++) {
                //attributes[i][j] = buf[tempIndex];
                attributes.set(i, j, buf[tempIndex]);
                tempIndex++;
            }
        }

        Matrix2DDouble cluster_centres = new Matrix2DDouble(numObjects, numAttributes);
        cluster(numObjects,
                numAttributes,
                attributes, /* [numObjects][numAttributes] */
                nclusters,
                threshold,
                cluster_centres
        );

        System.out.printf("number of Clusters %d\n", nclusters);
        System.out.printf("number of Attributes %d\n\n", numAttributes);
        System.out.print("Cluster Centers Output\n");
        System.out.print("The first number is cluster number and the following data is arribute value\n");
        System.out.print("=============================================================================\n\n");

        for (int i = 0; i < nclusters; i++) {
            System.out.printf("%d: ", i);
            for (int j = 0; j < numAttributes; j++)
                System.out.printf("%f ", cluster_centres.get(i, j));
            System.out.print("\n\n");
        }
    }
}