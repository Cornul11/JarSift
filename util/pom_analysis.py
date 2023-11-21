import argparse
import json
import logging
import os
import subprocess
import zipfile
from datetime import datetime

import lxml.etree as ET
import requests
from bs4 import BeautifulSoup
from tqdm import tqdm
from tqdm.contrib.logging import logging_redirect_tqdm
from tqdm.gui import tqdm as tqdm_gui

total_waiting_for_maven = 0

DEFAULT_ARCHIVE_PATH = "pom_archive.zip"
NS_URL = "http://maven.apache.org/POM/4.0.0"

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)


def extract_gav_from_pom_path(pom_file_path):
    # last folder name is the version
    # second last is the artifactId
    # and everything that comes after ../.m2/repository/ is the groupId, only with dots instead of slashes

    path_components = pom_file_path.split(os.sep)
    version = path_components[-2]
    artifact_id = path_components[-3]

    m2_index = path_components.index(".m2")
    group_id = ".".join(path_components[m2_index + 2 : -3])

    return group_id, artifact_id, version


def get_publication_date_from_local_maven_index(group_id, artifact_id, version):
    try:
        response = requests.get(
            "http://localhost:8080/lookup",
            params={
                "groupId": group_id,
                "artifactId": artifact_id,
                "version": version,
            },
        )
        if response.status_code == 200:
            date_str = response.text.strip()
            return date_str
        else:
            with logging_redirect_tqdm():
                logging.error(
                    f"Lookup failed with status code {response.status_code}",
                )
            return None
    except subprocess.CalledProcessError as e:
        logging.error(f"An error occurred: {e}")
        return None


def has_parent(pom_file_path):
    try:
        xml_parser = ET.XMLParser(recover=True)
        tree = ET.parse(pom_file_path, xml_parser)
        root = tree.getroot()
        ns_url = "http://maven.apache.org/POM/4.0.0"
        return root.find(f"{{{ns_url}}}parent") is not None
    except ET.ParseError as e:
        logging.error(f"Error parsing pom file: {pom_file_path}: {e}")
    except Exception as e:
        logging.error(f"Error: processing {pom_file_path}: {e}")
    return False


def contains_shade_plugin(pom_file_path):
    result_dict = {
        "path": pom_file_path,
        "has_shade_plugin": False,
        "has_dependency_reduced_pom": False,
        "has_minimize_jar": False,
        "has_relocations": False,
        "has_filters": False,
        "has_transformers": False,
        "is_error": False,
        "has_parent": False,
    }

    try:
        xml_parser = ET.XMLParser(recover=True)
        tree = ET.parse(pom_file_path, xml_parser)
        root = tree.getroot()

        if root.find(f"{{{NS_URL}}}parent") is not None:
            result_dict["has_parent"] = True

        for plugin in root.findall(f".//{{{NS_URL}}}plugin"):
            artifact_id = plugin.find(f"{{{NS_URL}}}artifactId")
            if artifact_id is not None and artifact_id.text == "maven-shade-plugin":
                result_dict["has_shade_plugin"] = True

                # search in <configuration> of <plugin>
                for conf in plugin.findall(f".//{{{NS_URL}}}configuration"):
                    if conf.find(f"{{{NS_URL}}}createDependencyReducedPom") is not None:
                        result_dict["has_dependency_reduced_pom"] = True
                    if conf.find(f"{{{NS_URL}}}minimizeJar") is not None:
                        result_dict["has_minimize_jar"] = True
                    if conf.find(f"{{{NS_URL}}}relocations") is not None:
                        result_dict["has_relocations"] = True

                # search within <executions> as well
                for execution in plugin.findall(f".//{{{NS_URL}}}execution"):
                    for conf in execution.findall(f".//{{{NS_URL}}}configuration"):
                        if (
                            conf.find(f"{{{NS_URL}}}createDependencyReducedPom")
                            is not None
                        ):
                            result_dict["has_dependency_reduced_pom"] = True
                        if conf.find(f"{{{NS_URL}}}minimizeJar") is not None:
                            result_dict["has_minimize_jar"] = True
                        if conf.find(f"{{{NS_URL}}}relocations") is not None:
                            result_dict["has_relocations"] = True

    except ET.ParseError as e:
        logging.error(f"Error parsing pom file: {pom_file_path}: {e}")
        result_dict["is_error"] = True
    except Exception as e:
        logging.error(f"Error: processing {pom_file_path}: {e}")
        result_dict["is_error"] = True
    return result_dict


class MavenPomAnalyzer:
    def __init__(self, args):
        self.args = args
        self.start_time = datetime.now()
        self.initialize_stats()

    def _update_trend(self, trend_dict, year_month):
        if year_month:
            trend_dict[year_month] = trend_dict.get(year_month, 0) + 1

    def initialize_stats(self):
        self.total_pom_files = 0
        self.total_shade_plugins = 0
        self.total_dependency_reduced_pom = 0
        self.total_minimize_jar = 0
        self.total_relocations = 0
        self.total_errors = 0
        self.total_not_found_in_index = 0
        self.total_not_found = 0
        self.total_with_parents = 0
        self.total_shade_plugin_no_parent = 0
        self.overall_trends = {}
        self.shade_trends = {
            "shade_plugin": {},
            "dependency_reduced_pom": {},
            "minimize_jar": {},
            "relocations": {},
            "shade_plugin_and_no_parent": {},
        }

    def run(self):
        self.validate_arguments()
        pom_files = self.get_pom_files()
        self.total_pom_files = len(pom_files)
        if self.args.mode == "archive":
            self.create_archive(pom_files)
        elif self.args.mode == "analyze":
            self.analyze_pom_files(pom_files)

    def validate_arguments(self):
        if not self.args.root_path and not self.args.file:
            raise ValueError("Must provide either root_path or --file")
        if self.args.root_path and not os.path.isdir(self.args.root_path):
            raise ValueError("root_path must be a folder")

    def get_pom_files(self):
        if self.args.file:
            return list(self.get_pom_files_from_file())
        else:
            return list(self.find_pom_files())

    def get_pom_files_from_file(self):
        with open(self.args.file, "r") as f:
            for line in f:
                yield line.strip()

    def analyze_pom_files(self, pom_files):
        for pom_file in self.progress_bar(pom_files):
            result = contains_shade_plugin(pom_file)
            self.update_stats(result)
        self.print_stats()
        self.save_stats_if_required()

    def progress_bar(self, iterable, **kwargs):
        return (
            tqdm_gui(iterable, **kwargs) if self.args.gui else tqdm(iterable, **kwargs)
        )

    def find_pom_files(self):
        for foldername, _, filenames in os.walk(self.args.root_path):
            for filename in filenames:
                if filename.endswith(".pom"):
                    yield os.path.join(foldername, filename)

    def create_archive(self, pom_files):
        with zipfile.ZipFile(self.args.archive_path, "w", zipfile.ZIP_DEFLATED) as zipf:
            for pom_file in self.progress_bar(
                pom_files, desc="Archiving pom.xml files"
            ):
                if not has_parent(pom_file):
                    zipf.write(pom_file)

    def update_stats(self, result):
        group_id, artifact_id, version = extract_gav_from_pom_path(result["path"])

        if result["is_error"] or any(
            v is None for v in [group_id, artifact_id, version]
        ):
            self.total_errors += 1
            return

        date = get_publication_date_from_local_maven_index(
            group_id, artifact_id, version
        )

        if date is None:
            self.total_not_found_in_index += 1
            date = get_publication_date_from_maven_repo_header(
                group_id, artifact_id, version
            )

        # retain only the year and month for stats
        year_month = date[:7] if date else None

        if date:
            self.overall_trends[year_month] = self.overall_trends.get(year_month, 0) + 1
        else:
            self.total_not_found += 1

        if result["has_shade_plugin"]:
            self._update_trend(self.shade_trends["shade_plugin"], year_month)
            self.total_shade_plugins += 1
        if result["has_dependency_reduced_pom"]:
            self._update_trend(self.shade_trends["dependency_reduced_pom"], year_month)
            self.total_dependency_reduced_pom += 1
        if result["has_minimize_jar"]:
            self._update_trend(self.shade_trends["minimize_jar"], year_month)
            self.total_minimize_jar += 1

        if result["has_relocations"]:
            self._update_trend(self.shade_trends["relocations"], year_month)
            self.total_relocations += 1

        if result["has_parent"]:
            self.total_with_parents += 1
        if result["has_shade_plugin"] and not result["has_parent"]:
            self._update_trend(
                self.shade_trends["shade_plugin_and_no_parent"], year_month
            )
            self.total_shade_plugin_no_parent += 1

    def print_stats(self):
        with logging_redirect_tqdm():
            logging.info(f"Total pom files: {self.total_pom_files}")
            logging.info(
                f"Total pom files with errors: {self.total_errors} ({self.total_errors / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with maven-shade-plugin: {self.total_shade_plugins} ({self.total_shade_plugins / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with maven-shade-plugin but no parent: {self.total_shade_plugin_no_parent} ({self.total_shade_plugin_no_parent / (self.total_pom_files - self.total_with_parents) * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with createDependencyReducedPom: {self.total_dependency_reduced_pom} ({self.total_dependency_reduced_pom / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with minimizeJar: {self.total_minimize_jar} ({self.total_minimize_jar / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with relocations: {self.total_relocations} ({self.total_relocations / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total pom files with parent: {self.total_with_parents} ({self.total_with_parents / self.total_pom_files * 100:.2f}%)"
            )
            logging.info(
                f"Total not found in index: {self.total_not_found_in_index} ({self.total_not_found_in_index / self.total_shade_plugins * 100:.2f}%)"
            )
            logging.info(
                f"Total not found: {self.total_not_found} ({self.total_not_found / self.total_shade_plugins * 100:.2f}%)"
            )

    def save_stats_if_required(self):
        if self.args.save:
            stats = {
                "total_pom_files": self.total_pom_files,
                "total_shade_plugins": self.total_shade_plugins,
                "total_dependency_reduced_pom": self.total_dependency_reduced_pom,
                "total_minimize_jar": self.total_minimize_jar,
                "total_relocations": self.total_relocations,
                "total_errors": self.total_errors,
                "total_not_found_in_index": self.total_not_found_in_index,
                "total_not_found": self.total_not_found,
                "total_with_parents": self.total_with_parents,
                "total_shade_plugin_no_parent": self.total_shade_plugin_no_parent,
                "general_trends": self.overall_trends,
                "shade_plugin_trends": self.shade_trends,
            }

            with open(self.args.save, "w") as f:
                json.dump(stats, f)
            logging.info(f"Saved stats to {self.args.save}")
            logging.info(
                f"Total time waiting for Maven Central: {total_waiting_for_maven}"
            )
            logging.info(
                f"Total time: {(datetime.now() - self.start_time).total_seconds()}"
            )


def parse_arguments():
    parser = argparse.ArgumentParser(description="Find pom files with shade plugin")
    parser.add_argument(
        "root_path",
        type=str,
        help="Root path to search for pom files",
        nargs="?",
        default=None,
    )
    parser.add_argument(
        "--file", type=str, help="File containing paths to pom.xml files", default=None
    )
    parser.add_argument(
        "--mode",
        type=str,
        help="Mode to run in: archive, analyze",
        choices=["analyze", "archive"],
        default="analyze",
    )
    parser.add_argument(
        "--archive_path",
        type=str,
        help='Path to save the archive, only used in "archive" mode',
        default="pom_archive.zip",
    )
    parser.add_argument(
        "--gui",
        action="store_true",
        help="Use graphical tqdm instead of the standard one",
        default=False,
    )
    parser.add_argument(
        "--save", type=str, help="Save the results to a file", default=None
    )

    return parser.parse_args()


def get_publication_date_from_maven_repo_header(group_id, artifact_id, version):
    url = f"https://repo1.maven.org/maven2/{group_id.replace('.', '/')}/{artifact_id}/{version}/{artifact_id}-{version}.pom"
    response = requests.head(url)
    if response.status_code == 200:
        date_text = response.headers["last-modified"]
        publication_date = datetime.strptime(
            date_text, "%a, %d %b %Y %H:%M:%S %Z"
        ).strftime("%Y-%m")
        return publication_date


def get_publication_date_from_maven_repo(group_id, artifact_id, version):
    global total_waiting_for_maven
    start_time = datetime.now()

    group_id = group_id.replace(".", "/")

    url = f"https://repo1.maven.org/maven2/{group_id}/{artifact_id}/{version}"

    try:
        response = requests.get(url)
        response.raise_for_status()

        soup = BeautifulSoup(response.text, "html.parser")

        jar_link = soup.find("a", href=f"{artifact_id}-{version}.pom")

        if jar_link:
            date_text = jar_link.parent.text.split()[-3]
            publication_date = datetime.strptime(date_text, "%Y-%m-%d").strftime(
                "%Y-%m-%d"
            )
            return publication_date
        else:
            logging.error(
                f"Could not find jar link for {group_id}:{artifact_id}:{version}"
            )
            return None

    except requests.HTTPError as http_err:
        logging.error(f"HTTP error occurred: {http_err}")
        return None
    except Exception as err:
        logging.error(f"Other error occurred: {err}")
        return None
    finally:
        end_time = datetime.now()
        total_waiting_for_maven += (end_time - start_time).total_seconds()


def get_publication_date_from_maven_central(group_id, artifact_id, version):
    global total_waiting_for_maven
    start_time = datetime.now()
    try:
        base_url = "https://search.maven.org/solrsearch/select"
        query = f"g:{group_id} AND a:{artifact_id} AND v:{version}"
        params = {
            "q": query,
            "rows": 1,
            "wt": "json",
        }
        response = requests.get(base_url, params=params)
        response_data = response.json()
        if response_data["response"]["numFound"] > 0:
            timestamp_millis = response_data["response"]["docs"][0]["timestamp"]
            publication_date = datetime.utcfromtimestamp(
                timestamp_millis / 1000
            ).strftime("%Y-%m-%d")
            return publication_date
        return None
    except Exception as e:
        logging.error(
            f"Error fetching publication date for {group_id}:{artifact_id}:{version} from Maven Central: {e}"
        )
        return None
    finally:
        end_time = datetime.now()
        total_waiting_for_maven += (end_time - start_time).total_seconds()


def main():
    args = parse_arguments()
    analyzer = MavenPomAnalyzer(args)
    analyzer.run()


if __name__ == "__main__":
    main()
