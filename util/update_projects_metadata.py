import argparse
import json
import os

import mysql
from jproperties import Properties
from mysql.connector import pooling
from tqdm import tqdm


def is_library_in_db(library, connection_pool):
    parts = library.split(":")
    if len(parts) != 3:
        return False

    group_id, artifact_id, version = parts

    select_library_query = "SELECT id FROM libraries_big WHERE group_id = %s AND artifact_id = %s AND version = %s AND unique_signatures > 0"
    try:
        connection = connection_pool.get_connection()
        cursor = connection.cursor(buffered=True)

        cursor.execute(select_library_query, (group_id, artifact_id, version))
        library_result = cursor.fetchone()

        return bool(library_result)
    except mysql.connector.Error as e:
        print(f"Error checking if library is in DB with signatures: {e}")
        return False
    finally:
        cursor.close()
        connection.close()


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


def main():
    parser = argparse.ArgumentParser(
        description="Add DB presence to existing metadata files."
    )
    parser.add_argument(
        "--metadata_folder", required=True, help="Path to the projects metadata folder"
    )

    args = parser.parse_args()

    connection_pool = connect_to_db()
    if not connection_pool:
        return

    metadata_folder = args.metadata_folder
    for file in tqdm(os.listdir(metadata_folder)):
        if file.endswith(".json"):
            file_path = os.path.join(metadata_folder, file)
            with open(file_path, "r") as f:
                data = json.load(f)

            for library in data.get("effectiveDependencies", []):
                gav = (
                    library["groupId"]
                    + ":"
                    + library["artifactId"]
                    + ":"
                    + library["version"]
                )
                library["presentInDatabase"] = is_library_in_db(
                    gav,
                    connection_pool,
                )
            with open(file_path, "w") as f:
                json.dump(data, f, indent=4)


if __name__ == "__main__":
    main()
