# JarSift

## Setup

This project requires a functioning MariaDB database. Connection details for this database should be provided in
a `config.properties` file, located at the root of the project. It's essential that an empty database exists prior to
initiating the process (this can be achieved by running the database initialisation procedure).

The `config.properties` file should be based on the `config.properties.example` template found in the project root.

Similarly, rename `.env.example` file to `.env` and populate it with the respective values.

Lastly, rename the `my-custom.cnf.example` file to `my-custom.cnf` and fill in the appropriate details fitting your
environment.

## Execution

There are two key processes in the execution of the project: Corpus Creation and Inference.

### Corpus Creation

Follow the steps below for the corpus creation:

Used to create the paths file which is used to seed the database:

```bash
find /path/to/your/local/.m2/repo \( -name "*.jar" -fprint jar_files.txt \) -o \( -name "*.pom" -fprint pom_files.txt \)
```

After the paths files have been created, follow the steps below to seed the database:

1. Run `docker compose up db`.
2. Wait for the internal database initialisation to complete.
3. Once completed, you can terminate the container.
4. Fill in the `PATHS_FILE` environment variable in the `docker-compose.yml` file or the `.env` file with the path to
   the `jar_files.txt` file created earlier.
5. Proceed by running `docker compose up`.

It's crucial to follow this sequence. Prematurely running `docker compose up` may result in the application failing due
to an unprepared database connection.

### Inference

To execute the inference segment, you need to have a MongoDB instance running which you need to seed with the necessary
data. The data can be found in the `data` directory.
To seed the MongoDB database:

```bash
# Create the MongoDB container
docker compose up mongodb

# You may use the existing all.zip file, or retrieve the latest data by running the following command (ensure you have gsutil installed)
gsutil cp gs://osv-vulnerabilities/Maven/all.zip .

# preferably in a venv
cd util
pip install -r requirements.txt
python import.py all.zip extracted
```

When executing the inference segment, ensure:

1. The corpus database is operational and seeded with the necessary data.
2. The MongoDB instance is operational and accessible and has been seeded with the necessary data.
3. Appropriate connection credentials are set in `config.properties`.

For verification, execute the following command from the project root:

```bash
sh run_inference.sh <path_to_jar>
```

## Evaluation
For the evaluation segment, you must ensure that the corpus database is operational and seeded with the necessary data.

To generate the evaluation data, execute the following command from the project root:

```bash
sh run_generator.sh <jars per config> <max dependencies per jar>
```

This will generate the Uber JARs and their respective metadata. This will also run the evaluation process and output the
results to the `evaluation` directory.

If you have already generated the evaluation data and wish to re-run the evaluation process, execute the following
command from the project root:

```bash
sh run_evaluation.sh <evaluation data directory>
```