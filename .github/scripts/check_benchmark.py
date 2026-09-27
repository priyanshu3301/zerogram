import json
import os
import sys
import glob

def main():
    # Macrobenchmark outputs JSON files in build/outputs/connected_android_test_additional_output/
    # Find the JSON file
    search_path = "baselineprofile/build/outputs/connected_android_test_additional_output/**/benchmarkData.json"
    files = glob.glob(search_path, recursive=True)
    
    if not files:
        print("No benchmarkData.json found.")
        sys.exit(1)
        
    benchmark_file = files[0]
    with open(benchmark_file, 'r') as f:
        data = json.load(f)
        
    # Example JSON structure for macrobenchmark:
    # {
    #   "benchmarks": [
    #     {
    #       "name": "startup",
    #       "metrics": {
    #         "timeToInitialDisplayMs": {
    #           "median": 250.0, ...
    #         }
    #       }
    #     }
    #   ]
    # }
    
    threshold_ms = 500.0
    failed = False
    
    for benchmark in data.get("benchmarks", []):
        name = benchmark.get("name", "")
        if "startup" in name.lower():
            metrics = benchmark.get("metrics", {})
            ttid = metrics.get("timeToInitialDisplayMs", {})
            median = ttid.get("median")
            
            if median is not None:
                print(f"Benchmark '{name}': Median timeToInitialDisplayMs = {median} ms")
                if median > threshold_ms:
                    print(f"ERROR: Cold start time ({median} ms) exceeds the threshold of {threshold_ms} ms!")
                    failed = True
                else:
                    print(f"SUCCESS: Cold start time is within the {threshold_ms} ms threshold.")
            else:
                print(f"WARNING: No timeToInitialDisplayMs found for benchmark '{name}'.")

    if failed:
        sys.exit(1)

if __name__ == "__main__":
    main()
