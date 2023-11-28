import json
import logging
import os

import requests
from tqdm import tqdm
from tqdm.contrib.logging import logging_redirect_tqdm

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)


def download_jar(artifact, version_key, folder="artifacts"):
    """Downloads jar from Maven Central"""

    version_info = artifact.get(version_key)
    if version_info and "Central" in version_info.get("repositories", []):
        group_id = artifact["groupId"]
        group_path = group_id.replace(".", "/")
        artifact_id = artifact["artifactId"]
        version = version_info["version"]

        url = f"https://repo1.maven.org/maven2/{group_path}/{artifact_id}/{version}/{artifact_id}-{version}.jar"

        response = requests.head(url)
        if response.status_code == 404:
            tqdm_log(f"File {url} does not exist")
            url = url.replace(".jar", ".pom")
            response = requests.head(url)
            if response.status_code == 200:
                tqdm_log(f"File {url} exists, but is a POM file")
            return

        file_path = os.path.join(folder, group_id, f"{artifact_id}-{version}.jar")

        if os.path.exists(file_path):
            tqdm_log(f"File {file_path} already exists")
            return

        response = requests.get(url)
        if response.status_code == 200:
            os.makedirs(os.path.dirname(file_path), exist_ok=True)
            with open(file_path, "wb") as f:
                f.write(response.content)
        else:
            tqdm_log(
                f"Failed to download {url} with status code {response.status_code}"
            )


def tqdm_log(msg):
    with logging_redirect_tqdm():
        logging.error(msg)


def main(file_path):
    with open(file_path, "r") as file:
        artifacts = json.load(file)

    for artifact in tqdm(artifacts, desc="Downloading artifacts", unit="artifact"):
        download_jar(artifact, "mostUsedVersion")
        download_jar(artifact, "mostUsedVulnerableVersion")


if __name__ == "__main__":
    import sys

    file_path = sys.argv[1]
    main(file_path)
