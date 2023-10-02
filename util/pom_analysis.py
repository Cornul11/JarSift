import os
import argparse
import lxml.etree as ET
import zipfile
import json
from tqdm import tqdm
from tqdm.gui import tqdm as tqdm_gui


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
            if filename == "pom.xml":
                yield os.path.join(foldername, filename)


def get_pom_files_from_file(file_path):
    with open(file_path, "r") as f:
        for line in f:
            yield line.strip()


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
            if result["has_shade_plugin"]:
                total_shade_plugins += 1
            if result["has_dependency_reduced_pom"]:
                total_dependency_reduced_pom += 1
            if result["has_minimize_jar"]:
                total_minimize_jar += 1
            if result["has_relocations"]:
                total_relocations += 1
            if result["has_parent"]:
                total_with_parents += 1
            if result["has_shade_plugin"] and not result["has_parent"]:
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
            }

            with open(args.save, "w") as f:
                json.dump(stats, f)
            print(f"Saved stats to {args.save}")
