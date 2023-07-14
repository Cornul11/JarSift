import dotenv from "dotenv";
import { join, resolve } from "path";
const envPath = resolve(__dirname, "..", "..", ".env");
dotenv.config({ path: envPath });

import { Dirent, readdirSync } from "fs";
import { eachLimit } from "async";
import { Sequelize } from "sequelize";

export function* walk(path: string): IterableIterator<string> {
  const entries: Dirent[] = readdirSync(path, { withFileTypes: true });
  // randomize the order of the entries
  entries.sort(() => Math.random() - 0.5);

  for (const entry of entries) {
    const entryPath: () => string = () => join(path, entry.name);

    if (entry.isFile()) {
      yield entryPath();
    }

    if (entry.isDirectory()) {
      yield* walk(entryPath());
    }
  }
}

/**
 * Iterate through a m2 repository and collect the file size of each jar file
 */
if (require.main === module) {
  const path = process.argv[2];
  if (!path) {
    console.error("Please specify a path");
    process.exit(1);
  }
  const sequelize = new Sequelize(
    process.env.MYSQL_DATABASE,
    process.env.MYSQL_USER,
    process.env.MYSQL_PASSWORD,
    {
      host: process.env.MYSQL_HOST || "localhost",
      port: parseInt(process.env.MYSQL_PORT || "3306"),
      dialect: "mariadb",
      logging: false,
    }
  );

  let count = 0;
  const intervalId = setInterval(() => {
    console.log(`[INFO] ${count} jars processed}`);
  }, 1000);

  eachLimit(
    walk(path),
    10,
    async (filePath, callback) => {
      try {
        if (!filePath.endsWith(".jar")) {
          return;
        }
        const stat = require("fs").statSync(filePath);

        const jarInfo = filePath
          .slice(filePath.indexOf("repository") + 11)
          .split("/");

        const groupID = jarInfo.slice(0, -3).join(".");
        const artifactID = jarInfo.slice(-3, -2).join(".");
        const version = jarInfo.slice(-2, -1).join(".");

        // update the mysql database using lib
        await sequelize.query({
          query:
            "UPDATE libraries SET disk_size = ? WHERE group_id = ? AND artifact_id = ? AND version = ?",
          values: [stat.size, groupID, artifactID, version],
        });
        count++;
      } catch (e) {
        console.error(e);
      } finally {
        callback();
      }
    },
    (err) => {
      clearInterval(intervalId);
    }
  );
  walk(path);
}
