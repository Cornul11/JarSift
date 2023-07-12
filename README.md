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