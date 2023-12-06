package uk.ac.manchester.tornado.examples.rodinia.kmeans;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.*;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

public class Kmeans {
    final static int RANDOM_MAX = Integer.MAX_VALUE;
    final static double FLT_MAX = 3.40282347e+38;
    static int numAttributes = 0;
    static int numObjects = 0;
    static int nclusters = 5;
    static double threshold = 0.001;

    static Int3 paras; // num_clusters, numObjects, numAttributes

    public static void find_nearest_point(double[] pt, /* [nfeatures] */
                                          double[][] pts, /* [npts][nfeatures] */
                                          VectorInt index
    ) {
        double min_dist = FLT_MAX;
        /* find the cluster center id with min distance to pt */
        for (int i = 0; i < pts.length; i++) {
            double dist = 0;
            for (int j = 0; j < pts[0].length; j++) {
                dist += (pt[j] - pts[i][j]) * (pt[j] - pts[i][j]);
            }

            // dist = euclid_dist_2(pt, pts[i], nfeatures); /* no need square root */
            if (dist < min_dist) {
                min_dist = dist;
                index.set(0, i);
            }
        }
        //return index;
    }

    public static void updateCentres(double[][] feature, /* in: [npoints][nfeatures] */
                                     Int3 paras,
                                     int[] membership,
                                     double[][] tmp_cluster_centres,
                                     VectorInt index,
                                     VectorDouble delta,
                                     double[][] new_centers,
                                     int[] new_centers_len) {
        for (@Parallel int i = 0; i < paras.getY(); i++) {
            /* find the index of nestest cluster centers */
            double min_dist = FLT_MAX;
            /* find the cluster center id with min distance to pt */
            find_nearest_point(feature[i], tmp_cluster_centres, index);
            /* if membership changes, increase delta by 1 */
            if (membership[i] != index.get(0)) {
                delta.set(0, delta.get(0) + 1.0);
            }
            /* assign the membership to object i */
            membership[i] = index.get(0);
            /* update new cluster centers : sum of objects located within */
            new_centers_len[index.get(0)]++;
            for (int j = 0; j < paras.getZ(); j++) {
                new_centers[index.get(0)][j] += feature[i][j];
            }
        }

        /* replace old cluster centers with new_centers */
        for (int i = 0; i < paras.getX(); i++) {
            for (int j = 0; j < paras.getZ(); j++) {
                if (new_centers_len[i] > 0)
                    tmp_cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
                new_centers[i][j] = 0.0; /* set back to 0 */
            }
            new_centers_len[i] = 0; /* set back to 0 */
        }
    }

    /*----< kmeans_clustering() >---------------------------------------------*/
    public static void kmeans_clustering(double[][] feature, /* in: [npoints][nfeatures] */
                                         Int3 paras,
                                         VectorDouble threshold,
                                         int[] membership,
                                         double[][] tmp_cluster_centres) /* out: [npoints] */ {

        int n = 0;
        VectorInt index = new VectorInt(1);
        int[] new_centers_len; /* [nclusters]: no. of points in each cluster */
        VectorDouble delta = new VectorDouble(1);
        double[][] new_centers; /* [nclusters][nfeatures] */

        /* allocate space for returning variable clusters[] */
        //tmp_cluster_centres = new double[nclusters][nfeatures];

        /* randomly pick cluster centers */
        for (int i = 0; i < nclusters; i++) {
            for (int j = 0; j < paras.getZ(); j++) {
                tmp_cluster_centres[i][j] = feature[n][j];
            }
            n++;
        }

        for (int i = 0; i < paras.getY(); i++) {
            membership[i] = -1;
        }

        /* need to initialize new_centers_len and new_centers[0] to all 0 */
        new_centers_len = new int[nclusters];

        new_centers = new double[nclusters][paras.getZ()];

        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, feature, paras, membership, tmp_cluster_centres, index, delta, new_centers, new_centers_len)
                .task("t1", Kmeans::updateCentres, feature, paras, membership, tmp_cluster_centres, index, delta, new_centers, new_centers_len)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, feature, paras, membership, tmp_cluster_centres, index, delta, new_centers, new_centers_len);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);
        do {
            delta.set(0, 0.0);
            executor1.execute();
            //            for (int i = 0; i < paras.getY(); i++) {
            //                /* find the index of nestest cluster centers */
            //                find_nearest_point(feature[i], tmp_cluster_centres, index);
            //                /* if membership changes, increase delta by 1 */
            //                if (membership[i] != index.get(0)) {
            //                    delta.set(0, delta.get(0) + 1.0);
            //                }
            //                /* assign the membership to object i */
            //                membership[i] = index.get(0);
            //                /* update new cluster centers : sum of objects located within */
            //                new_centers_len[index.get(0)]++;
            //                for (int j = 0; j < paras.getZ(); j++){
            //                    new_centers[index.get(0)][j] += feature[i][j];
            //                }
            //            }

            //updateCentres(feature, paras, membership, tmp_cluster_centres, index, delta, new_centers, new_centers_len);

            //            /* replace old cluster centers with new_centers */
            //            for (int i = 0; i < nclusters; i++) {
            //                for (int j = 0; j < paras.getZ(); j++) {
            //                    if (new_centers_len[i] > 0)
            //                        tmp_cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
            //                    new_centers[i][j] = 0.0; /* set back to 0 */
            //                }
            //                new_centers_len[i] = 0; /* set back to 0 */
            //            }

            //delta /= npoints;
        } while (delta.get(0) > threshold.get(0));

        //return clusters;
    }

    /*---< cluster() >-----------------------------------------------------------*/
    public static void cluster(int numObjects, /* number of input objects */
                               int numAttributes, /* size of attribute of each object */
                               double[][] attributes, /* [numObjects][numAttributes] */
                               int num_nclusters,
                               VectorDouble threshold, /* in:   */
                               double[][] cluster_centres /* out: [best_nclusters][numAttributes] */

    ) {
        int[] membership;
        double[][] tmp_cluster_centres = new double[nclusters][numAttributes];

        membership = new int[numObjects];

        nclusters = num_nclusters;

        kmeans_clustering(attributes,
                paras,
                threshold,
                membership,
                tmp_cluster_centres);

        for (int i = 0; i < tmp_cluster_centres.length; i++) {
            for (int j = 0; j < cluster_centres[0].length; j++) {
                cluster_centres[i][j] = tmp_cluster_centres[i][j];
            }
        }
    }

    public static void main(String[] args) {
        // Parse command-line arguments
        if (args.length < 3) {
            System.out.println("Usage: java Kmeans <input_file_name> <num_of_clusters> <threshold_value>");
            System.exit(1);
        }

        numAttributes = 0;
        numObjects = 0;
        String filename = args[0];
        nclusters = Integer.valueOf(args[1]);
        // threshold = Double.valueOf(args[2]);
        VectorDouble threshold = new VectorDouble(1);
        threshold.set(0, Double.valueOf(args[2]));
        paras = new Int3(); // num_clusters, numObjects, numAttributes
        paras.setX(nclusters);

        /* from the input file, get the numAttributes and numObjects ------------*/
        try {
            Scanner scanner = new Scanner(new File(filename));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                numObjects++;
            }
            scanner.close();
            paras.setY(numObjects);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            Scanner scanner = new Scanner(new File(filename));
            String line = scanner.nextLine();
            String[] nums = line.split(" ");
            numAttributes = nums.length - 1;
            scanner.close();
            paras.setZ(numAttributes);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        /* allocate space for attributes[] and read attributes of all objects */
        double[] buf = new double[numObjects * numAttributes];
        double[][] attributes = new double[numObjects][numAttributes];
        try {
            Scanner scanner = new Scanner(new File(filename));
            int i = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] nums = line.split(" ");
                for (int j = 1; j < nums.length; j++) {
                    buf[i] = Double.valueOf(nums[j]);
                    i++;
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("I/O completed");

        int tempIndex = 0;
        for (int i = 0; i < numObjects; i++) {
            for (int j = 0; j < numAttributes; j++) {
                attributes[i][j] = buf[tempIndex];
                tempIndex++;
            }
        }

        double[][] cluster_centres = new double[numObjects][numAttributes];
        cluster(numObjects,
                numAttributes,
                attributes, /* [numObjects][numAttributes] */
                nclusters,
                threshold,
                cluster_centres
        );

        System.out.printf("number of Clusters %d\n", nclusters);
        System.out.printf("number of Attributes %d\n\n", numAttributes);
        System.out.printf("Cluster Centers Output\n");
        System.out.printf("The first number is cluster number and the following data is arribute value\n");
        System.out.printf("=============================================================================\n\n");

        for (int i = 0; i < nclusters; i++) {
            System.out.printf("%d: ", i);
            for (int j = 0; j < numAttributes; j++)
                System.out.printf("%f ", cluster_centres[i][j]);
            System.out.printf("\n\n");
        }
    }
}