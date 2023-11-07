import random
import sys

def generate_graph(num_nodes, max_edges_per_node):
    nodes = []
    edges = []
    
    edge_index = 0
    for i in range(num_nodes):
        num_edges = random.randint(0, max_edges_per_node)
        nodes.append((edge_index, num_edges))
        edge_index += num_edges
        
        for _ in range(num_edges):
            target_node = random.randint(0, num_nodes - 1)
            cost = random.randint(1, 10)  # assuming a cost between 1 and 10
            edges.append((target_node, cost))
            
    return nodes, edges

def write_to_file(filename, num_nodes, source_node, nodes, edges):
    with open(filename, 'w') as f:
        f.write(f"{num_nodes}\n")
        for node in nodes:
            f.write(f"{node[0]} {node[1]}\n")
        f.write(f"{source_node}\n")
        f.write(f"{len(edges)}\n")
        for edge in edges:
            f.write(f"{edge[0]} {edge[1]}\n")
            
if __name__ == "__main__":
    if len(sys.argv) != 5:
        print("Usage: python script.py <num_nodes> <max_edges_per_node> <source_node> <output_file>")
        sys.exit(1)
        
    num_nodes = int(sys.argv[1])
    max_edges_per_node = int(sys.argv[2])
    source_node = int(sys.argv[3])
    output_file = sys.argv[4]
    
    nodes, edges = generate_graph(num_nodes, max_edges_per_node)
    write_to_file(output_file, num_nodes, source_node, nodes, edges)
    
    print(f"Graph generated and written to {output_file}")

