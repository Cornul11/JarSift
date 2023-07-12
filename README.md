A working MariaDB database is required to run this project. The database connection information should be provided in a `config.properties` file. It should already be initialized with an empty `corpus` database before running.

`config.properties` should be located in the root of the project before running. It should be based of the provided `config.properties.example` file in the root of the project.

A similar thing has to be done for the `.env.example` file. It should be renamed to `.env` and the values should be filled in.

In order to run the inference mechanism on an unknown uber-JAR, the database must be running, and the correct connection credentials should be set in `config.properties`. Then you can run the following command in the root of the project:

```bash
sh run_inference.sh <path_to_uber_jar>
```