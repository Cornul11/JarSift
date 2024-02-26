import argparse
import json
import os


def load_json_file(file_path):
    with open(file_path, "r") as file:
        return json.load(file)


def find_vulnerabilities_in_inferred_artifact(inferred_artifact_file_path):
    try:
        artifact_data = load_json_file(inferred_artifact_file_path)
        vulnerabilities = []
        for library in artifact_data:
            vulnerabilities.extend(library.get("vulnerabilities", []))
        return vulnerabilities
    except FileNotFoundError:
        return []


def print_artifact_info(
    group_id, artifact_id, version, vulnerabilities, is_vulnerable_version
):
    """Prints the artifact information. Prints in red if it's a known vulnerable version."""
    color_start = "\033[91m" if is_vulnerable_version else ""
    color_end = "\033[0m" if is_vulnerable_version else ""

    print(
        f"{color_start}Artifact: {group_id}:{artifact_id}, Version: {version}, Vulnerabilities: {vulnerabilities}{color_end}"
    )


def analyze_artifacts(maven_artifacts_file, artifacts_directory):
    usable_shaded_jar_count = 0
    detected_unknown_vulnerable_versions = 0
    detected_known_vulnerable_versions = 0
    maven_artifacts = load_json_file(maven_artifacts_file)
    for artifact in maven_artifacts:
        group_id = artifact["groupId"]
        artifact_id = artifact["artifactId"]

        for version_key in ["mostUsedVersion", "mostUsedVulnerableVersion"]:
            version_info = artifact.get(version_key)
            if version_info:
                usable_shaded_jar_count += 1
                version = version_info["version"]
                inferred_artifact_file_path = os.path.join(
                    artifacts_directory, group_id, f"{artifact_id}-{version}.json"
                )
                detected_vulnerabilities = find_vulnerabilities_in_inferred_artifact(
                    inferred_artifact_file_path
                )
                if detected_vulnerabilities:
                    is_vulnerable_version = version_key == "mostUsedVulnerableVersion"
                    print_artifact_info(
                        group_id,
                        artifact_id,
                        version,
                        detected_vulnerabilities,
                        is_vulnerable_version,
                    )
                    if not is_vulnerable_version:
                        detected_unknown_vulnerable_versions += 1
                    else:
                        detected_known_vulnerable_versions += 1

    print(f"Total usable shaded JARs: {usable_shaded_jar_count}")
    print(
        f"Total detected vulnerable versions: {detected_unknown_vulnerable_versions} ({(detected_unknown_vulnerable_versions / usable_shaded_jar_count) * 100:.2f}%)"
    )
    print(
        f"Total detected known vulnerable versions: {detected_known_vulnerable_versions} ({(detected_known_vulnerable_versions / usable_shaded_jar_count) * 100:.2f}%)"
    )


def main():
    parser = argparse.ArgumentParser(
        description="Analyze Maven artifacts for vulnerabilities"
    )
    parser.add_argument(
        "--maven_artifacts_file",
        required=True,
        help="Path to the JSON file containing Maven artifacts information",
    )
    parser.add_argument(
        "--artifacts_directory",
        required=True,
        help="Path to the directory containing the inferred artifacts metadata",
    )

    args = parser.parse_args()
    analyze_artifacts(args.maven_artifacts_file, args.artifacts_directory)


if __name__ == "__main__":
    main()
