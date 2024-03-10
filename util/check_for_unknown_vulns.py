import argparse
import json
import os
import subprocess

import requests
from jproperties import Properties
from mysql.connector import pooling
from pymongo import MongoClient
from tqdm import tqdm


def parse_database_url(db_url):
    # db_url is in the format "jdbc:postgresql://localhost:5432/maven"
    try:
        url_parts = db_url.split("//")[1].split("/")
        host_port = url_parts[0]
        database = url_parts[1]

        host = host_port.split(":")[0]

        return host, database
    except IndexError:
        raise ValueError("Invalid database URL format")


properties = Properties()
with open("../config.properties", "rb") as properties_file:
    properties.load(properties_file, "utf-8")

db_host, db_name = parse_database_url(properties.get("database.url").data)


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
    total_known_vulnerable_versions = 0
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
                is_vulnerable_version = version_key == "mostUsedVulnerableVersion"
                total_known_vulnerable_versions += 1 if is_vulnerable_version else 0
                if detected_vulnerabilities:
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
        f"Total detected known vulnerable versions: {detected_known_vulnerable_versions} ({(detected_known_vulnerable_versions / total_known_vulnerable_versions) * 100:.2f}%)"
    )


def connect_to_db():
    try:
        connection_pool = pooling.MySQLConnectionPool(
            pool_name="pom_resolution_pool",
            pool_size=5,
            host=db_host,
            database=db_name,
            user=properties.get("database.username").data,
            password=properties.get("database.password").data,
        )
        return connection_pool
    except Exception as e:
        print(f"Error connecting to the database: {e}")
        return None


def get_all_libraries(cursor):
    cursor.execute("SELECT id, group_id, artifact_id, version FROM libraries")
    return cursor.fetchall()


def connect_to_mongodb():
    client = MongoClient("mongodb://localhost:27072/")
    db = client.osv_db
    return db


def check_vulnerability_in_mongodb(db, group_id, artifact_id, version):
    query = {
        "affected.package.name": f"{group_id}:{artifact_id}",
        "affected.package.ecosystem": "Maven",
        "affected.versions": {"$in": [version]},
    }
    count = db.data.count_documents(query)
    return count > 0


def get_vulnerable_libraries_from_mongodb(db):
    query = {
        "affected.package.ecosystem": "Maven",
    }
    return db.data.find(query)


def update_library_vulnerability_status(vulnerable_libraries, output_file_path):
    not_found = 0
    total_vulnerable = 0
    pool = connect_to_db()
    if pool:
        cnx = pool.get_connection()
        cursor = cnx.cursor(buffered=True)

        with open(output_file_path, "w") as file:
            for vuln in tqdm(vulnerable_libraries):
                maven_affected = [
                    a for a in vuln["affected"] if a["package"]["ecosystem"] == "Maven"
                ]
                if not maven_affected:
                    continue

                affected_package = maven_affected[0]
                if "versions" in affected_package:
                    for version in affected_package["versions"]:
                        total_vulnerable += 1
                        group_id, artifact_id = affected_package["package"][
                            "name"
                        ].split(":")

                        file.write(f"{group_id}:{artifact_id}:{version}\n")
                        print(
                            f"Updating {group_id}:{artifact_id} version {version} to vulnerable"
                        )
                        query = "SELECT id FROM libraries WHERE group_id = %s AND artifact_id = %s AND version = %s"
                        cursor.execute(query, (group_id, artifact_id, version))
                        library_id = cursor.fetchone()
                        if not library_id:
                            print(
                                f"Library {group_id}:{artifact_id} version {version} not found in corpus"
                            )
                            not_found += 1
                        # query = "UPDATE libraries SET vulnerable = 1 WHERE group_id = %s AND artifact_id = %s AND version = %s"
                        # cursor.execute(query, (group_id, artifact_id, version))
            cnx.commit()
            cursor.close()
            cnx.close()
    print(f"Total not found: {not_found}")
    print(f"Total vulnerable: {total_vulnerable}")


def fill_vulnerabilities(output_file_path="vulnerable_versions.txt"):
    mongo_db = connect_to_mongodb()
    vulnerable_libraries = get_vulnerable_libraries_from_mongodb(mongo_db)
    update_library_vulnerability_status(vulnerable_libraries, output_file_path)


def check_if_exists_in_maven_central_index(group_id, artifact_id, version):
    try:
        response = requests.get(
            "http://localhost:8032/lookup",
            params={
                "groupId": group_id,
                "artifactId": artifact_id,
                "version": version,
            },
        )
        return response.status_code == 200
    except subprocess.CalledProcessError as e:
        print(f"An error occurred: {e}")
        return None


def filter_maven_central_artifacts(
    input_file, output_file="filtered_vulnerable_versions.txt"
):
    with open(input_file, "r") as file:
        vulnerable_artifacts = file.readlines()

    vulnerable_artifacts = [a.strip() for a in vulnerable_artifacts]
    count_in_maven_index = 0

    with open(output_file, "w") as output_file:
        for artifact in tqdm(vulnerable_artifacts):
            group_id, artifact_id, version = artifact.split(":")
            exists = check_if_exists_in_maven_central_index(
                group_id, artifact_id, version
            )
            if exists is not None:
                if exists:
                    count_in_maven_index += 1

                    output_file.write(f"{group_id}:{artifact_id}:{version}\n")

    print(f"Total in Maven Central index: {count_in_maven_index}")
    print(
        f"Percentage in Maven Central index: {(count_in_maven_index / len(vulnerable_artifacts)) * 100:.2f}%"
    )


def download_vulnerable_artifacts(input_file, download_output_path):
    with open(input_file, "r") as file:
        vulnerable_artifacts = file.readlines()

    vulnerable_artifacts = [a.strip() for a in vulnerable_artifacts]
    for artifact in tqdm(vulnerable_artifacts):
        group_id, artifact_id, version = artifact.split(":")
        group_id_path = group_id.replace(".", "/")
        download_path = os.path.join(
            download_output_path,
            group_id_path,
            artifact_id,
            version,
            f"{artifact_id}-{version}.jar",
        )
        # https://repo1.maven.org/maven2/com/daml/participant-state_2.13/2.3.13/participant-state_2.13-2.3.13.jar
        if not os.path.exists(download_path):
            url = f"https://repo1.maven.org/maven2/{group_id_path}/{artifact_id}/{version}/{artifact_id}-{version}.jar"
            response = requests.get(url)
            # check if the entire path exists
            os.makedirs(os.path.dirname(download_path), exist_ok=True)
            with open(download_path, "wb") as download_file:
                download_file.write(response.content)


def check_vulnerable_inclusion(input_file):
    not_found_in_corpus = 0
    found_in_corpus_but_no_signatures = 0
    total_vulnerable = 0
    vulnerable_inclusion_count = 0
    pool = connect_to_db()
    if pool:
        cnx = pool.get_connection()
        cursor = cnx.cursor(buffered=True)

        with open(input_file, "r") as file:
            vulnerable_artifacts = file.readlines()

        vulnerable_artifacts = [a.strip() for a in vulnerable_artifacts]

        for artifact in tqdm(vulnerable_artifacts):
            group_id, artifact_id, version = artifact.split(":")
            query = "SELECT id, is_uber_jar FROM libraries WHERE group_id = %s AND artifact_id = %s AND version = %s"
            cursor.execute(query, (group_id, artifact_id, version))
            library_info = cursor.fetchone()
            if not library_info:
                not_found_in_corpus += 1
                continue

            library_id, is_uber_jar = library_info
            if is_uber_jar == 0:
                cursor.execute(
                    "SELECT class_hash FROM signatures_memory WHERE library_id = %s",
                    (library_id,),
                )
                class_hashes = cursor.fetchall()

                if not class_hashes:
                    found_in_corpus_but_no_signatures += 1
                    continue

                class_hashes_set = {ch[0] for ch in class_hashes}

                if len(class_hashes_set) == 1 and None in class_hashes_set:
                    # shouldn't happen
                    import sys

                    sys.exit(0)
                    print(
                        f"Found {group_id}:{artifact_id} version {version} in corpus but no signatures"
                    )
                    found_in_corpus_but_no_signatures += 1
                    continue

                placeholders = ", ".join(["%s"] * len(class_hashes_set))
                # Corrected query to exclude same group_id and artifact_id, and count matching libraries
                cursor.execute(
                    f"""
                                    SELECT l.library_id, COUNT(DISTINCT sm.class_hash)
                                    FROM signatures_memory sm
                                    JOIN libraries l ON sm.library_id = l.id
                                    WHERE sm.class_hash IN ({placeholders}) AND l.id != %s
                                    AND l.group_id != %s AND l.artifact_id != %s
                                    GROUP BY l.library_id
                                    HAVING COUNT(DISTINCT sm.class_hash) = %s;
                                    """,
                    tuple(class_hashes_set)
                    + (library_id, group_id, artifact_id, len(class_hashes_set)),
                )
                matching_libraries = cursor.fetchall()

                vulnerable_inclusion_count += len(matching_libraries)

        print(f"Total not found in corpus: {not_found_in_corpus}")
        print(
            f"Libraries with full inclusion of vulnerable signatures: {vulnerable_inclusion_count}"
        )


def main():
    parser = argparse.ArgumentParser(
        description="Analyze Maven artifacts for vulnerabilities"
    )
    parser.add_argument("--mode", required=True, help="Mode of operation")
    parser.add_argument(
        "--output_file", help="Path to the vulnerable artifacts GAV output file"
    )
    parser.add_argument(
        "--input_file", help="Path to the vulnerable artifacts GAV input file"
    )
    parser.add_argument(
        "--maven_artifacts_file",
        help="Path to the JSON file containing Maven artifacts information",
    )
    parser.add_argument(
        "--artifacts_directory",
        help="Path to the directory containing the inferred artifacts metadata",
    )
    parser.add_argument("--download_output_path", help="Path to the output directory")

    args = parser.parse_args()
    if args.mode == "analyze_artifacts":
        if args.maven_artifacts_file and args.artifacts_directory:
            analyze_artifacts(args.maven_artifacts_file, args.artifacts_directory)
        else:
            print(
                "Error: Both --maven_artifacts_file and --artifacts_directory are required for this mode"
            )
    elif args.mode == "fill_vulnerabilities":
        fill_vulnerabilities()
    elif args.mode == "filter_maven_central_artifacts":
        if not args.input_file:
            print("Error: --input_file is required for this mode")
        else:
            filter_maven_central_artifacts(args.input_file, args.output_file)
    elif args.mode == "download_vulnerable_artifacts":
        if not args.input_file or not args.download_output_path:
            print(
                "Error: Both --input_file and --download_output_path are required for this mode"
            )
        else:
            download_vulnerable_artifacts(args.input_file, args.download_output_path)
    elif args.mode == "check_vulnerable_inclusion":
        if not args.input_file:
            print("Error: --input_file is required for this mode")
        else:
            check_vulnerable_inclusion(args.input_file)
    else:
        print(f"Error: Unknown mode {args.mode}")


if __name__ == "__main__":
    main()
