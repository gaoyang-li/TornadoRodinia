import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Kmeans {
    final static int RANDOM_MAX = Integer.MAX_VALUE;
    final static float FLT_MAX = 3.40282347e+38f;
    static int numAttributes = 0;
    static int numObjects = 0;
    static int nclusters = 5;
    static float threshold = 0.001f;

    public static void find_nearest_point(float[] pt, /* [nfeatures] */
        int nfeatures,
        float[][] pts, /* [npts][nfeatures] */
        int npts,
        int[] index
    ) {
        float min_dist = FLT_MAX;
        /* find the cluster center id with min distance to pt */
        for (int i = 0; i < npts; i++) {
            float dist = 0;
            for (int j = 0; j < nfeatures; j++) {
                dist += (pt[j] - pts[i][j]) * (pt[j] - pts[i][j]);
            }

            // dist = euclid_dist_2(pt, pts[i], nfeatures); /* no need square root */
            if (dist < min_dist) {
                min_dist = dist;
                index[0] = i;
            }
        }
        //return index;
    }

    /*----< euclid_dist_2() >----------------------------------------------------*/
    /* multi-dimensional spatial Euclid distance square */
    // public static float euclid_dist_2(float[] pt1,
    //     float[] pt2,
    //     int numdims) {
    //     float ans = 0;

    //     for (int i = 0; i < numdims; i++) {
    //         ans += (pt1[i] - pt2[i]) * (pt1[i] - pt2[i]);
    //     }

    //     return ans;
    // }

    /*----< kmeans_clustering() >---------------------------------------------*/
    public static void kmeans_clustering(float[][] feature, /* in: [npoints][nfeatures] */
        int nfeatures,
        int npoints,
        int nclusters,
        float threshold,
        int[] membership,
        float[][] tmp_cluster_centres) /* out: [npoints] */ {

        int i, j, n = 0;
        int[] index = new int[1];
        int loop = 0;
        int[] new_centers_len; /* [nclusters]: no. of points in each cluster */
        float delta;
        float[][] new_centers; /* [nclusters][nfeatures] */

        /* allocate space for returning variable clusters[] */
        //tmp_cluster_centres = new float[nclusters][nfeatures];

        /* randomly pick cluster centers */
        for (i = 0; i < nclusters; i++) {
            for (j = 0; j < nfeatures; j++) {
                tmp_cluster_centres[i][j] = feature[n][j];
            }
            n++;
        }

        for (i = 0; i < npoints; i++) {
            membership[i] = -1;
        }

        /* need to initialize new_centers_len and new_centers[0] to all 0 */
        new_centers_len = new int[nclusters];

        new_centers = new float[nclusters][nfeatures];

        do {

            delta = 0.0f;

            for (i = 0; i < npoints; i++) {
                /* find the index of nestest cluster centers */
                find_nearest_point(feature[i], nfeatures, tmp_cluster_centres, nclusters, index);
                /* if membership changes, increase delta by 1 */
                if (membership[i] != index[0]) delta += 1.0;

                /* assign the membership to object i */
                membership[i] = index[0];

                /* update new cluster centers : sum of objects located within */
                new_centers_len[index[0]]++;
                for (j = 0; j < nfeatures; j++)
                    new_centers[index[0]][j] += feature[i][j];
            }

            /* replace old cluster centers with new_centers */
            for (i = 0; i < nclusters; i++) {
                for (j = 0; j < nfeatures; j++) {
                    if (new_centers_len[i] > 0)
                        tmp_cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
                    new_centers[i][j] = 0.0f; /* set back to 0 */
                }
                new_centers_len[i] = 0; /* set back to 0 */
            }

            //delta /= npoints;
        } while (delta > threshold);

        //return clusters;
    }

    /*---< cluster() >-----------------------------------------------------------*/
    public static void cluster(int numObjects, /* number of input objects */
        int numAttributes, /* size of attribute of each object */
        float[][] attributes, /* [numObjects][numAttributes] */
        int num_nclusters,
        float threshold, /* in:   */
        float[][] cluster_centres /* out: [best_nclusters][numAttributes] */

    ) {
        int[] membership;
        float[][] tmp_cluster_centres = new float[nclusters][numAttributes];

        membership = new int[numObjects];

        nclusters = num_nclusters;

        kmeans_clustering(attributes,
            numAttributes,
            numObjects,
            nclusters,
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
        int nloops = 1;
        float[][] cluster_centres = null;
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            Scanner scanner = new Scanner(new File(filename));
            String line = scanner.nextLine();
            String[] nums = line.split(" ");
            numAttributes = nums.length - 1;
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        /* allocate space for attributes[] and read attributes of all objects */
        float[] buf = new float[numObjects * numAttributes];
        float[][] attributes = new float[numObjects][numAttributes];
        try {
            Scanner scanner = new Scanner(new File(filename));
            int i = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] nums = line.split(" ");
                for (int j = 1; j < nums.length; j++) {
                    buf[i] = Float.parseFloat(nums[j]);
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

        double startTime = System.nanoTime();
        for (int i = 0; i < nloops; i++) {
            cluster_centres = new float[numObjects][numAttributes];
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
                System.out.printf("%f ", cluster_centres[i][j]);
            System.out.printf("\n\n");
        }

        System.out.printf("Time for process: %f seconds\n", ((endTime - startTime) / 1000000000));
    }
}