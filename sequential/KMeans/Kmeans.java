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

    public static int find_nearest_point(double[] pt, /* [nfeatures] */
        int nfeatures,
        double[][] pts, /* [npts][nfeatures] */
        int npts
    ) {
        int index = 0;
        double min_dist = FLT_MAX;
        /* find the cluster center id with min distance to pt */
        for (int i = 0; i < npts; i++) {
            double dist;
            dist = euclid_dist_2(pt, pts[i], nfeatures); /* no need square root */
            if (dist < min_dist) {
                min_dist = dist;
                index = i;
            }
        }
        return index;
    }

    /*----< euclid_dist_2() >----------------------------------------------------*/
    /* multi-dimensional spatial Euclid distance square */
    public static double euclid_dist_2(double[] pt1,
        double[] pt2,
        int numdims) {
        double ans = 0;

        for (int i = 0; i < numdims; i++) {
            ans += (pt1[i] - pt2[i]) * (pt1[i] - pt2[i]);
        }

        return ans;
    }

    /*----< kmeans_clustering() >---------------------------------------------*/
    public static void kmeans_clustering(double[][] feature, /* in: [npoints][nfeatures] */
        int nfeatures,
        int npoints,
        int nclusters,
        double threshold,
        int[] membership,
        double[][] tmp_cluster_centres) /* out: [npoints] */ {

        int i, j, n = 0, index, loop = 0;
        int[] new_centers_len; /* [nclusters]: no. of points in each cluster */
        double delta;
        double[][] new_centers; /* [nclusters][nfeatures] */

        /* allocate space for returning variable clusters[] */
        //tmp_cluster_centres = new double[nclusters][nfeatures];

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

        new_centers = new double[nclusters][nfeatures];

        do {

            delta = 0.0;

            for (i = 0; i < npoints; i++) {
                /* find the index of nestest cluster centers */
                index = find_nearest_point(feature[i], nfeatures, tmp_cluster_centres, nclusters);
                /* if membership changes, increase delta by 1 */
                if (membership[i] != index) delta += 1.0;

                /* assign the membership to object i */
                membership[i] = index;

                /* update new cluster centers : sum of objects located within */
                new_centers_len[index]++;
                for (j = 0; j < nfeatures; j++)
                    new_centers[index][j] += feature[i][j];
            }

            /* replace old cluster centers with new_centers */
            for (i = 0; i < nclusters; i++) {
                for (j = 0; j < nfeatures; j++) {
                    if (new_centers_len[i] > 0)
                        tmp_cluster_centres[i][j] = new_centers[i][j] / new_centers_len[i];
                    new_centers[i][j] = 0.0; /* set back to 0 */
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
        double[][] attributes, /* [numObjects][numAttributes] */
        int num_nclusters,
        double threshold, /* in:   */
        double[][] cluster_centres /* out: [best_nclusters][numAttributes] */

    ) {
        int[] membership;
        double[][] tmp_cluster_centres = new double[nclusters][numAttributes];

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
        String filename = args[0];
        nclusters = Integer.valueOf(args[1]);
        threshold = Double.valueOf(args[2]);

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
