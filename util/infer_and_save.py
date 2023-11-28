import logging
import os
import subprocess

from tqdm import tqdm
from tqdm.contrib.logging import logging_redirect_tqdm

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)


def count_jar_files(path):
    count = 0
    for root, dirs, files in os.walk(path):
        for file in files:
            if file.endswith(".jar"):
                count += 1
    return count


def main(artifacts_path, custom_cwd):
    java_command = "/usr/lib/jvm/java-11-openjdk-amd64/bin/java"
    java_options = "-Xmx8g"
    classpath = "target/dependency/*:target/thesis-1.0-SNAPSHOT.jar"
    main_class = "nl.tudelft.cornul11.thesis.corpus.MainApp"
    mode = "-m IDENTIFICATION_MODE"

    total_jar_files = count_jar_files(artifacts_path)

    with tqdm(total=total_jar_files, desc="Running inference", unit="JAR") as pbar:
        for root, dirs, files in os.walk(artifacts_path):
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

                    tqdm_log(f"Running command: {' '.join(command)}")
                    command_str = " ".join(command)
                    result = subprocess.run(
                        command_str, cwd=custom_cwd, shell=True, stdout=subprocess.DEVNULL
                    )
                    if result.returncode != 0:
                        tqdm_log(f"Failed to run command: {' '.join(command)}")
                    else:
                        tqdm_log(f"Successfully ran command: {' '.join(command)}")

                    pbar.update(1)


def tqdm_log(msg):
    with logging_redirect_tqdm():
        logging.info(msg)


if __name__ == "__main__":
    import sys

    artifacts_path = sys.argv[1]
    custom_cwd = sys.argv[2]
    main(artifacts_path, custom_cwd)
