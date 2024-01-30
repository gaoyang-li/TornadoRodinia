package uk.ac.manchester.tornado.examples.rodinia.bfs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.api.collections.types.Int2;
import uk.ac.manchester.tornado.api.collections.types.Int3;
import uk.ac.manchester.tornado.api.collections.types.Int4;
import uk.ac.manchester.tornado.api.collections.types.Int8;
import uk.ac.manchester.tornado.api.collections.types.VectorInt;
import uk.ac.manchester.tornado.api.collections.types.VectorInt2;
import uk.ac.manchester.tornado.api.collections.types.VectorInt3;
import uk.ac.manchester.tornado.api.collections.types.VectorInt4;
import uk.ac.manchester.tornado.api.collections.types.VectorInt8;

public class BFS {
    //    Node[] h_graph_nodes;
    static VectorInt h_graph_nodes_starting;
    static VectorInt h_graph_nodes_edges;
    static boolean stop = false;
    static int no_of_nodes = 0;

    public static void main(String[] args) {
        if (args.length != 2) {
            usage();
            System.exit(1);
        }
        BFS b = new BFS();
        int num_threads = Integer.parseInt(args[0]);
        String inputFile = args[1];

        b.bfsGraph(num_threads, inputFile);
    }

    private static void usage() {
        System.out.println("Usage: BFS <num_threads> <input_file>");
    }

    private void bfsGraph(int num_threads, String inputFile) {
        System.out.println("Reading File");

        try {
            Scanner scanner = new Scanner(new File(inputFile));
            no_of_nodes = scanner.nextInt();
            h_graph_nodes_starting = new VectorInt(no_of_nodes);
            h_graph_nodes_edges = new VectorInt(no_of_nodes);
            initNodes(scanner, h_graph_nodes_starting, h_graph_nodes_edges);
            for (int i = 0; i < no_of_nodes; i++) {
                h_graph_nodes_starting.set(i, h_graph_nodes_starting.get(i));
                h_graph_nodes_edges.set(i, h_graph_nodes_edges.get(i));
            }
            VectorInt h_graph_mask = new VectorInt(no_of_nodes); // boolean[] h_graph_mask = new boolean[no_of_nodes];
            VectorInt h_updating_graph_mask = new VectorInt(no_of_nodes); // boolean[] h_updating_graph_mask = new boolean[no_of_nodes];
            VectorInt h_graph_visited = new VectorInt(no_of_nodes); // boolean[] h_graph_visited = new boolean[no_of_nodes];
            int source = scanner.nextInt();
            h_graph_mask.set(source, 1);
            h_graph_visited.set(source, 1);
            int edge_list_size = scanner.nextInt();
            VectorInt h_graph_edges = initEdges(scanner, edge_list_size);
            scanner.close();
            VectorInt h_cost = new VectorInt(no_of_nodes);
            for (int i = 0; i < no_of_nodes; i++) {
                h_cost.set(i, -1);
            }
            h_cost.set(source, 0);
            traverseGraph(num_threads, no_of_nodes, h_graph_edges, h_graph_mask, h_updating_graph_mask, h_graph_visited, h_cost);
            writeResultsToFile(h_cost);
        } catch (FileNotFoundException e) {
            System.out.println("Error Reading graph file");
        }
    }

    public void initNodes(Scanner scanner, VectorInt h_graph_nodes_starting, VectorInt h_graph_nodes_edges) {
        for (int i = 0; i < no_of_nodes; i++) {
            h_graph_nodes_starting.set(i, scanner.nextInt());
            h_graph_nodes_edges.set(i, scanner.nextInt());
        }
    }

    public static VectorInt initEdges(Scanner scanner, int edge_list_size) {
        VectorInt edges = new VectorInt(edge_list_size);
        for (int i = 0; i < edge_list_size; i++) {
            edges.set(i, scanner.nextInt());
            scanner.nextInt(); // cost, currently not used
        }
        return edges;
    }

    public static void initMask(VectorInt h_graph_nodes_starting, VectorInt h_graph_nodes_edges, VectorInt h_graph_mask, VectorInt h_graph_visited, VectorInt h_graph_edges, VectorInt h_cost, VectorInt h_updating_graph_mask) { //int no_of_nodes
        for (@Parallel int tid = 0; tid < h_graph_nodes_starting.size(); tid++) {
            if (h_graph_mask.get(tid) == 1) {
                h_graph_mask.set(tid, 0);
                for (int i = h_graph_nodes_starting.get(tid); i < (h_graph_nodes_starting.get(tid) + h_graph_nodes_edges.get(tid)); i++) {
                    int id = h_graph_edges.get(i);
                    if (h_graph_visited.get(id) == 0) {
                        h_cost.set(id, h_cost.get(tid)+1);
                        h_updating_graph_mask.set(id, 1);
                    }
                }
            }
        }
    }

    public static void updateMask(VectorInt h_updating_graph_mask, VectorInt h_graph_mask, VectorInt h_graph_visited, VectorInt stop) {
        for (@Parallel int tid = 0; tid < h_updating_graph_mask.size(); tid++) {
            if (h_updating_graph_mask.get(tid) == 1) {
                h_graph_mask.set(tid, 1);
                h_graph_visited.set(tid, 1);
                stop.set(0, 1); // stop = true
                h_updating_graph_mask.set(tid, 0);
            }
        }
    }

    public static void traverseGraph(int num_threads, int no_of_nodes, VectorInt h_graph_edges, VectorInt h_graph_mask, VectorInt h_updating_graph_mask, VectorInt h_graph_visited, VectorInt h_cost) {
        System.out.println("Start traversing the tree");
        VectorInt stop = new VectorInt(1);
        TornadoDevice device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph1 = new TaskGraph("s1")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, h_graph_nodes_starting, h_graph_nodes_edges, h_graph_mask, h_graph_visited, h_graph_edges, h_cost, h_updating_graph_mask) //
                .task("t1", BFS::initMask, h_graph_nodes_starting, h_graph_nodes_edges, h_graph_mask, h_graph_visited, h_graph_edges, h_cost, h_updating_graph_mask) // no_of_nodes,
                .transferToHost(DataTransferMode.EVERY_EXECUTION, h_graph_mask, h_cost, h_updating_graph_mask);
        ImmutableTaskGraph immutableTaskGraph1 = taskGraph1.snapshot();
        TornadoExecutionPlan executor1 = new TornadoExecutionPlan(immutableTaskGraph1)
                .withDevice(device);

        device = TornadoRuntime.getTornadoRuntime().getDefaultDevice();
        TaskGraph taskGraph2 = new TaskGraph("s2")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, h_updating_graph_mask, stop)
                .task("t2", BFS::updateMask, h_updating_graph_mask, h_graph_mask, h_graph_visited, stop)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, h_updating_graph_mask, h_graph_mask, h_graph_visited, stop);
        ImmutableTaskGraph immutableTaskGraph2 = taskGraph2.snapshot();
        TornadoExecutionPlan executor2 = new TornadoExecutionPlan(immutableTaskGraph2)
                .withDevice(device);

        long startTime = System.nanoTime();
        int loops = 0;
        do {
            stop.set(0, 0);
            executor1.execute();
            executor2.execute();
            loops++;
//             initMask(h_graph_nodes_starting, h_graph_nodes_edges, h_graph_mask, h_graph_visited, h_graph_edges, h_cost, h_updating_graph_mask);
//             updateMask(h_updating_graph_mask, h_graph_mask, h_graph_visited, stop);
        } while (stop.get(0) == 1);
        long endTime = System.nanoTime();
//        System.out.println("loops: " + loops);
        System.out.println("Compute time: " + (double)(endTime - startTime) / 1000000000);
    }

    private static void writeResultsToFile(VectorInt h_cost) {
        try {
            PrintWriter writer = new PrintWriter("result.txt");
            for (int i = 0; i < h_cost.size(); i++) {
                writer.printf("%d) cost:%d%n", i, h_cost.get(i));
            }
            writer.close();
            System.out.println("Result stored in result.txt");
        } catch (FileNotFoundException e) {
            System.out.println("Error writing to result.txt");
        }
    }
}