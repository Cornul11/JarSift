The "server" argument can be used to run a server on the 8032 port, under the `/lookup` resource, expecting three params: `groupId`, `artifactId` and `version`.
If the combination is valid, and there is information about the artifact, the server will return a timestamp in the format: `YYYY-MM-DD`.