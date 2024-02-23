from jproperties import Properties
from mysql.connector import pooling
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


def get_top_level_parents(cursor):
    cursor.execute(
        """
    SELECT id, library_id, has_assembly_plugin, has_shade_plugin, has_dependency_reduced_pom, 
    has_minimize_jar, has_relocations, has_filters, has_transformers
    FROM pom_info
    WHERE parent_id IS NULL OR parent_id NOT IN (SELECT library_id FROM pom_info)"""
    )
    return cursor.fetchall()


def update_children(pool, parent_id, inherited_props):
    cnx = pool.get_connection()
    cursor = cnx.cursor(buffered=True)

    cursor.execute(
        """
    SELECT id FROM pom_info WHERE parent_id = %s
    """,
        (parent_id,),
    )
    children = cursor.fetchall()

    for (child_id,) in children:
        update_clause = ", ".join(
            [f"{prop} = 1" for prop, value in inherited_props.items() if value]
        )
        if update_clause:
            update_query = f"UPDATE pom_info SET {update_clause} WHERE id = {child_id}"
            cursor.execute(update_query)

        cursor.execute(
            """
        SELECT has_assembly_plugin, has_shade_plugin, has_dependency_reduced_pom,
        has_minimize_jar, has_relocations, has_filters, has_transformers
        FROM pom_info WHERE id = %s
        """,
            (child_id,),
        )
        child_props = cursor.fetchone()
        next_gen_props = {
            prop: max(value, child_props[i])
            for i, (prop, value) in enumerate(inherited_props.items())
        }

        update_children(pool, child_id, next_gen_props)

    cnx.commit()
    cursor.close()
    cnx.close()


def main():
    pool = connect_to_db()
    if pool:
        cnx = pool.get_connection()
        cursor = cnx.cursor(buffered=True)
        top_level_parents = get_top_level_parents(cursor)
        cursor.close()
        cnx.close()

        for parent in tqdm(top_level_parents):
            parent_id, library_id, *has_props = parent
            inherited_props = {
                "has_assembly_plugin": has_props[0],
                "has_shade_plugin": has_props[1],
                "has_dependency_reduced_pom": has_props[2],
                "has_minimize_jar": has_props[3],
                "has_relocations": has_props[4],
                "has_filters": has_props[5],
                "has_transformers": has_props[6],
            }
            update_children(pool, library_id, inherited_props)
    else:
        print("Failed to connect to the database")


if __name__ == "__main__":
    main()
