
| Benchmark  | Result compared to Rodinia Openmp | Exe time compared to Rodinia Openmp | opencl | ptx | spirv | remark |
| ------------- | ------------- | ------------- | ------------- | ------------- | ------------- | ------------- |
| bfs | same  | undetermined | support | support | unsupport | null
| hotspot | same  | undetermined | unsupport | support | unsupport | bail out with opencl-backend
| hotspot3D | same  | undetermined | support | support | unsupport | null
| kmeans | null  | undetermined | unsupport | unsupport | unsupport | [bug](https://github.com/beehive-lab/TornadoVM/issues/331)
| lud | undetermined  | undetermined | support | support | unsupport | null
| nw | undetermined  | undetermined | support | support | unsupport | null
| particlefilter | same  | undetermined | support | unsupport | unsupport | results are all Nah with ptx-backend
| pathfinder | null  | undetermined | unsupport | support | unsupport | null
| srad | undetermined  | undetermined | support | support | unsupport | null




