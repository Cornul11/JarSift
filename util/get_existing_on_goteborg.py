import argparse
from tqdm import tqdm
import logging

logging.basicConfig(level=logging.INFO, format="")


def parse_arguments():
    parser = argparse.ArgumentParser(
        description="Process the paths to find remaining vulnerable artifacts."
    )
    parser.add_argument(
        "--vulnerable", required=True, help="Path to the vulnerable_artifacts.txt file"
    )
    parser.add_argument(
        "--downloaded", required=True, help="Path to the downloaded_artifacts.txt file"
    )
    parser.add_argument(
        "--jar_paths", required=True, help="Path to the jar_paths.txt file"
    )
    parser.add_argument(
        "--output",
        required=True,
        help="Path to the output file for remaining vulnerable artifacts",
    )
    return parser.parse_args()


def read_downloaded_artifacts(filename, base_path):
    with open(filename, "r") as file:
        return {
            line.strip()[len(base_path) :]
            for line in tqdm(file, desc="Reading downloaded artifacts")
        }


def read_vulnerable_artifacts(filename):
    with open(filename, "r") as file:
        return {
            line.strip() for line in tqdm(file, desc="Reading vulnerable artifacts")
        }


def gav_to_path(gav):
    group_id, artifact_id, version = gav.split(":")
    return f"{group_id.replace('.', '/')}/{artifact_id}/{version}"


def read_jar_paths(filename, base_path):
    with open(filename, "r") as file:
        jar_paths = {}
        for line in tqdm(file, desc="Reading jar paths"):
            path = line.strip()
            gav = path_to_gav(path, base_path)
            jar_paths[gav] = path[len(base_path) :]
        return jar_paths


def path_to_gav(path, base_path):
    path = path.replace(base_path, "").strip("/")
    parts = path.split("/")

    if (
        len(parts) < 4
    ):  # Need at least group_id, artifact_id, version, and jar file name
        print(
            f"Warning: Path does not conform to expected format and will be skipped: {path}"
        )
        return None
        # or return an empty string or a placeholder value that you can filter out later

    version = parts[-2]
    artifact_id = parts[-3]
    group_id = ".".join(parts[:-3])
    return f"{group_id}:{artifact_id}:{version}"


def main():
    args = parse_arguments()

    base_path_downloaded = "/home/dan/jar-vulnerability-detection/util/download_path/"
    base_path_jars = "/data/.m2/repository/"
    vulnerable_artifacts = read_vulnerable_artifacts(args.vulnerable)
    downloaded_artifacts = read_downloaded_artifacts(
        args.downloaded, base_path_downloaded
    )
    jar_paths = read_jar_paths(args.jar_paths, base_path_jars)

    vulnerable_paths = {gav_to_path(gav) for gav in vulnerable_artifacts}

    remaining_artifacts = vulnerable_paths - set(downloaded_artifacts)

    logging.info("Calculating remaining artifacts")
    remaining_in_jar_paths = {
        jar_paths[gav] for gav in jar_paths if gav_to_path(gav) in remaining_artifacts
    }

    with open(args.output, "w") as file:
        for path in tqdm(sorted(remaining_in_jar_paths), desc="Writing to output file"):
            file.write(base_path_jars + path + "\n")

    logging.info(
        f"Found {len(remaining_in_jar_paths)} remaining vulnerable artifacts in JAR paths"
    )

    not_found_anywhere_count = (
        len(vulnerable_paths)
        - len({gav_to_path(gav) for gav in jar_paths if gav in vulnerable_artifacts})
        - len(remaining_in_jar_paths)
    )

    logging.info(
        f"{not_found_anywhere_count} vulnerable artifacts have not been found in downloaded artifacts or jar paths."
    )


if __name__ == "__main__":
    main()
