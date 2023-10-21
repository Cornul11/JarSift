import argparse
import json
import os
import zipfile
from datetime import datetime

import lxml.etree as ET
import requests
from tqdm import tqdm
from tqdm.gui import tqdm as tqdm_gui
from bs4 import BeautifulSoup

total_waiting_for_maven = 0
start_time = datetime.now()


def has_parent(pom_file_path):
    try:
        xml_parser = ET.XMLParser(recover=True)
        tree = ET.parse(pom_file_path, xml_parser)
        root = tree.getroot()
        ns_url = "http://maven.apache.org/POM/4.0.0"
        return root.find(f"{{{ns_url}}}parent") is not None
    except ET.ParseError as e:
        print(f"Error parsing pom file: {pom_file_path}: {e}")
    except Exception as e:
        print(f"Error: processing {pom_file_path}: {e}")
    return False


def find_pom_files(root_path):
    for foldername, _, filenames in os.walk(root_path):
        for filename in filenames:
            if filename.endswith(".pom"):
                yield os.path.join(foldername, filename)


def get_pom_files_from_file(file_path):
    with open(file_path, "r") as f:
        for line in f:
            yield line.strip()


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
            print(f"Could not find jar link for {group_id}:{artifact_id}:{version}")
            return None

    except requests.HTTPError as http_err:
        print(f"HTTP error occurred: {http_err}")
        return None
    except Exception as err:
        print(f"Other error occurred: {err}")
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
        print(
            f"Error fetching publication date for {group_id}:{artifact_id}:{version} from Maven Central: {e}"
        )
        return None
    finally:
        end_time = datetime.now()
        total_waiting_for_maven += (end_time - start_time).total_seconds()


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
        ns_url = "http://maven.apache.org/POM/4.0.0"

        group_id = root.find(f"{{{ns_url}}}groupId")
        artifact_id = root.find(f"{{{ns_url}}}artifactId")
        version = root.find(f"{{{ns_url}}}version")

        if group_id is None:
            parent = root.find(f"{{{ns_url}}}parent")
            if parent is not None:
                group_id = parent.find(f"{{{ns_url}}}groupId")

        if version is None:
            parent = root.find(f"{{{ns_url}}}parent")
            if parent is not None:
                version = parent.find(f"{{{ns_url}}}version")

        result_dict.update(
            {
                "group_id": group_id.text if group_id is not None else None,
                "artifact_id": artifact_id.text if artifact_id is not None else None,
                "version": version.text if version is not None else None,
            }
        )

        if root.find(f"{{{ns_url}}}parent") is not None:
            result_dict["has_parent"] = True

        for plugin in root.findall(f".//{{{ns_url}}}plugin"):
            artifact_id = plugin.find(f"{{{ns_url}}}artifactId")
            if artifact_id is not None and artifact_id.text == "maven-shade-plugin":
                result_dict["has_shade_plugin"] = True

                # search in <configuration> of <plugin>
                for conf in plugin.findall(f".//{{{ns_url}}}configuration"):
                    if conf.find(f"{{{ns_url}}}createDependencyReducedPom") is not None:
                        result_dict["has_dependency_reduced_pom"] = True
                    if conf.find(f"{{{ns_url}}}minimizeJar") is not None:
                        result_dict["has_minimize_jar"] = True
                    if conf.find(f"{{{ns_url}}}relocations") is not None:
                        result_dict["has_relocations"] = True

                # search within <executions> as well
                for execution in plugin.findall(f".//{{{ns_url}}}execution"):
                    for conf in execution.findall(f".//{{{ns_url}}}configuration"):
                        if (
                            conf.find(f"{{{ns_url}}}createDependencyReducedPom")
                            is not None
                        ):
                            result_dict["has_dependency_reduced_pom"] = True
                        if conf.find(f"{{{ns_url}}}minimizeJar") is not None:
                            result_dict["has_minimize_jar"] = True
                        if conf.find(f"{{{ns_url}}}relocations") is not None:
                            result_dict["has_relocations"] = True

    except ET.ParseError as e:
        print(f"Error parsing pom file: {pom_file_path}: {e}")
        result_dict["is_error"] = True
    except Exception as e:
        print(f"Error: processing {pom_file_path}: {e}")
        result_dict["is_error"] = True
    return result_dict


def create_archive(pom_files, archive_path, progress_bar):
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as zipf:
        for pom_file in progress_bar(pom_files, desc="Archiving pom.xml files"):
            if not has_parent(pom_file):
                zipf.write(pom_file)


if __name__ == "__main__":
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

    args = parser.parse_args()

    root_path = args.root_path

    if not args.root_path and not args.file:
        parser.error("Must provide either root_path or --file")

    if args.root_path and not os.path.isdir(args.root_path):
        parser.error("root_path must be a folder")

    total_pom_files = 0
    total_shade_plugins = 0
    total_dependency_reduced_pom = 0
    total_minimize_jar = 0
    total_relocations = 0
    total_errors = 0
    total_with_parents = 0
    total_shade_plugin_no_parent = 0
    monthly_trends = {
        "shade_plugin": {},
        "dependency_reduced_pom": {},
        "minimize_jar": {},
        "relocations": {},
        "shade_plugin_and_no_parent": {},
    }

    if args.file:
        pom_files = list(get_pom_files_from_file(args.file))
    else:
        pom_files = list(find_pom_files(root_path))

    total_pom_files = len(pom_files)

    progress_bar = tqdm_gui if args.gui else tqdm

    if args.mode == "archive":
        create_archive(pom_files, args.archive_path, progress_bar)
    elif args.mode == "analyze":
        for result in progress_bar(
            (contains_shade_plugin(pom_file) for pom_file in pom_files),
            total=total_pom_files,
            desc="Processing pom.xml files",
        ):
            if result["is_error"]:
                total_errors += 1

            date = None
            year_month = None

            if result["has_shade_plugin"]:
                date = get_publication_date_from_maven_repo(
                    result["group_id"], result["artifact_id"], result["version"]
                )
                if date:
                    year_month = date[:7]
                    monthly_trends["shade_plugin"][year_month] = (
                        monthly_trends["shade_plugin"].get(year_month, 0) + 1
                    )
                total_shade_plugins += 1
            if result["has_dependency_reduced_pom"]:
                if date:
                    monthly_trends["dependency_reduced_pom"][year_month] = (
                        monthly_trends["dependency_reduced_pom"].get(year_month, 0) + 1
                    )
                total_dependency_reduced_pom += 1
            if result["has_minimize_jar"]:
                if date:
                    monthly_trends["minimize_jar"][year_month] = (
                        monthly_trends["minimize_jar"].get(year_month, 0) + 1
                    )
                total_minimize_jar += 1
            if result["has_relocations"]:
                if date:
                    monthly_trends["relocations"][year_month] = (
                        monthly_trends["relocations"].get(year_month, 0) + 1
                    )
                total_relocations += 1
            if result["has_parent"]:
                total_with_parents += 1
            if result["has_shade_plugin"] and not result["has_parent"]:
                if date:
                    monthly_trends["shade_plugin_and_no_parent"][year_month] = (
                        monthly_trends["shade_plugin_and_no_parent"].get(year_month, 0)
                        + 1
                    )
                total_shade_plugin_no_parent += 1

        print(f"Total pom files: {total_pom_files}")
        print(
            f"Total pom files with errors: {total_errors} ({total_errors / total_pom_files * 100:.2f}%)"
        )
        print(
            f"Total pom files with maven-shade-plugin: {total_shade_plugins} ({total_shade_plugins / total_pom_files * 100:.2f}%)"
        )
        print(
            f"Total pom files with maven-shade-plugin but no parent: {total_shade_plugin_no_parent} ({total_shade_plugin_no_parent / (total_pom_files - total_with_parents) * 100:.2f}%)"
        )
        print(
            f"Total pom files with createDependencyReducedPom: {total_dependency_reduced_pom} ({total_dependency_reduced_pom / total_pom_files * 100:.2f}%)"
        )
        print(
            f"Total pom files with minimizeJar: {total_minimize_jar} ({total_minimize_jar / total_pom_files * 100:.2f}%)"
        )
        print(
            f"Total pom files with relocations: {total_relocations} ({total_relocations / total_pom_files * 100:.2f}%)"
        )
        print(
            f"Total pom files with parent: {total_with_parents} ({total_with_parents / total_pom_files * 100:.2f}%)"
        )

        if args.save:
            stats = {
                "total_pom_files": total_pom_files,
                "total_errors": total_errors,
                "total_shade_plugins": total_shade_plugins,
                "total_shade_plugin_no_parent": total_shade_plugin_no_parent,
                "total_dependency_reduced_pom": total_dependency_reduced_pom,
                "total_minimize_jar": total_minimize_jar,
                "total_relocations": total_relocations,
                "total_with_parents": total_with_parents,
                "shade_plugin_trends": monthly_trends,
            }

            with open(args.save, "w") as f:
                json.dump(stats, f)
            print(f"Saved stats to {args.save}")
            print(f"Total time waiting for Maven Central: {total_waiting_for_maven}")
            print(
                f"Percentage of waiting time: {total_waiting_for_maven / (datetime.now() - start_time).total_seconds() * 100:.2f}%"
            )
            print(f"Total time: {(datetime.now() - start_time).total_seconds()}")
