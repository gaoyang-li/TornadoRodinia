
| Algorithm  | Result compared to Rodinia Openmp | Exe time compared to Rodinia Openmp | opencl | ptx | spirv | remark |
| ------------- | ------------- | ------------- | ------------- | ------------- | ------------- | ------------- |
| bfs | same  | undetermined | support | support | undetermined | null
| hotspot | same  | undetermined | unsupport | support | undetermined | bail out with opencl-backend
| hotspot3D | same  | undetermined | unsupport | support | undetermined | null
| kmeans | null  | undetermined | unsupport | unsupport | undetermined | [bug](https://github.com/beehive-lab/TornadoVM/issues/331)
| lud | undetermined  | undetermined | support | unsupport | undetermined | null
| nw | undetermined  | undetermined | support | support | undetermined | null
| particlefilter | same  | undetermined | support | unsupport | undetermined | results are all Nah with ptx-backend
| pathfinder | same  | undetermined | unsupport | support | undetermined | null
| srad | undetermined  | undetermined | support | support | undetermined | null




