package uk.ac.manchester.tornado.examples.rodinia.kmeans;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat;
import uk.ac.manchester.tornado.api.types.collections.VectorInt;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat;
import java.io.File;
import java.util.Scanner;

public class Kmeans{
    final static int RANDOM_MAX = Integer.MAX_VALUE;
    final static float FLT_MAX = 3.40282347e+38f;
    static int numAttributes = 0;
    static int numObjects = 0;
    static int nclusters = 5;
    static float threshold = 0.001f;

    public static void parallel(int npoints,
                                int nfeatures,
                                int nclusters,
                                Matrix2DFloat feature,
                                Matrix2DFloat tmp_cluster_centres,
                                Matrix2DFloat new_centers,
                                VectorInt index,
                                VectorInt membership,
                                VectorInt new_centers_len,
                                @Reduce VectorFloat delta
                                ){
        for (@Parallel int i = 0; i < npoints; i++) {
            /* find the index of nestest cluster centers */
            find_nearest_point(i, feature, nfeatures, tmp_cluster_centres, nclusters, index);
            /* if membership changes, increase delta by 1 */
            if (membership.get(i) != index.get(0)) {
                delta.set(0, delta.get(0) + 1.0f);
            }

            /* assign the membership to object i */
            membership.set(i, index.get(0));

            /* update new cluster centers : sum of objects located within */
            new_centers_len.set(index.get(0), new_centers_len.get(index.get(0)) + 1);
            for (int j = 0; j < nfeatures; j++){
                new_centers.set(index.get(0), j, new_centers.get(index.get(0), j) + feature.get(i, j));
            }
        }
    }

    public static void find_nearest_point(int p, Matrix2DFloat pt, int nfeatures, Matrix2DFloat pts, int npts, VectorInt index) {
        float min_dist = FLT_MAX;
        /* find the cluster center id with min distance to pt */
        for (int i = 0; i < npts; i++) {
            float dist = 0;
            for (int j = 0; j < nfeatures; j++) {
                dist += (pt.get(p, j) - pts.get(i, j)) * (pt.get(p, j) - pts.get(i, j));
            }
            if (dist < min_dist) {
                min_dist = dist;
                index.set(0, i);
            }
        }
    }

    /*----< kmeans_clustering() >---------------------------------------------*/
    public static void kmeans_clustering(Matrix2DFloat feature, /* in: [npoints][nfeatures] */
                                         int nfeatures,
                                         int npoints,
                                         int nclusters,
                                         float threshold,
                                         VectorInt membership,
                                         Matrix2DFloat tmp_cluster_centres) /* out: [npoints] */ {
        int n = 0;
        VectorInt index = new VectorInt(1);
        int loop = 0;
        VectorInt new_centers_len; /* [nclusters]: no. of points in each cluster */
        VectorFloat delta = new VectorFloat(1);
        Matrix2DFloat new_centers; /* [nclusters][nfeatures] */

        /* randomly pick cluster centers */
        for (int i = 0; i < nclusters; i++) {
            for (int j = 0; j < nfeatures; j++) {
                tmp_cluster_centres.set(i, j, feature.get(n, j));
            }
            n++;
        }

        for (int i = 0; i < npoints; i++) {
            membership.set(i, -1);
        }

        /* need to initialize new_centers_len and new_centers[0] to all 0 */
        new_centers_len = new VectorInt(nclusters);
        new_centers = new Matrix2DFloat(nclusters, nfeatures);

        do {
            delta.set(0, 0.0f);
            TaskGraph taskGraph1 = new TaskGraph("s1")
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, npoints, nfeatures, nclusters, feature, tmp_cluster_centres, new_centers, index, membership, new_centers_len, delta)
                    .task("t1", Kmeans::parallel, npoints, nfeatures, nclusters, feature, tmp_cluster_centres, new_centers, index, membership, new_centers_len, delta)
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, new_centers, index, membership, new_centers_len, delta);
            ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
            TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1);
            executor1.execute();

            /* replace old cluster centers with new_centers */
            for (int i = 0; i < nclusters; i++) {
                for (int j = 0; j < nfeatures; j++) {
                    if (new_centers_len.get(i) > 0){
                        tmp_cluster_centres.set(i, j, new_centers.get(i, j) / new_centers_len.get(i));
                    }
                    new_centers.set(i, j, 0.0f); /* set back to 0 */
                }
                new_centers_len.set(i, 0); /* set back to 0 */
            }
        } while (delta.get(0) > threshold);

        //return clusters;
    }

    /*---< cluster() >-----------------------------------------------------------*/
    public static void cluster(int numObjects, /* number of input objects */
                               int numAttributes, /* size of attribute of each object */
                               Matrix2DFloat attributes, /* [numObjects][numAttributes] */
                               int num_nclusters,
                               float threshold, /* in:   */
                               Matrix2DFloat cluster_centres /* out: [best_nclusters][numAttributes] */

    ) {
        VectorInt membership;

        Matrix2DFloat tmp_cluster_centres = new Matrix2DFloat(nclusters, numAttributes);

        membership = new VectorInt(numObjects);

        nclusters = num_nclusters;

        kmeans_clustering(attributes,
                numAttributes,
                numObjects,
                nclusters,
                threshold,
                membership,
                tmp_cluster_centres);

        for (int i = 0; i < tmp_cluster_centres.getNumRows(); i++) {
            for (int j = 0; j < cluster_centres.getNumColumns(); j++) {
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

        numAttributes = 0;
        numObjects = 0;
        int nloops = 1;
        Matrix2DFloat cluster_centres = null;
        String filename = args[0];
        nclusters = Integer.parseInt(args[1]);
        threshold = Float.parseFloat(args[2]);

        /* from the input file, get the numAttributes and numObjects ------------*/
        try {
            Scanner scanner = new Scanner(new File(filename));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                numObjects++;
            }
            scanner.close();
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }

        try {
            Scanner scanner = new Scanner(new File(filename));
            String line = scanner.nextLine();
            String[] nums = line.split(" ");
            numAttributes = nums.length - 1;
            scanner.close();
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }

        /* allocate space for attributes[] and read attributes of all objects */
        VectorFloat buf = new VectorFloat(numObjects * numAttributes);
        Matrix2DFloat attributes = new Matrix2DFloat(numObjects, numAttributes);
        try {
            Scanner scanner = new Scanner(new File(filename));
            int i = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] nums = line.split(" ");
                for (int j = 1; j < nums.length; j++) {
                    buf.set(i, Float.parseFloat(nums[j]));
                    i++;
                }
            }
            scanner.close();
        } catch (Exception e) {
            System.err.println("error reading file");
            System.exit(1);
        }
        System.out.println("I/O completed");

        int tempIndex = 0;
        for (int i = 0; i < numObjects; i++) {
            for (int j = 0; j < numAttributes; j++) {
                attributes.set(i, j, buf.get(tempIndex));
                tempIndex++;
            }
        }

        double startTime = System.nanoTime();
        for (int i = 0; i < nloops; i++) {
            cluster_centres = new Matrix2DFloat(numObjects, numAttributes);
            cluster(numObjects,
                    numAttributes,
                    attributes, /* [numObjects][numAttributes] */
                    nclusters,
                    threshold,
                    cluster_centres
            );
        }
        double endTime = System.nanoTime();

        System.out.printf("number of Clusters %d\n", nclusters);
        System.out.printf("number of Attributes %d\n\n", numAttributes);
        System.out.printf("Cluster Centers Output\n");
        System.out.printf("The first number is cluster number and the following data is arribute value\n");
        System.out.printf("=============================================================================\n\n");

        for (int i = 0; i < nclusters; i++) {
            System.out.printf("%d: ", i);
            for (int j = 0; j < numAttributes; j++)
                System.out.printf("%g ", cluster_centres.get(i, j));
            System.out.print("\n\n");
        }

        System.out.printf("Time for process: %f seconds\n", ((endTime - startTime) / 1000000000));
    }
}