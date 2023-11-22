import json
import statistics
import time
from concurrent.futures import ThreadPoolExecutor
from pprint import pprint

import requests


def analyze_results(results, max_time):
    assert len(results) > 0
    response_times = [result[1] for result in results if result[0] == 200]
    error_count = len([result for result in results if result[0] != 200])
    assert len(response_times) > 0
    assert len(results) == len(response_times) + error_count
    average_time = statistics.mean(response_times)
    median_time = statistics.median(response_times)
    min_time = min(response_times)
    max_response_time = max(response_times)
    std_dev = statistics.stdev(response_times)
    percentiles = {
        "50th": statistics.quantiles(response_times, n=100)[49],
        "90th": statistics.quantiles(response_times, n=100)[89],
        "95th": statistics.quantiles(response_times, n=100)[94],
        "99th": statistics.quantiles(response_times, n=100)[98],
    }
    error_rate = error_count / len(results)
    throughput = len(results) / max_time

    return {
        "average": average_time,
        "median": median_time,
        "min": min_time,
        "max_response_time": max_response_time,
        "std_dev": std_dev,
        "percentiles": percentiles,
        "error_rate": error_rate,
        "throughput": throughput,
    }


def parse_jar_path(jar_path):
    parts = jar_path.strip().split("/")
    version = parts[-2]
    artifactId = parts[-3]
    groupId = ".".join(parts[parts.index("repository") + 1 : -3])
    return groupId, artifactId, version


def benchmark_api(groupId, artifactId, version, url="http://localhost:8080/lookup"):
    params = {
        "groupId": groupId,
        "artifactId": artifactId,
        "version": version,
    }
    start = time.time()
    response = requests.get(url, params=params)
    end = time.time()
    return response.status_code, end - start


def main(file_path):
    with open(file_path, "r") as file:
        jar_paths = file.readlines()

    start_time = time.time()

    with ThreadPoolExecutor(max_workers=10) as executor:
        futures = [
            executor.submit(benchmark_api, *parse_jar_path(path)) for path in jar_paths
        ]
        results = [future.result() for future in futures]

    end_time = time.time()
    max_time = end_time - start_time

    analysis = analyze_results(results, max_time)

    pprint(analysis)


if __name__ == "__main__":
    import sys

    file_path = sys.argv[1]
    main(file_path)
