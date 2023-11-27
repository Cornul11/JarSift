import json
import os

import requests


def download_jar(artifact, version_key, folder="artifacts"):
    """Downloads jar from Maven Central"""

    version_info = artifact.get(version_key)
    if version_info and "Central" in version_info.get("repositories", []):
        group_path = artifact["groupId"].replace(".", "/")
        artifact_id = artifact["artifactId"]
        version = version_info["version"]

        url = f"https://repo1.maven.org/maven2/{group_path}/{artifact_id}/{version}/{artifact_id}-{version}.jar"
        file_path = os.path.join(folder, f"{artifact_id}-{version}.jar")

        response = requests.get(url)
        if response.status_code == 200:
            os.makedirs(folder, exist_ok=True)
            with open(file_path, "wb") as f:
                f.write(response.content)
        else:
            print(f"Failed to download {url} with status code {response.status_code}")


def main(file_path):
    with open(file_path, "r") as file:
        artifacts = json.load(file)

    for i, artifact in enumerate(artifacts, start=1):
        download_jar(artifact, "mostUsedVersion")
        download_jar(artifact, "mostUsedVulnerableVersion")

        if i >= 5:
            break


if __name__ == "__main__":
    import sys

    file_path = sys.argv[1]
    main(file_path)
