# WIP: Project title

## Setup

This project requires a functioning MariaDB database. Connection details for this database should be provided in a `config.properties` file, located at the root of the project. It's essential that an empty database exists prior to initiating the process (this can be achieved by running the database initialisation procedure).

The `config.properties` file should be based on the `config.properties.example` template found in the project root.

Similarly, rename `.env.example` file to `.env` and populate it with the necessary values.

Lastly, rename the `my-custom.cnf.example` file to `my-custom.cnf` and fill in the appropriate details.

## Execution

There are two key processes in the execution of the project: Corpus Creation and Inference.


### Corpus Creation

Follow the steps below for the corpus creation:

1. Run the command `docker compose up db` or `docker-compose up db` depending on your docker version.
2. Wait for the internal database initialisation to complete.
3. Once completed, you can terminate the comtainer.
4. Proceed by running either `docker-compose up` or `docker compose up` depending on your docker version.


It's crucial to follow this sequence. Prematurely running `docker-compose up` may result in the application failing due to an unprepared database connection.

### Inference
When executing the inference segment, ensure:

1. The database is operational.
2. Appropriate connection credentials are set in `config.properties`.

Poor verification, execute the following command from the project root:
```bash
sh run_inference.sh <path_to_uber_jar>
```

Used to create the paths file
```bash
find /home/dan/.m2/repository \( -name "*.jar" -fprint jar_files.txt \) -o \( -name "*.pom" -fprint pom_files.txt \)
```

To seed the MongoDB database:
```bash
# Create the MongoDB container
docker compose up mongodb

# preferably in a venv
cd util
pip install -r requirements.txt
python inport.py all.zip extracted
```

To export the SQL file for usage in SQLite:
```bash
mysqldump \
--host 127.0.0.1 \
--user=root --password \
--skip-create-options \
--compatible=ansi \
--skip-extended-insert \
--compact \
--single-transaction \
--no-create-db \
--no-create-info \
--hex-blob \
--skip-quote-names corpus \
| grep -a "^INSERT INTO" | grep -a -v "__diesel_schema_migrations" \
| sed 's#\\"#"#gm' \
| sed -sE "s#,0x([^,]*)#,X'\L\1'#gm" \
> mysql-to-sqlite.sql
```

To import the SQL file into SQLite:
```bash
sqlite3 corpus.db
> CREATE TABLE IF NOT EXISTS libraries (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id TEXT NOT NULL, artifact_id TEXT NOT NULL, version TEXT NOT NULL, jar_hash INTEGER NOT NULL, jar_crc INTEGER NOT NULL, is_uber_jar INTEGER NOT NULL, disk_size INTEGER NOT NULL, total_class_files INTEGER NOT NULL, unique_signatures INTEGER NOT NULL);
> CREATE TABLE IF NOT EXISTS signatures (id INTEGER PRIMARY KEY AUTOINCREMENT, library_id INTEGER NOT NULL, class_hash TEXT NOT NULL, class_crc INTEGER NOT NULL);
> PRAGMA synchronous = OFF;
> PRAGMA journal_mode = MEMORY;
> PRAGMA auto_vacuum=OFF;
> PRAGMA index_journal=OFF;
> PRAGMA temp_store=MEMORY;
> PRAGMA cache_siz=-256000;
```