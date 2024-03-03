#include <iostream>
#include <filesystem>
#include <fstream>
#include <vector>
#include <set>
#include <sstream>
#include <string>
#include <mysql_driver.h>
#include <mysql_connection.h>
#include <cppconn/prepared_statement.h>
#include <cppconn/resultset.h>
#include <unordered_set>
#include <unordered_map>
#include "indicators/single_include/indicators/indicators.hpp"

struct Library {
	int id;
	std::string group_id;
	std::string artifact_id;
	std::string version;
};

std::map<std::tuple<std::string, std::string, std::string>, int> libraryMap;
std::map<int, std::set<std::string>> libraryClassHashes;

namespace fs = std::filesystem;


std::vector<std::string> split(const std::string& str, char delimiter) {
	std::vector<std::string> tokens;
	std::string token;
	std::istringstream tokenStream(str);
	while (std::getline(tokenStream, token, delimiter)) {
		tokens.push_back(token);
	}
	return tokens;
}

std::unordered_map<std::string, std::unordered_set<std::string>> readClassHashesFromFolder(const std::string& folderPath) {
	std::unordered_map<std::string, std::unordered_set<std::string>> classHashesPerArtifact;

	for (const auto& entry : fs::recursive_directory_iterator(folderPath)) {
		if (entry.is_regular_file() && entry.path().extension() == ".txt") {
			std::string filePath = entry.path().string();
			auto parts = split(filePath.substr(folderPath.length() + 1), '/');
			if (parts.size() < 4) continue;
			std::string group_id = parts[0];
			for (size_t i = 1; i < parts.size() - 3; ++i) {
				group_id += '.' + parts[i];
			}
			std::string artifact_id = parts[parts.size() - 3];
			std::string version = parts[parts.size() - 2];
			std::string artifactKey = group_id + ":" + artifact_id + ":" + version;

			std::ifstream fileStream(filePath);
			std::string classHash;
			while (std::getline(fileStream, classHash)) {
				if (!classHash.empty()) {
					classHashesPerArtifact[artifactKey].insert(classHash);
				}
			}
		}
	}
	return classHashesPerArtifact;
}

void checkLibrariesContainAllHashes(std::shared_ptr<sql::Connection> conn, const std::unordered_map<std::string, std::unordered_set<std::string>>& classHashesPerArtifact, const std::string& outputPath) {
	std::ofstream outputFile(outputPath);
	outputFile << "ID vulnerable artifact,vulnerable artifact,Matching Artifact, Matching Hashes\n";

	int totalMatches = 0;
	
	for (const auto& [artifactKey, classHashes] : classHashesPerArtifact) {
		std::cout << "Processing artifact: " << artifactKey << "\n";
		std::vector<std::string> parts = split(artifactKey, ':');
		std::string vulnerableGroupId = parts[0];
		std::string vulnerableArtifactId = parts[1];
		std::string vulnerableVersion = parts[2];

		std::vector<std::string> hashesVector(classHashes.begin(), classHashes.end());

		std::string query = "SELECT library_id, COUNT(class_hash) AS matching_hashes FROM signatures_memory WHERE class_hash IN (";
		for (size_t i = 0; i < hashesVector.size(); ++i) {
			query += "?";
			if (i < hashesVector.size() - 1) query += ",";
		}
		query += ") GROUP BY library_id HAVING matching_hashes = ?";

		try {
			std::shared_ptr<sql::PreparedStatement> pstmt(conn->prepareStatement(query));

			for (size_t i = 0; i < hashesVector.size(); ++i) {
				pstmt->setString(i + 1, hashesVector[i]);
			}

			pstmt->setInt(hashesVector.size() + 1, hashesVector.size());

			std::shared_ptr<sql::ResultSet> res(pstmt->executeQuery());

			while (res->next()) {
				int libraryId = res->getInt("library_id");
				int matchingHashes = res->getInt("matching_hashes");

				std::shared_ptr<sql::PreparedStatement> pstmtDetails(conn->prepareStatement("SELECT group_id, artifact_id, version FROM libraries WHERE id = ?"));
				pstmtDetails->setInt(1, libraryId);
				std::shared_ptr<sql::ResultSet> resDetails(pstmtDetails->executeQuery());

				if (resDetails->next()) {
					std::string group_id = resDetails->getString("group_id");
					std::string artifact_id = resDetails->getString("artifact_id");
					std::string version = resDetails->getString("version");

					if (group_id == vulnerableGroupId && artifact_id == vulnerableArtifactId) {
						continue;
					}

					std::cout << "Library ID " << libraryId << " has all " << matchingHashes << " matching class hashes.\n";
					outputFile << libraryId << "," << artifactKey << "," << group_id << ":" << artifact_id << ":" << version << "," << matchingHashes << "\n";
					totalMatches;
				}
			}
		} catch (sql::SQLException& e) {
			std::cerr << "SQL Error: " << e.what() << std::endl;
		}
	}
	std::cout << "Total matches found: " << totalMatches << std::endl;
	outputFile.close();
}

int main() {
	sql::mysql::MySQL_Driver *driver;
	std::shared_ptr<sql::Connection> conn;

	std::unordered_map<std::string, std::unordered_set<std::string>> classHashesPerArtifact = readClassHashesFromFolder("signatures");

	std::string outputPath = "match_results.csv";
	try {
		std::cout << "Connecting to database...\n";
		driver = sql::mysql::get_mysql_driver_instance();
		conn.reset(driver->connect("tcp://db:3306", "root", "some_password"));
		conn->setSchema("corpus");
		std::cout << "Connected successfully.\n";

		std::cout << "Processing vulnerable artifacts...\n";

		checkLibrariesContainAllHashes(conn, classHashesPerArtifact, outputPath);
        std::cout << "Finished processing vulnerable artifacts.\n";

	} catch (sql::SQLException &e) {
		std::cerr << "SQL Error: " << e.what() << std::endl;
		return 1;
	} catch (std::exception &e) {
		std::cerr << "Error: " << e.what() << std::endl;
		return 1;
	}

	return 0;
}
