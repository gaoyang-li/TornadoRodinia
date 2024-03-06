import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;


public class Bfs {
    static int[] h_graph_nodes_starting;
    static int[] h_graph_nodes_edges;
    static boolean stop = false;
    static int no_of_nodes = 0;

    class Node {
        int starting;
        int no_of_edges;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            usage();
            System.exit(1);
        }
        String inputFile = args[0];
        bfsGraph(inputFile);
    }

    public static void usage() {
        System.out.println("Usage: java Bfs <input_file>");
    }

    public static void bfsGraph(String inputFile) {
        System.out.println("Reading File");

        try {
            Scanner scanner = new Scanner(new File(inputFile));
            no_of_nodes = scanner.nextInt();
            h_graph_nodes_starting = new int[no_of_nodes];
            h_graph_nodes_edges = new int[no_of_nodes];
            initNodes(scanner, h_graph_nodes_starting, h_graph_nodes_edges);
            for (int i = 0; i < no_of_nodes; i++) {
                h_graph_nodes_starting[i] = h_graph_nodes_starting[i];
                h_graph_nodes_edges[i] = h_graph_nodes_edges[i];
            }
            int[] h_graph_mask = new int[no_of_nodes]; // boolean[] h_graph_mask = new boolean[no_of_nodes];
            int[] h_updating_graph_mask = new int[no_of_nodes]; // boolean[] h_updating_graph_mask = new boolean[no_of_nodes];
            int[] h_graph_visited = new int[no_of_nodes]; // boolean[] h_graph_visited = new boolean[no_of_nodes];
            int source = scanner.nextInt();
            h_graph_mask[source] = 1;
            h_graph_visited[source] = 1;
            int edge_list_size = scanner.nextInt();
            int[] h_graph_edges = initEdges(scanner, edge_list_size);
            scanner.close();
            int[] h_cost = new int[no_of_nodes];
            for (int i = 0; i < no_of_nodes; i++) {
                h_cost[i] = -1;
            }
            h_cost[source] = 0;
            traverseGraph(no_of_nodes, h_graph_edges, h_graph_mask, h_updating_graph_mask, h_graph_visited, h_cost);
            writeResultsToFile(h_cost);
        } catch (FileNotFoundException e) {
            System.out.println("Error Reading graph file");
        }
    }

    public static void initNodes(Scanner scanner, int[] h_graph_nodes_starting, int[] h_graph_nodes_edges) {
        for (int i = 0; i < no_of_nodes; i++) {
            h_graph_nodes_starting[i] = scanner.nextInt();
            h_graph_nodes_edges[i] = scanner.nextInt();
        }
    }

    public static int[] initEdges(Scanner scanner, int edge_list_size) {
        int[] edges = new int[edge_list_size];
        for (int i = 0; i < edge_list_size; i++) {
            edges[i] = scanner.nextInt();
            scanner.nextInt();
        }
        return edges;
    }


    public static void initMask(int[] h_graph_nodes_starting, int[] h_graph_nodes_edges, int[] h_graph_mask, int[] h_graph_visited, int[] h_graph_edges, int[] h_cost, int[] h_updating_graph_mask) { 
        for ( int tid = 0; tid < h_graph_nodes_starting.length; tid++) {
            if (h_graph_mask[tid] == 1) {
                h_graph_mask[tid] = 0;
                for (int i = h_graph_nodes_starting[tid]; i < (h_graph_nodes_starting[tid] + h_graph_nodes_edges[tid]); i++) {
                    int id = h_graph_edges[i];
                    if (h_graph_visited[id] == 0) {
                        h_cost[id] = h_cost[tid]+1;
                        h_updating_graph_mask[id] = 1;
                    }
                }
            }
        }
    }

    public static void updateMask(int[] h_updating_graph_mask, int[] h_graph_mask, int[] h_graph_visited, int[] stop) {
        for ( int tid = 0; tid < h_updating_graph_mask.length; tid++) {
            if (h_updating_graph_mask[tid] == 1) {
                h_graph_mask[tid] = 1;
                h_graph_visited[tid] = 1;
                stop[0] = 1; 
                h_updating_graph_mask[tid] = 0;
            }
        }
    }

    public static void traverseGraph(int no_of_nodes, int[] h_graph_edges, int[] h_graph_mask, int[] h_updating_graph_mask, int[] h_graph_visited, int[] h_cost) {
        System.out.println("Start traversing the tree");
        long startTime = System.nanoTime();
        int[] stop = new int[1];
        do {
            stop[0] = 0;
            initMask(h_graph_nodes_starting, h_graph_nodes_edges, h_graph_mask, h_graph_visited, h_graph_edges, h_cost, h_updating_graph_mask);
            updateMask(h_updating_graph_mask, h_graph_mask, h_graph_visited, stop);
        } while (stop[0] == 1);

        long endTime = System.nanoTime();
        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);
    }

    public static void writeResultsToFile(int[] h_cost) {
        try {
            PrintWriter writer = new PrintWriter("result.txt");
            for (int i = 0; i < h_cost.length; i++) {
                writer.printf("%d) cost:%d%n", i, h_cost[i]);
            }
            writer.close();
            System.out.println("Result stored in result.txt");
        } catch (FileNotFoundException e) {
            System.out.println("Error writing to result.txt");
        }
    }
}
