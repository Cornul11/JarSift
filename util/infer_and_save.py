import logging
import os
import subprocess

from tqdm import tqdm
from tqdm.contrib.logging import logging_redirect_tqdm

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)


def main(artifacts_path, custom_cwd):
    java_command = "/usr/lib/jvm/java-11-openjdk-amd64/bin/java"
    java_options = "-Xmx8g"
    classpath = "target/dependency/*:target/thesis-1.0-SNAPSHOT.jar"
    main_class = "nl.tudelft.cornul11.thesis.corpus.MainApp"
    mode = "-m IDENTIFICATION_MODE"

    for root, dirs, files in tqdm(os.walk(artifacts_path)):
        for file in files:
            if file.endswith(".jar"):
                jar_path = os.path.join(root, file)
                output_path = jar_path.replace(".jar", ".json")
                command = [
                    java_command,
                    java_options,
                    "-cp",
                    classpath,
                    main_class,
                    mode,
                    "-f",
                    jar_path,
                    "-o",
                    output_path,
                ]

                logging.info(f"Running command: {' '.join(command)}")
                result = subprocess.run(command, cwd=custom_cwd, shell=True)
                if result.returncode != 0:
                    tqdm_log(f"Failed to run command: {' '.join(command)}")
                else:
                    tqdm_log(f"Successfully ran command: {' '.join(command)}")


def tqdm_log(msg):
    with logging_redirect_tqdm():
        logging.error(msg)


if __name__ == "__main__":
    import sys

    artifacts_path = sys.argv[1]
    custom_cwd = sys.argv[2]
    main(artifacts_path, custom_cwd)
